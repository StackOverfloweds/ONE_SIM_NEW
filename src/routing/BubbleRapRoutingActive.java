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
        if (con.isUp()) {
            startTimestamps.put(other, SimClock.getTime());
        }
    }



    @Override
    public BubbleRapRoutingActive replicate () 
    {
        return new BubbleRapRoutingActive(this);
    }
}
