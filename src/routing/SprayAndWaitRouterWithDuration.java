package routing;

import core.*;
import java.util.*;
import core.Connection;
import core.DTNHost;
import core.Message;
import core.Settings;
import routing.community.*;
public class SprayAndWaitRouterWithDuration extends ActiveRouter {

    public static final String NROF_COPIES = "nrofCopies";
    public static final String BINARY_MODE = "binaryMode";
    public static final String SPRAYANDWAIT_NS = "SprayAndWaitRouter";
    public static final String MSG_COUNT_PROPERTY = SPRAYANDWAIT_NS + "." + "copies";

    protected int initialNrofCopies;
    protected boolean isBinary;

    // Connection history tracking
    protected Map<DTNHost, Double> startTimestamps;
    protected Map<DTNHost, List<Duration>> connHistory;

    // Variables for connection time calculations
    protected double t1;
    protected double t2;
    protected double t3;
    protected double r;  // Exponential smoothing factor

    public SprayAndWaitRouterWithDuration(Settings s) {
        super(s);
        Settings snwSettings = new Settings(SPRAYANDWAIT_NS);

        initialNrofCopies = snwSettings.getInt(NROF_COPIES);
        isBinary = snwSettings.getBoolean(BINARY_MODE);

        startTimestamps = new HashMap<>();
        connHistory = new HashMap<>();
        t1 = 0.0;
        t2 = 0.0;
        t3 = 1.0;  // Default to avoid division by zero
        r = 0.5;   // Smoothing factor
    }

    /**
     * Copy constructor.
     * @param proto
     */
    protected SprayAndWaitRouterWithDuration(SprayAndWaitRouterWithDuration proto) {
        super(proto);
        this.initialNrofCopies = proto.initialNrofCopies;
        this.isBinary = proto.isBinary;
        this.startTimestamps = new HashMap<>(proto.startTimestamps);
        this.connHistory = new HashMap<>(proto.connHistory);
        this.t1 = proto.t1;
        this.t2 = proto.t2;
        this.t3 = proto.t3;
        this.r = proto.r;
    }

    @Override
    public void changedConnection(Connection con) {
        DTNHost other = con.getOtherNode(getHost());
        DTNHost thisHost = getHost();
        if (con.isUp()) {
            startTimestamps.put(other, SimClock.getTime());
        } else {
            double time = check(thisHost, other);
            double eTime = SimClock.getTime();

            List<Duration> history;
            if (!connHistory.containsKey(other)) {
                history = new LinkedList<>();
                connHistory.put(other, history);
            } else {
                history = connHistory.get(other);
            }

            if (eTime - time > 0) {
                history.add(new Duration(time, eTime));
            }

            startTimestamps.remove(other);

            // Calculate t1 and t2 from connection history
            calculateConnectionTimes(history);
            t3 = SimClock.getTime() - time;  // Update t3 as the current connection interval
        }
    }

    /**
     * Calculate t1 and t2 from the list of connection durations (history)
     * t1 and t2 are total connection times during separate connection periods
     */
    private void calculateConnectionTimes(List<Duration> history) {
        if (history.size() >= 2) {
            // Use the two latest connection durations for t1 and t2
            Duration latest = history.get(history.size() - 1);
            Duration secondLatest = history.get(history.size() - 2);
            t1 = latest.getDuration();
            t2 = secondLatest.getDuration();
        } else if (history.size() == 1) {
            // If there's only one connection period, use it for t1
            t1 = history.get(0).getDuration();
            t2 = 0.0;  // No second connection available
        } else {
            t1 = 0.0;
            t2 = 0.0;
        }
    }

    // Method to calculate μ (mu) as the duty ratio of connection
    protected double calculateMu() {
        return (t1 + t2) / t3;
    }

    // Method to calculate U_time based on μ and exponential smoothing factor r
    protected double calculateUTime() {
        double mu = calculateMu();
        return Math.exp(r * mu);
    }

    @Override
    public int receiveMessage(Message m, DTNHost from) {
        return super.receiveMessage(m, from);
    }

    @Override
    public Message messageTransferred(String id, DTNHost from) {
        Message msg = super.messageTransferred(id, from);
        Integer nrofCopies = (Integer) msg.getProperty(MSG_COUNT_PROPERTY);

        assert nrofCopies != null : "Not a SnW message: " + msg;

        if (isBinary) {
            /* in binary S'n'W the receiving node gets ceil(n/2) copies */
            nrofCopies = (int) Math.ceil(nrofCopies / 2.0);
        } else {
            /* in standard S'n'W the receiving node gets only single copy */
            nrofCopies = 1;
        }

        msg.updateProperty(MSG_COUNT_PROPERTY, nrofCopies);
        return msg;
    }

    @Override
    public boolean createNewMessage(Message msg) {
        makeRoomForNewMessage(msg.getSize());

        msg.setTtl(this.msgTtl);
        msg.addProperty(MSG_COUNT_PROPERTY, new Integer(initialNrofCopies));
        addToMessages(msg, true);

        // Print the calculated μ and U_time for each new message
        System.out.println("Message created with ID: " + msg.getId());
        System.out.println("μ (Mu) = " + calculateMu());
        System.out.println("U_time = " + calculateUTime());

        return true;
    }

    @Override
    public void update() {
        super.update();
        if (!canStartTransfer() || isTransferring()) {
            return; // nothing to transfer or is currently transferring
        }

        /* try messages that could be delivered to final recipient */
        if (exchangeDeliverableMessages() != null) {
            return;
        }

        /* create a list of SAWMessages that have copies left to distribute */
        @SuppressWarnings(value = "unchecked")
        List<Message> copiesLeft = sortByQueueMode(getMessagesWithCopiesLeft());

        if (copiesLeft.size() > 0) {
            /* try to send those messages */
            this.tryMessagesToConnections(copiesLeft, getConnections());
        }
    }

    /**
     * Creates and returns a list of messages this router is currently
     * carrying and still has copies left to distribute (nrof copies > 1).
     * @return A list of messages that have copies left
     */
    protected List<Message> getMessagesWithCopiesLeft() {
        List<Message> list = new ArrayList<>();

        for (Message m : getMessageCollection()) {
            Integer nrofCopies = (Integer) m.getProperty(MSG_COUNT_PROPERTY);
            assert nrofCopies != null : "SnW message " + m + " didn't have " + "nrof copies property!";
            if (nrofCopies > 1) {
                list.add(m);
            }
        }

        return list;
    }

    @Override
    protected void transferDone(Connection con) {
        Integer nrofCopies;
        String msgId = con.getMessage().getId();
        /* get this router's copy of the message */
        Message msg = getMessage(msgId);

        if (msg == null) { // message has been dropped from the buffer after..
            return; // ..start of transfer -> no need to reduce amount of copies
        }

        /* reduce the amount of copies left */
        nrofCopies = (Integer) msg.getProperty(MSG_COUNT_PROPERTY);
        if (isBinary) {
            nrofCopies /= 2;
        } else {
            nrofCopies--;
        }
        msg.updateProperty(MSG_COUNT_PROPERTY, nrofCopies);
    }

    @Override
    public SprayAndWaitRouterWithDuration replicate() {
        return new SprayAndWaitRouterWithDuration(this);
    }

    private double check(DTNHost thisHost, DTNHost peer) {
        if (startTimestamps.containsKey(peer)) {
            return startTimestamps.get(peer);
        }
        return 0;
    }
}
