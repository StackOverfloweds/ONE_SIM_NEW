/*
 * Copyright 2024 HaiPigGi (Bryan)
 * 
 */

package routing;

import core.*;
import java.util.*;
import routing.community.*;
import routing.util.TupleDe;


public class PeopleRankActiveRouter extends ActiveRouter {
    /*Initialitation variable Dumping factor to empty */
    public static final String DUMPING_FACTOR_SETTING = "dumpingFactor";
    public static final String TRESHOLD_SETTING = "treshold";

    /** PeopleRank router's setting namespace ({@value}) **/ 
	public static final String PeopleRank_NS = "PeopleRankActiveRouter";
    public static final String MSG_COUNT_PROPERTY = PeopleRank_NS + "." +
		"copies";

    // Community detection and damping factor
    protected double dumpingFactor; // Damping factor used in the PeopleRank algorithm
    protected double treshold; // Threshold for considering connections

    // create data structure 
    protected Map <DTNHost, TupleDe <Double, Integer>> per;
    protected Map <DTNHost, List<Duration>> connHistory;
    protected Map<DTNHost,Double> startTimeStamps;
    protected Set <DTNHost> thisHostSet;

    /**
     * Create Constructor
     * @param s
     */
    public PeopleRankActiveRouter (Settings s)
    {
        super(s);
        Settings peopleActive = new Settings (PeopleRank_NS);
        if (peopleActive.contains(DUMPING_FACTOR_SETTING)) {
            dumpingFactor = s.getDouble(DUMPING_FACTOR_SETTING);
        } else {
            this.dumpingFactor = 0.85; // get default by the paper
        } 
        if (peopleActive.contains(TRESHOLD_SETTING)) {
            treshold = s.getDouble(TRESHOLD_SETTING);
        } else {
            this.treshold = 700; //get default by the paper
        }
        connHistory = new HashMap<DTNHost, List<Duration>>();
        per = new HashMap<>();
        thisHostSet = new HashSet<DTNHost>();
    }

    /**
     * create Copy constructor
     * @param pr
     */

     public PeopleRankActiveRouter (PeopleRankActiveRouter pr) 
     {
        super (pr);
        //replicate 
        this.dumpingFactor = pr.dumpingFactor;
        this.treshold = pr.treshold;
        startTimeStamps = new HashMap<DTNHost, Double>();

        this.connHistory = new HashMap<DTNHost, List<Duration>>();
        this.thisHostSet = new HashSet<DTNHost>();
        this.per = new HashMap<>();        
     }

        @Override
        public void changedConnection (Connection con) 
        {   
            DTNHost other = con.getOtherNode(getHost());
            if (con.isUp()) {
            startTimeStamps.put(other, SimClock.getTime());
            } else {
                if (startTimeStamps.containsKey(other))
                {
                    double startTime = startTimeStamps.remove(other);
                    double duration = SimClock.getTime() - startTime;

                    List <Duration> history;

                    if (!connHistory.containsKey(other)) {
                        history = new LinkedList<Duration>();
                        connHistory.put(other, history);
                    } else {
                        history = connHistory.get(other);
                    }
                    history.add(new Duration(duration, SimClock.getTime()));
                }

                 /**
                 * Update connHistory, Total FriendRank, and totalFriend and save it in per
                 * every time connection Down
                 */

                  // i use iterator fo loop
                Iterator <Map.Entry<DTNHost, List<Duration >>> iterator = connHistory.entrySet().iterator();

               while (iterator.hasNext()) {

               Map.Entry<DTNHost, List <Duration>> entry = iterator.next();

               double friendRank = calculatePer(entry.getKey());

              //get total number of friend
               Set <DTNHost> Fj = new HashSet<>(connHistory.keySet());
               Fj.add(other);
               int totalFriend = Fj.size();

               //create a new tuple with the friend rank adn total number of friend
               TupleDe <Double, Integer> tuple = new TupleDe<>(friendRank, totalFriend);

               //update the tuple in the per map
               per.put(other, tuple);
        }
            }
        }
     /**
     * Calculates the PeopleRank for a given host based on the formula:
     * PeR(Ni) = (1 - d) + d * Σ PeR(Nj) / |F(Nj)|
     * 
     * Where:
     * - PeR(Ni) is the PeopleRank for the current host.
     * - d is the damping factor obtained from the setting. If not specified, it
     * defaults to 0.75.
     * - PeR(Nj) is the ranking of other connected nodes (friends).
     * - |F(Nj)| is the total number of friends of other nodes.
     * 
     * @param host The host for which to calculate the PeopleRank.
     * @return The PeopleRank for the specified host.
     */

     public double calculatePer (DTNHost host)
     {
        double dampingFactor = this.dumpingFactor;

        double sum = 0.0;

        int totalFriend = 0;
        for (TupleDe<Double, Integer> tuple : per.values())
        {
            if (!tuple.getFirst().equals(host)) 
            {
                double friendRanking = tuple.getFirst();
                int friendOfOtherHost = tuple.getSecond();

                if (friendOfOtherHost > 0) 
                {
                    sum +=friendRanking / totalFriend;
                }
            }
        }
        return (1 - dumpingFactor) + dampingFactor * sum ;
     }

    
     @Override
     public void update()
     {
        super.update();
        if (!canStartTransfer() || isTransferring()) {
            return;
        }

        if (exchangeDeliverableMessages() != null)
        {
            return;
        }

     }

     

      /**
     * Determines whether a message should be sent from this host to another host
     * based on the PeopleRank routing algorithm.
     * 
     * @param m         The message to be sent.
     * @param thisHost  The current host from which the message originates.
     * @param otherHost The destination host to which the message should be sent.
     * @return True if the message should be sent to the other host, false
     *         otherwise.
     */

     private boolean sendMessage (Message m, DTNHost otherHost, DTNHost thisHost)
     {
        // Check if the destination of the message is the other host
        if (m.getTo() == otherHost) {
            return true; // Message should be sent directly to the destination
        }
        // Calculate PeopleRank for this host and other host
        double perThisHost = calculatePer(thisHost);
        double perOtherHost = calculatePer(otherHost);

        // Initialize F(i) as the set of friends of i
        Set<DTNHost> Fi = new HashSet<>(connHistory.keySet());
        Fi.add(thisHost);

        // Check if this host is in contact with the other host or already friend
        if (connHistory.containsKey(otherHost) || thisHostSet.contains(otherHost)) {
            // while 1 do
            while (true) {
                // while i is in contact with j do
                for (Map.Entry<DTNHost, List<Duration>> entry : connHistory.entrySet()) {
                    if (entry.getKey().equals(otherHost)) {
                        Iterator<DTNHost> iterator = Fi.iterator();
                        while (iterator.hasNext()) {
                            DTNHost check = iterator.next();
                            if (otherHost.equals(check)) { // if j ∈ F(i) then
                                // System.out.println("Check Fi : " + check + "other host : " + otherHost);
                                return true;
                            } else if (!check.equals(otherHost)) { // if j !∈ F(i) then
                                // System.out.println("false");
                                return false;
                            }
                        }
                    }
                    // while ∃ m ∈ buffer(i) do
                    Buffer messageBuffer = new Buffer(); // Instantiate Buffer with settings
                    int bufferSize = messageBuffer.getBufferSize(thisHost);
                    while (bufferSize > 0) {
                        if (perOtherHost >= perThisHost || otherHost.equals(m.getTo())) {
                            return true; // Condition met, Forward
                        }
                    }
                }

                // If the destination host is not in contact with the current host, check the
                // end while
            }
        }
        return false; // Otherwise, do not send the message to other host
     }

     @Override
     public Message messageTransferred(String id, DTNHost from)
     {
        // Retrieve the message that was transferred
    Message m = super.messageTransferred(id, from);
    
    // Get the current host and the other hosts in the network
    DTNHost thisHost = getHost();
    List<Connection> connections = thisHost.getConnections();
    
    // Loop through the connected hosts
    for (Connection con : connections) {
        DTNHost otherHost = con.getOtherNode(thisHost);
        
        // Call the PeopleRank routing algorithm to determine if the message should be sent
        if (sendMessage(m, otherHost, thisHost)) {
            if (true) { // Example condition; replace with your actual logic
                return m; // Return the message if it's successfully sent
            }
        }
    }
    
    return null; // If no message is forwarded, return null
     }
    @Override
    public  PeopleRankActiveRouter replicate () {
        return new PeopleRankActiveRouter (this);
    }

}
