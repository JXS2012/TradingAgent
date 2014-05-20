package edu.udel.agents.fool;

import se.sics.tasim.aw.Agent;
import se.sics.tasim.aw.Message;
import se.sics.tasim.props.StartInfo;
import se.sics.tasim.props.SimulationStatus;
import se.sics.isl.transport.Transportable;
import edu.umich.eecs.tac.props.*;

import java.util.*;

import net.sf.javaml.*;
import net.sf.javaml.clustering.KMeans;
import net.sf.javaml.core.Dataset;
import net.sf.javaml.core.DefaultDataset;
import net.sf.javaml.core.DenseInstance;
import net.sf.javaml.core.Instance;
import net.sf.javaml.tools.DatasetTools;

//git@github.com/JXS2012/TradingAgent.git
/**
 * This class is a skeletal implementation of a TAC/AA agent.
 *
 * @author Patrick Jordan
 * @see <a href="http://aa.tradingagents.org/documentation">TAC AA Documentation</a>
 */
public class FoolAgent extends Agent {
	
	/** User defined constants **/
	
	//Percentage of total revenue to be spent on ads
	double adRevenueRatioPercent = 0.2;
	
	//Percent
	double baseBidPerProductRevenuePercent = 0.09;
	
	//Current simulation day
	int simulationDay = 0;
	
	//Agressive bidding initial days
	int initialSimulationDays = 5;
	
	//Agressive bid percent
	double aggressiveBidPercent = 1.0;
	
	//Agressive bid percent
	double spikeBidPercent = 1.1;
	
	//Maximum bid multiplicative factor
	double maxBidFactor = 4;
	
	//K-Means Clustering object
	KMeans kmeansClusterer = new KMeans(2);
	
	//Spike interval percent
	double minSpikeImpressionsDifference = 15;
		
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
    protected List<SalesReport> salesReports;

    /**
     * The list contains all of the {@link QueryReport query reports} delivered to the agent.  Each
     * {@link QueryReport query report} contains the impressions, clicks, cost, average position, and ad displayed
     * by the agent for each query class during the period as well as the positions and displayed ads of all advertisers
     * during the period for each query class.
     */
    protected List<QueryReport> queryReports;
    
    private Queue<SalesReport> conversionInWindow;

    private String publisherAddress;
    /**
     * List of all the possible queries made available in the {@link RetailCatalog retail catalog}.
     */
    protected Set<Query> querySpace;
    
    Map<Query, Double> impressions = new HashMap<Query, Double>();
    Map<Query, Double> clicks = new HashMap<Query, Double>();
    Map<Query, Double> conversions = new HashMap<Query, Double>();
    Map<Query, Double> values = new HashMap<Query, Double>();
    
    //Minimum bids for each product
    Map<Query, Double> baseBid = new HashMap<Query, Double>();
    
    //Maximum bids for each product
    Map<Query, Double> maxBid = new HashMap<Query, Double>();
    
    //Spike status of each product
    Map<Query, Boolean> spikeDetect = new HashMap<Query, Boolean>();
    Map<Query, Boolean> spikeDetectPreviousDay = new HashMap<Query, Boolean>();
    
    //Impressions dataset for each product
    Map<Query, List<Double>> impressionData = new HashMap<Query, List<Double>>();
    
    Map<String, Set<Query>> queriesForComponent = new HashMap<String, Set<Query>>();
    
    Map<String, Set<Query>> queriesForManufacturer = new HashMap<String, Set<Query>>();
    
    QueryReport currQueryReport;


    public FoolAgent() {
        salesReports = new LinkedList<SalesReport>();
        queryReports = new LinkedList<QueryReport>();
        querySpace = new LinkedHashSet<Query>();
        conversionInWindow = new LinkedList<SalesReport>();
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
        
        //String publisherAddress = advertiserInfo.getPublisherId();

        for(Query query : querySpace) {
            // The publisher will interpret a NaN bid as
            // a request to persist the prior day's bid
            //double bid = Double.NaN;
            // bid = [ calculated optimal bid ]
        	
            // The publisher will interpret a null ad as
            // a request to persist the prior day's ad
            Ad ad = getAd(query);
            // ad = [ calculated optimal ad ]
        	//Product product = ad.getProduct();
        	
        	double bid = computeBid(query);	
        	
        	
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
        	//System.out.println(publisherAddress);
            sendMessage(publisherAddress, bidBundle);
        }
    }
    
    /**
     * This computes the bid value
     * @param query
     * @return bid value
     */    
    private double computeBid(Query query)	{

    	double bidBase = baseBid.get(query);
    	double bid = 0;
		Ad ad = getAd(query);
		Product product;
		if (ad.getProduct() != null)
			product = ad.getProduct();
		else
			product = new Product(null, null);

    	if (simulationDay <= initialSimulationDays)	{
    		//System.out.println("Initial simulation day");
    		bid = Math.max(bidBase , bidBase * aggressiveBidPercent * BidModifier(query));
    		System.out.println("Curr Product: "+product.getManufacturer()+"\t"+
    				product.getComponent()+"\t"+
    				"Initial"+"\t"+
    				baseBid.get(query)+"\t"+
    				maxBid.get(query)+"\t"+
    				bid);
    		
    	} else if (spikeDetect.get(query)) {
    		bid = Math.max(bidBase , bidBase * spikeBidPercent * BidModifier(query));
    		
    		System.out.println("Curr Product: "+product.getManufacturer()+"\t"+
    				product.getComponent()+"\t"+
    				"Spike"+"\t"+
    				baseBid.get(query)+"\t"+
    				maxBid.get(query)+"\t"+
    				bid);
    	} else {
    		//System.out.println("Regular simulation day");
    		bid = Math.min(maxBid.get(query), bidBase * BidModifier(query));
    		
    		System.out.println("Curr Product: "+product.getManufacturer()+"\t"+
    				product.getComponent()+"\t"+
    				"Regular"+"\t"+
    				baseBid.get(query)+"\t"+
    				maxBid.get(query)+"\t"+
    				bid);
    	}


    	return(bid);
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
    		return ad;
    	} else if (getType(query) == 1) {
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
    		product = new Product(null, null);
    		Ad ad = new Ad();
    		return ad;
    	}
	}
    
    private String rankManufacturer(String component) {
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
    	double loseModifier = getLoseModifier(query);
    	double capacityModifier = getCapacityModifier();
    	return rankModifier*specialModifier*typeModifier*loseModifier*capacityModifier;
    }

    /**
     * If we over sale, we reduce bid
     * @return
     */
    private double getCapacityModifier() {
		// TODO Auto-generated method stub
    	int capacity = advertiserInfo.getDistributionCapacity();
    	
    	double totalConversion = 0;
    	for (SalesReport s : conversionInWindow)
    	{
    		double dailyConversion = 0;
    		for (Query q : querySpace)
    			dailyConversion = dailyConversion + s.getConversions(q);
    		totalConversion = totalConversion + dailyConversion;
    	}
    	
    	double saleRate = totalConversion / capacity;
    	System.out.format("Capacity rate %f", saleRate);
    	return Math.min(1,0.9*Math.exp(1 - saleRate));
	}

	/**
     * This computes the modifier for the queries that we lose. This is a special case because we no longer no the impression of the query
     * @param query
     * @return
     */
    private double getLoseModifier(Query query) {
		// TODO Auto-generated method stub
    	double position = currQueryReport.getPosition(query, advertiserInfo.getAdvertiserId());
    	//System.out.format("for query %s %s agent %s at position %f\n", query.getManufacturer(), query.getComponent(), advertiserInfo.getAdvertiserId(), position);
    	if (Double.isNaN(position))
    	{
    		//System.out.format("for query %s %s agent %s get buffed\n", query.getManufacturer(), query.getComponent(), advertiserInfo.getAdvertiserId());
    		return 1.0;	
    	}
    	else
    		return 1.0;
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
		double kValue = values.get(k);
		double rank = 0.;
		double totalRank = 0.;
		for (Query queryItem : kList) {
			totalRank ++;
			if (impressions.get(queryItem) > kImpression)
				rank = rank + 1.;
		}
		for (Query queryItem : kList) {
			totalRank ++;
			if (values.get(queryItem) > kValue)
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
    	currQueryReport = queryReport;
		for (Query query : querySpace) {
			int index = queryReport.indexForEntry(query);
			if (index >= 0) {
				impressions.put(query,impressions.get(query)+queryReport.getImpressions(index));
				
				if(queryReport.getImpressions(index) != 0.0)	{					
					List<Double> tempList = new ArrayList<Double>();				
					if (impressionData.containsKey(query))	{					
						tempList.addAll(impressionData.get(query));	
						//System.out.println("Impressions data before adding : "+impressionData.get(query));
					} 
					tempList.add((double)queryReport.getImpressions(index));
					impressionData.put(query, tempList);
					//System.out.println("Impressions data after adding : "+impressionData.get(query));
				}
				
				clicks.put(query, clicks.get(query)+queryReport.getClicks(index));
			}
			
			// TODO develop detectBurst(query) function so that we can increase bid accordingly
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
		
		if (conversionInWindow.size() < advertiserInfo.getDistributionWindow())
			conversionInWindow.add(salesReport);
		else
		{	conversionInWindow.remove();
			conversionInWindow.add(salesReport);
		}
    }

    /**
     * Processes a simulation status notification.  Each simulation day the {@link SimulationStatus simulation status }
     * notification is sent after the other daily messages ({@link QueryReport} {@link SalesReport} have been sent.
     *
     * @param simulationStatus the daily simulation status.
     */
    protected void handleSimulationStatus(SimulationStatus simulationStatus) {

    	simulationDay = simulationStatus.getCurrentDate();
    	
    	computeBaseBids();
    	
    	System.out.println("Current simulation date is "+simulationDay);
    	
    	System.out.println("Spike detection started");
    	spikeDetection();
    	System.out.println("Spike detection finished");
    	
        computeQueryBidLimits();
    	
        //System.out.println("Sending bids and ads started");
        sendBidAndAds();
        //System.out.println("Sending bids and ads finished");
        
        resetSpikeDetection();
    }

    /**
     * Processes the publisher information.
     * @param publisherInfo the publisher information.
     */
    protected void handlePublisherInfo(PublisherInfo publisherInfo) {
        this.publisherInfo = publisherInfo;
    }

    /**
     * Processes the slot information.
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
    	initializeSpikeDetection();
    }

    /**
     * Resets all spikes to false. This is done every day as the spike detected at 
     * day d-1 will last only the following day (day d+1).
     */
	private void resetSpikeDetection() {
		for (Query query : querySpace) {
    		spikeDetect.put(query, false);
		}
	}

    /**
     * Initialize spikes. Sets all spikes to false. 
     */
	private void initializeSpikeDetection() {
		for (Query query : querySpace) {
    		spikeDetect.put(query, false);
    		spikeDetectPreviousDay.put(query, false);
		}
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
        impressionData.clear();
        baseBid.clear();
        maxBid.clear();
        spikeDetect.clear();
        spikeDetectPreviousDay.clear();
        conversionInWindow.clear();
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
    
    /**
     * Computes the base bid value for each type of product
     */
    private void computeBaseBids()	{
    	for (Query query : querySpace) {
    		Ad ad = getAd(query);
        	Product product = ad.getProduct();  
    		//baseBid.put(query, retailCatalog.getSalesProfit(product) * baseBidPerProductRevenuePercent);
        	baseBid.put(query, 10 * baseBidPerProductRevenuePercent);
    		//System.out.println("Curr Product: "+product.getManufacturer()+"\t"+
    		//		product.getComponent()+"\t"+baseBid.get(query));
    	}
    }

    /**
     * Computes the maximum bid value for each type of product
     */
    private void computeQueryBidLimits()	{
    	SalesReport salesReport = salesReports.get(salesReports.size()-1);
    	double totalRevenuePerDay = 0;
    	double totalRevenuePerProduct = 0;
    	double maxBidCurrProduct = 0;
    	double totalProductRevenue = 0;
    	double totalRevenue = 0;
    	
		for (Query query : querySpace) {
			int index = salesReport.indexForEntry(query);
			if (index >= 0) {
				totalRevenuePerDay += salesReport.getRevenue(index);
				totalRevenuePerProduct += salesReport.getRevenue(index);
			}
			
			if(values.containsKey(query))
				totalRevenue += values.get(query);
		}
		
		for (Query query : querySpace) {
    		Ad ad = getAd(query);
        	Product product = ad.getProduct();  
        	
        	if(values.containsKey(query))
        		totalProductRevenue = values.get(query);
        	
        	//maxBidCurrProduct = retailCatalog.getSalesProfit(product) * 
        	//		(totalRevenuePerDay/totalRevenuePerProduct);
        	
        	//if (totalRevenue)
        	if(totalProductRevenue <= 0 || totalRevenue <= 0)	{
        		maxBidCurrProduct = baseBid.get(query);
        	} else {
        		//maxBidCurrProduct = maxBidFactor * retailCatalog.getSalesProfit(product) * 
        		//		(totalProductRevenue/totalRevenue);
        		maxBidCurrProduct = maxBidFactor * 10 * 
        				(totalProductRevenue/totalRevenue);
        	}        	
			maxBid.put(query, Math.max(2, maxBidCurrProduct));
			
    		//System.out.println("Curr Product: "+product.getManufacturer()+"\t"+
    		//		product.getComponent()+"\t"+baseBid.get(query)+"\t"+maxBid.get(query));

		}
    }

    private void spikeDetection()	{    	
    	
    	double clusterCenter1 = 0;
    	double clusterCenter2 = 0;
    	boolean first = true;
    	
    	if(simulationDay > 1){  
    		//System.out.println("Spike: Valid day");
    		for (Query query : querySpace) {
    			Dataset productImpressions = new DefaultDataset();
    			List<Double> currImpressionData = new ArrayList<Double>();
    			
    			if (impressionData.containsKey(query)){
    				//System.out.println("Curr impression data : "+impressionData.get(query));
    				if(impressionData.get(query).size()>=10)	{
    					//System.out.println("Spike: Adding all points");
    					currImpressionData.addAll(impressionData.get(query));
    				} else {
    					//System.out.println("Spike: Less than 10 points");
    					spikeDetectPreviousDay.put(query,false);
    					continue;
    				}
    			} else {
    				//System.out.println("Spike: No query data");
    				//System.out.println("Spike: Adding 0.0 point");
    				//currImpressionData.add(0.0);
					spikeDetectPreviousDay.put(query,false);
					continue;
    			}

    			/*double[] value = new double[currImpressionData.size()];
    			for(int i=0; i<currImpressionData.size(); i++)	{
    				value[i] = currImpressionData.get(i);
    				
    			}*/
    			
    			for(ListIterator<Double> iter = currImpressionData.listIterator(); iter.hasNext();)	{
    				double[] value = new double[] {iter.next()};
    				Instance instance = new DenseInstance(value);
    				productImpressions.add(instance);
    			}
    			
    			//System.out.println("Before kmeans");
    			Dataset[] clusteredImpressions = kmeansClusterer.cluster(productImpressions);
    			//System.out.println("After kmeans");
    			
    			System.out.println("Clustering results : "+clusteredImpressions);
    			if (clusteredImpressions.length != 2)	{
    				System.out.println("Number of clusters found is not 2 (cluster = "+clusteredImpressions.length+" )");
    				continue;
    				//System.exit(simulationDay);
    			} else {
        			spikeDetectPreviousDay.put(query,false);
        		}
    			
        		for (Dataset currCluster : clusteredImpressions){
        			Instance avgInstance = DatasetTools.average(currCluster);
        			if(first)	{
        				clusterCenter1 = avgInstance.value(0);
        				first = false;
        			} else	{
        				clusterCenter2 = avgInstance.value(0);
        			}        			
        		}
        		System.out.println("Cluster centers : "+clusterCenter1+"\t"+clusterCenter2);
        		
        		if(Math.min(clusterCenter1,clusterCenter2)!=0)	{
        			if(Math.max(clusterCenter1,clusterCenter2)/Math.min(clusterCenter1,clusterCenter2) 
        					> minSpikeImpressionsDifference)	{
        				if(!spikeDetectPreviousDay.get(query)){
        					spikeDetect.put(query, true);
        					spikeDetectPreviousDay.put(query,true);
        				} else {
        					spikeDetectPreviousDay.put(query,false);
        				}
        			} else {
        				spikeDetectPreviousDay.put(query,false);
        			}
        		} else {
        			spikeDetectPreviousDay.put(query,false);
        		}
    		}
    	}
    }    
}


