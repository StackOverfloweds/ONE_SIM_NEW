package routing;

import core.*;
import java.util.*;
import routing.community.*;

public class BubbleRapRoutingActive extends ActiveRouter {

    // Start-initialisation
    public static final String COMMUNITY_ALG_SETTING = "communityDetectAlg"; // added
    public static final String CENTRALITY_ALG_SETTING = "centralityAlg";

    protected Map<DTNHost, Double> startTimestamps;
    protected Map<DTNHost, List<Duration>> connHistory;

    protected CommunityDetection community; // added
    protected Centrality centrality;

     /** PeopleRank router's setting namespace ({@value}) **/ 
	public static final String BubbleRap_NS = "BubbleRapRoutingActive";
    public static final String MSG_COUNT_PROPERTY = BubbleRap_NS + "." +
		"copies";
    
    /**
     * create consructor
     * @param s
     */
    public BubbleRapRoutingActive (Settings s)
    {
        super(s);
        Settings BubbleRap = new Settings(BubbleRap_NS);
        if (BubbleRap.contains(COMMUNITY_ALG_SETTING)) 
        {
            this.community = (CommunityDetection) s.createIntializedObject(s.getSetting(COMMUNITY_ALG_SETTING));
        } else {
            this.community = new SimpleCommunityDetection(s); //default setting
        } 
        if (s.contains(CENTRALITY_ALG_SETTING)) {
            this.centrality = (Centrality) s.createIntializedObject(s.getSetting(CENTRALITY_ALG_SETTING));
        } else {
            this.centrality = new AverageWinCentrality1(s);
        }
    }

    /**
     * create copy constructor
     * @param proto
     * 
     */
    public BubbleRapRoutingActive (BubbleRapRoutingActive proto)
    {
        super (proto);
        this.community = proto.community.replicate();
        this.centrality = proto.centrality.replicate();
        startTimestamps = new HashMap<DTNHost, Double>();
        connHistory = new HashMap<DTNHost, List<Duration>>();
    }

    @Override
    public void changedConnection (Connection con) 
    {
        DTNHost other = con.getOtherNode(getHost());
        DTNHost thisHost = getHost();
        if (con.isUp()) {
            startTimestamps.put(other, SimClock.getTime());
        } else {
            double time = check(thisHost, other);
            double eTime = SimClock.getTime();

            List <Duration> history;
            if (!connHistory.containsKey(other)) {
                history = new LinkedList<Duration>();
                connHistory.put(other, history);
            } else {
                history = connHistory.get(other);
            }

            if (eTime - time >0)
            {
                history.add(new Duration(time,eTime));
            }

            CommunityDetection peerCD = this.getRouter(other).community;
            community.connectionLost(thisHost, other, peerCD, history);

            startTimestamps.remove(other);

        }
    }
    //create to check time contact this host and other
    private  double check (DTNHost thisHost, DTNHost peer)
    {
        if (startTimestamps.containsKey(thisHost)) {
            startTimestamps.get(peer);
        }
        return 0;
    }
    private BubbleRapRoutingActive getRouter(DTNHost h) {
        MessageRouter otherRouter = h.getRouter();
        assert otherRouter instanceof ActiveRouter : "This router only works with other routers of the same type";
    
        return (BubbleRapRoutingActive) otherRouter;
    }
    



    @Override
    public BubbleRapRoutingActive replicate () 
    {
        return new BubbleRapRoutingActive(this);
    }
}
