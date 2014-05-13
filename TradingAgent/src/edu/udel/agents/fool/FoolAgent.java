package edu.udel.agents.fool;

import se.sics.tasim.aw.Agent;
import se.sics.tasim.aw.Message;
import se.sics.tasim.props.StartInfo;
import se.sics.tasim.props.SimulationStatus;
import se.sics.isl.transport.Transportable;
import edu.umich.eecs.tac.props.*;

import java.util.*;

//Test commit
/**
 * This class is a skeletal implementation of a TAC/AA agent.
 *
 * @author Patrick Jordan
 * @see <a href="http://aa.tradingagents.org/documentation">TAC AA Documentation</a>
 */
public class FoolAgent extends Agent {
    /**
     * Basic simulation information. {@link StartInfo} contains
     * <ul>
     * <li>simulation ID</li>
     * <li>simulation start time</li>
     * <li>simulation length in simulation days</li>
     * <li>actual seconds per simulation day</li>
     * </ul>
     * An agent should receive the {@link StartInfo} at the beginning of the game or during recovery.
     */
    private StartInfo startInfo;

    /**
     * Basic auction slot information. {@link SlotInfo} contains
     * <ul>
     * <li>the number of regular slots</li>
     * <li>the number of promoted slots</li>
     * <li>promoted slot bonus</li>
     * </ul>
     * An agent should receive the {@link SlotInfo} at the beginning of the game or during recovery.
     * This information is identical for all auctions over all query classes.
     */
    protected SlotInfo slotInfo;

    /**
     * The retail catalog. {@link RetailCatalog} contains
     * <ul>
     * <li>the product set</li>
     * <li>the sales profit per product</li>
     * <li>the manufacturer set</li>
     * <li>the component set</li>
     * </ul>
     * An agent should receive the {@link RetailCatalog} at the beginning of the game or during recovery.
     */
    protected RetailCatalog retailCatalog;

    /**
     * The basic advertiser specific information. {@link AdvertiserInfo} contains
     * <ul>
     * <li>the manufacturer specialty</li>
     * <li>the component specialty</li>
     * <li>the manufacturer bonus</li>
     * <li>the component bonus</li>
     * <li>the distribution capacity discounter</li>
     * <li>the address of the publisher agent</li>
     * <li>the distribution capacity</li>
     * <li>the address of the advertiser agent</li>
     * <li>the distribution window</li>
     * <li>the target effect</li>
     * <li>the focus effects</li>
     * </ul>
     * An agent should receive the {@link AdvertiserInfo} at the beginning of the game or during recovery.
     */
    protected AdvertiserInfo advertiserInfo;

    /**
     * The basic publisher information. {@link PublisherInfo} contains
     * <ul>
     * <li>the squashing parameter</li>
     * </ul>
     * An agent should receive the {@link PublisherInfo} at the beginning of the game or during recovery.
     */
    protected PublisherInfo publisherInfo;

    /**
     * The list contains all of the {@link SalesReport sales report} delivered to the agent.  Each
     * {@link SalesReport sales report} contains the conversions and sales revenue accrued by the agent for each query
     * class during the period.
     */
    protected Queue<SalesReport> salesReports;

    /**
     * The list contains all of the {@link QueryReport query reports} delivered to the agent.  Each
     * {@link QueryReport query report} contains the impressions, clicks, cost, average position, and ad displayed
     * by the agent for each query class during the period as well as the positions and displayed ads of all advertisers
     * during the period for each query class.
     */
    protected Queue<QueryReport> queryReports;

    private String publisherAddress;
    /**
     * List of all the possible queries made available in the {@link RetailCatalog retail catalog}.
     */
    protected Set<Query> querySpace;
    
    Map<Query, Double> impressions = new HashMap<Query, Double>();
    Map<Query, Double> clicks = new HashMap<Query, Double>();
    Map<Query, Double> conversions = new HashMap<Query, Double>();
    Map<Query, Double> values = new HashMap<Query, Double>();
    
    Map<String, Set<Query>> queriesForComponent = new HashMap<String, Set<Query>>();
    
    Map<String, Set<Query>> queriesForManufacturer = new HashMap<String, Set<Query>>();


    public FoolAgent() {
        salesReports = new LinkedList<SalesReport>();
        queryReports = new LinkedList<QueryReport>();
        querySpace = new LinkedHashSet<Query>();
    }

    /**
     * Processes the messages received the by agent from the server.
     *
     * @param message the message
     */
    protected void messageReceived(Message message) {
        Transportable content = message.getContent();

        if (content instanceof QueryReport) {
            handleQueryReport((QueryReport) content);
        } else if (content instanceof SalesReport) {
            handleSalesReport((SalesReport) content);
        } else if (content instanceof SimulationStatus) {
            handleSimulationStatus((SimulationStatus) content);
        } else if (content instanceof PublisherInfo) {
            handlePublisherInfo((PublisherInfo) content);
        } else if (content instanceof SlotInfo) {
            handleSlotInfo((SlotInfo) content);
        } else if (content instanceof RetailCatalog) {
            handleRetailCatalog((RetailCatalog) content);
        } else if (content instanceof AdvertiserInfo) {
            handleAdvertiserInfo((AdvertiserInfo) content);
        } else if (content instanceof StartInfo) {
            handleStartInfo((StartInfo) content);
        }
    }

    /**
     * Sends a constructed {@link BidBundle} from any updated bids, ads, or spend limits.
     */
    protected void sendBidAndAds() {
        BidBundle bidBundle = new BidBundle();

        String publisherAddress = advertiserInfo.getPublisherId();

        for(Query query : querySpace) {
            // The publisher will interpret a NaN bid as
            // a request to persist the prior day's bid
            //double bid = Double.NaN;
            // bid = [ calculated optimal bid ]
        	
            // The publisher will interpret a null ad as
            // a request to persist the prior day's ad
            Ad ad = getAd(query);
            // ad = [ calculated optimal ad ]
        	Product product = ad.getProduct();
        	double bidBase = 0.8;
        	double bid = Math.min(0.2*retailCatalog.getSalesProfit(product), bidBase * BidModifier(query));
        	
        	
            // The publisher will interpret a NaN spend limit as
            // a request to persist the prior day's spend limit
            double spendLimit = Double.NaN;
            // spendLimit = [ calculated optimal spend limit ]


            // Set the daily updates to the ad campaigns for this query class
            bidBundle.addQuery(query,  bid, ad);
            bidBundle.setDailyLimit(query, spendLimit);
        }

        // The publisher will interpret a NaN campaign spend limit as
        // a request to persist the prior day's campaign spend limit
        double campaignSpendLimit = Double.NaN;
        // campaignSpendLimit = [ calculated optimal campaign spend limit ]


        // Set the daily updates to the campaign spend limit
        bidBundle.setCampaignDailySpendLimit(campaignSpendLimit);

        // Send the bid bundle to the publisher
        if (publisherAddress != null) {
            sendMessage(publisherAddress, bidBundle);
        }
    }

    /**
     * This constructs a proper Ad for a certain query
     * @param query
     * @return
     */
    private Ad getAd(Query query) {
		// TODO make this more suitable for F0 types
    	if (getType(query) == 2) {
    		//Trivial for F2
    		Product product = new Product(query.getManufacturer(),query.getComponent());
    		Ad ad = new Ad(product);
    		return ad;}
    	else if (getType(query) == 1) {
    		//Little complex for F1
    		Product product;
    		if (query.getManufacturer() != null) {
    			//F1 manufacturer
    			String component = rankComponent(query.getManufacturer());
    			product = new Product(query.getManufacturer(),component);
    		} else {
    			//F1 product
    			String manufacturer = rankManufacturer(query.getComponent());
    			product = new Product(manufacturer,query.getComponent()); 			
    		}
    		Ad ad = new Ad(product);
    		return ad;
    	} else {
    		//Most complex for F0
    		Product product;
    		product = new Product(advertiserInfo.getManufacturerSpecialty(),advertiserInfo.getComponentSpecialty());
    		Ad ad = new Ad(product);
    		return ad;
    	}
	}
    
    private String rankManufacturer(String component) {
		// TODO Auto-generated method stub
    	Set<Query> queryForComponent = queriesForComponent.get(component);
    	HashMap<Query, Double> scoreForQueries = new HashMap<Query, Double>();
    	for (Query query : queryForComponent) {
    		double score = getRankModifier(rankQuery(query,queryForComponent)) * getSpecialModifier(query);
    		scoreForQueries.put(query, score);
    	}
    	
        ValueComparator vc = new ValueComparator(scoreForQueries);
        TreeMap<Query, Double> sorted = new TreeMap<Query, Double>(vc);
        sorted.putAll(scoreForQueries);
		return sorted.lastKey().getManufacturer();
	}

	private String rankComponent(String manufacturer) {
		// TODO Auto-generated method stub
    	Set<Query> queryForManufacturer = queriesForManufacturer.get(manufacturer);
    	HashMap<Query, Double> scoreForQueries = new HashMap<Query, Double>();
    	for (Query query : queryForManufacturer) {
    		double score = getRankModifier(rankQuery(query,queryForManufacturer)) * getSpecialModifier(query);
    		scoreForQueries.put(query, score);
    	}
    	
        ValueComparator vc = new ValueComparator(scoreForQueries);
        TreeMap<Query, Double> sorted = new TreeMap<Query, Double>(vc);
        sorted.putAll(scoreForQueries);
		return sorted.lastKey().getComponent();
	}

	/**
     * This computes the weight for a certain query, the larger the weight is, the more money goes to the bidding for this query
     * @param query
     * @return
     */
    private double BidModifier(Query query) {
    	double rankModifier = getRankModifier(rankQuery(query, querySpace));
    	double specialModifier = getSpecialModifier(query);
    	double typeModifier = getTypeModifier(getType(query));
    	return rankModifier*specialModifier*typeModifier;
    }

    /**
     * This computes the modifier for items that we are specialized in
     * @param query
     * @return
     */
	private double getSpecialModifier(Query query) {
		// TODO Fine tune numbers
		if (query.getComponent() == advertiserInfo.getComponentSpecialty() && query.getManufacturer() == advertiserInfo.getManufacturerSpecialty())
			return 1.44;
		else if (query.getComponent() != advertiserInfo.getComponentSpecialty() && query.getManufacturer() != advertiserInfo.getManufacturerSpecialty())
			return 1;
		
		return 1.2;
	}

	/**
	 * This computes the modifier for different types of queries
	 * @param type
	 * @return
	 */
	private double getTypeModifier(int type) {
		// TODO Fine tune numbers
		if (type == 2)
			//F2 highest
			return 1.2;
		else if (type == 0)
			//F0 lowest
			return 0.8;
		//F1 middle
		return 1;
	}

	/**
	 * This returns the type of a query
	 * @param query
	 * @return 0 for F0, 1 for F1, 2 for F2
	 */
	private int getType(Query query) {
		// Check F2
		if (query.getComponent() != null && query.getManufacturer() != null)
			return 2;
		// Check F0
		if (query.getComponent() == null && query.getManufacturer() == null)
			return 0;
		// Otherwise F1
		return 1;
	}

	/**
	 * This computes the modifier based on query's popularity ranking
	 * @param rank
	 * @return
	 */
	private double getRankModifier(double rank) {
		// TODO Fine tune numbers
		double lambda = 0.18;
		// rank here is a double between 0 and 1, 0 for highest rank, 1 for lowest rank
		// The maximum rank modifier is exp(lambda)
		return Math.exp(lambda*(1-rank));
	}

	/**
	 * This returns the popularity ranking of a certain query
	 * @param k
	 * @return 0 for highest ranking and 1 for lowest ranking
	 */
	private double rankQuery(Query k, Set<Query> kList) {
		double kImpression = impressions.get(k);
		double rank = 0.;
		double totalRank = 0.;
		for (Query queryItem : kList) {
			totalRank ++;
			if (impressions.get(queryItem) > kImpression)
				rank = rank + 1.;
		}
		return rank / totalRank;
	}

	/**
     * Processes an incoming query report.
     *
     * @param queryReport the daily query report.
     */
    protected void handleQueryReport(QueryReport queryReport) {
		for (Query query : querySpace) {

			int index = queryReport.indexForEntry(query);
			if (index >= 0) {
				impressions.put(query,impressions.get(query)+queryReport.getImpressions(index));
				clicks.put(query, clicks.get(query)+queryReport.getClicks(index));
			}
		}
    }

    /**
     * Processes an incoming sales report.
     *
     * @param salesReport the daily sales report.
     */
    protected void handleSalesReport(SalesReport salesReport) {
        salesReports.add(salesReport);
		for (Query query : querySpace) {

			int index = salesReport.indexForEntry(query);
			if (index >= 0) {
				conversions.put(query,conversions.get(query)+salesReport.getConversions(index));
				values.put(query, values.get(query)+salesReport.getRevenue(index));
			}
		}
    }

    /**
     * Processes a simulation status notification.  Each simulation day the {@link SimulationStatus simulation status }
     * notification is sent after the other daily messages ({@link QueryReport} {@link SalesReport} have been sent.
     *
     * @param simulationStatus the daily simulation status.
     */
    protected void handleSimulationStatus(SimulationStatus simulationStatus) {
        sendBidAndAds();
    }

    /**
     * Processes the publisher information.
     * @param publisherInfo the publisher information.
     */
    protected void handlePublisherInfo(PublisherInfo publisherInfo) {
        this.publisherInfo = publisherInfo;
    }

    /**
     * Processrs the slot information.
     * @param slotInfo the slot information.
     */
    protected void handleSlotInfo(SlotInfo slotInfo) {
        this.slotInfo = slotInfo;
    }

    /**
     * Processes the retail catalog.
     * @param retailCatalog the retail catalog.
     */
    protected void handleRetailCatalog(RetailCatalog retailCatalog) {
        this.retailCatalog = retailCatalog;

        // The query space is all the F0, F1, and F2 queries for each product
        // The F0 query class
        if(retailCatalog.size() > 0) {
            querySpace.add(new Query(null, null));
        }
        

        for(Product product : retailCatalog) {
            // The F1 query classes
            // F1 Manufacturer only
            querySpace.add(new Query(product.getManufacturer(), null));
            // F1 Component only
            querySpace.add(new Query(null, product.getComponent()));
            // The F2 query class
            querySpace.add(new Query(product.getManufacturer(), product.getComponent()));
            
        	queriesForComponent.put(product.getComponent(), new LinkedHashSet<Query> ());
        	queriesForManufacturer.put(product.getManufacturer(), new LinkedHashSet<Query> ());
        }
        
		for (Query query : querySpace) {
			impressions.put(query, 100.);
			clicks.put(query, 9.);
			conversions.put(query,1.); 
			values.put(query, retailCatalog.getSalesProfit(0));
		
			if ( query.getComponent() != null && query.getManufacturer() != null) {
				queriesForComponent.get(query.getComponent()).add(query);
				queriesForManufacturer.get(query.getManufacturer()).add(query);}
		}
		
    }

    /**
     * Processes the advertiser information.
     * @param advertiserInfo the advertiser information.
     */
    protected void handleAdvertiserInfo(AdvertiserInfo advertiserInfo) {
        this.advertiserInfo = advertiserInfo;
        publisherAddress = advertiserInfo.getPublisherId();
    }

    /**
     * Processes the start information.
     * @param startInfo the start information.
     */
    protected void handleStartInfo(StartInfo startInfo) {
        this.startInfo = startInfo;
    }

    /**
     * Prepares the agent for a new simulation.
     */
    protected void simulationSetup() {
    }

    /**
     * Runs any post-processes required for the agent after a simulation ends.
     */
    protected void simulationFinished() {
        salesReports.clear();
        queryReports.clear();
        querySpace.clear();    
        impressions.clear();
        clicks.clear();
        conversions.clear();
        values.clear();

    }
    
    static class ValueComparator implements Comparator<Query> {

        Map<Query, Double> base;

        ValueComparator(Map<Query, Double> base) {
            this.base = base;
        }

        @Override
        public int compare(Query a, Query b) {
        	Double x = base.get(a);
        	Double y = base.get(b);
            if (x.equals(y)) {
                return -1;
            }
            return x.compareTo(y);
        }
    }
    
}
