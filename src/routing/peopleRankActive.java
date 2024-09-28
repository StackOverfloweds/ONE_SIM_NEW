/*
 * Copyright 2024 HaiPigGi (Bryan)
 * 
 */
package routing;

import core.*;
import java.util.*;
import javax.print.attribute.standard.MediaSize;
import routing.community.*;
import routing.util.TupleDe;

public class peopleRankActive extends ActiveRouter {
    /*Initialitation variable Dumping factor to empty */
    public static final String DUMPING_FACTOR_SETTING = "dumpingFactor";
    public static final String TRESHOLD_SETTING = "treshold";

    /** Prophet router's setting namespace ({@value})*/ 
	public static final String PeopleRank_NS = "PeopleRank";

    // Community detection and damping factor
    protected double dumpingFactor; // Damping factor used in the PeopleRank algorithm
    protected double treshold; // Threshold for considering connections

    // create data structure 
    protected Map <DTNHost, TupleDe <Double, Integer>> per;
    protected Map <DTNHost, List<Duration>> connHistory;
    protected Map<DTNHost,Double> startTimeStamps;
    protected Set <DTNHost> thisHostSet;
    public peopleRankActive (Settings s)
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

     public peopleRankActive (peopleRankActive pr) 
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
    public  peopleRankActive replicate () {
        peopleRankActive p = new peopleRankActive(this);
        return p;
    }

}