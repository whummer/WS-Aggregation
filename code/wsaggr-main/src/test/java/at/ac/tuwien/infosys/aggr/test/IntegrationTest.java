/*
 * Project 'WS-Aggregation':
 * http://www.infosys.tuwien.ac.at/prototype/WS-Aggregation/
 *
 * Copyright 2010-2012 Vienna University of Technology
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package at.ac.tuwien.infosys.aggr.test;

import io.hummer.util.Configuration;
import io.hummer.util.Util;
import io.hummer.util.misc.PerformanceInterceptor;
import io.hummer.util.test.GenericTestResult;
import io.hummer.util.test.GenericTestResult.IterationResult;
import io.hummer.util.ws.EndpointReference;
import io.hummer.util.ws.WebServiceClient;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

import org.junit.Ignore;
import org.w3c.dom.Element;

import at.ac.tuwien.infosys.aggr.AggregationClient;
import at.ac.tuwien.infosys.aggr.node.AggregatorNode;
import at.ac.tuwien.infosys.aggr.node.DataServiceNode;
import at.ac.tuwien.infosys.aggr.node.Registry;
import at.ac.tuwien.infosys.aggr.performance.AggregatorPerformanceInfo;
import at.ac.tuwien.infosys.aggr.proxy.AggregatorNodeProxy;
import at.ac.tuwien.infosys.aggr.request.RequestInput;
import at.ac.tuwien.infosys.aggr.request.WAQLQuery;
import at.ac.tuwien.infosys.aggr.strategy.AggregationStrategySimple;
import at.ac.tuwien.infosys.aggr.strategy.StrategyChain;
import at.ac.tuwien.infosys.aggr.util.ServiceStarter;
import at.ac.tuwien.infosys.aggr.util.TestUtil;
import at.ac.tuwien.infosys.aggr.xml.XQueryProcessor;
import at.ac.tuwien.infosys.test.TestServiceStarter;

@Ignore
public class IntegrationTest {

	private static Util util = new Util();
	private static boolean doSave = true;
	private static int numIterations = 20;
	
	public static String[] tickerSymbols = new String[]{
		"BIG", "BAC", "AAPL", "ABT", "B", "CSCO", "CVX", "DIS", 
		"DT", "EMC", "XOM", "F", "WFC", "FTE", "GOOG", "GS", "PG", "HBC", 
		"HMC", "HPQ", "INTC", "IBM", "JPM"};
	
	private static String testName;
	private static GenericTestResult result;
	private static int fixedStrategy = -1;
	
	static {
		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run() {
				saveResult();
			}
		});
	}
	
	private static void runIntegratedTest() throws Exception {
		//ServiceStarter.start(15, 2000, new String[]{"Voting", "Booking", "Rendering"});
		TestServiceStarter.start(15, 200, new String[]{"Voting", "Booking", "Rendering", "stockpriceGoogle"});

		Thread.sleep(5000);
		
		runBookingRequests(); 

//		runVotingRequests();
		
//		runPortfolioRequests();
		
		//runRenderRequests();
		
	}

	private static void runRenderRequests() throws Exception {
		String preparationQuery = 	"declare namespace tns=\"http://test.aggr.infosys.tuwien.ac.at/\";\n" +
				"<RenderResult>" +
				"{for $pixel in //tns:pixel " +
				"order by number($pixel/@position) " +
				"return " +
				"$pixel" +
				"}</RenderResult>";
		
		// <level> will be replaced by an integer
		Object request = "for $i in (0 to <level>) " +
				"let $start := $i*(90000 idiv <level>), " +
				"$end := ($i+1)*(90000 idiv <level>) " +
				"return " +
				"<tns:render xmlns:tns=\"http://test.aggr.infosys.tuwien.ac.at/\">" +
				"<source>theSource</source>" +
				"<start>{$start}</start>" +
				"<end>{$end}</end>" +
				"</tns:render>";
		
		String query = "declare namespace tns=\"http://test.aggr.infosys.tuwien.ac.at/\";\n" +
				"<RenderResult>" +
				"{for $pixel in //tns:pixel " +
				"where number($pixel/@position) < 90000 " + 
				"order by number($pixel/@position) " +
				"return " +
				"data($pixel/@color)" +
				"}</RenderResult>";
		
		WAQLQuery queries = new WAQLQuery();
		queries.addPreparationQuery(preparationQuery);
		queries.setQuery(query);
		
		runRequests(queries, request, "Rendering");
	}
	
	private static void runPortfolioRequests() throws Exception {
		
		DataServiceNode yahoo = new DataServiceNode();
		yahoo.setEPR(new EndpointReference("<wsa:EndpointReference xmlns:wsa=\"http://schemas.xmlsoap.org/ws/2004/08/addressing\" " +
				"xmlns:tns=\"" + Configuration.NAMESPACE + "\">" +
				"<wsa:Address>" + "http://finance.yahoo.com/q/hp?" + "</wsa:Address>" +
			"</wsa:EndpointReference>"));
		Registry.getRegistryProxy().addDataServiceNode("stockpriceYahoo", yahoo);
		DataServiceNode google = new DataServiceNode();
		google.setEPR(new EndpointReference("<wsa:EndpointReference xmlns:wsa=\"http://schemas.xmlsoap.org/ws/2004/08/addressing\" " +
				"xmlns:tns=\"" + Configuration.NAMESPACE + "\">" +
				"<wsa:Address>" + "http://www.google.com/finance/historical?" + "</wsa:Address>" +
			"</wsa:EndpointReference>"));
		
		// <tickerSymbols> will be replaced by a list like "('BIG','BAC')"
		Object request = "<inputs>" +
				"<input feature=\"stockpriceYahoo\" type=\"HTTP_GET\" contentType=\"xQuery\"><![CDATA[" +
				"for $i in <tickerSymbols>, $n in (1 to (<level> idiv 100)) " +
				"return concat('q=', $i, '&amp;num=100&amp;start=',(($n - 1)*100),'&amp;startdate=Jan+1%2C+2000')" +
				"]]></input>" +
				"</inputs>";

		// <tickerSymbols> will be replaced by a list like "('BIG','BAC')"
		// <level> will be replaced by an integer
		// <numResults> will be replaced by an integer
		request = "<inputs>" +
				"<input id=\"0\" feature=\"stockpriceGoogle\" contentType=\"xQuery\"><![CDATA[" +
				"for $symbol in ('BIG'), $n in (1 to <level>) " +
				"return " +
				"<tns:getQuotes xmlns:tns=\"http://test.aggr.infosys.tuwien.ac.at/\">" +
				"<symbol>{$symbol}</symbol>" +
				"<resultsCount><numResults></resultsCount>" +
				"</tns:getQuotes>" +
				"]]></input>" +
				"</inputs>";
		
		String preparationQuery = "let $symbol := substring-before(substring-after(//div[@id='gbar']/a[1]/@href,'?q='), '&amp;')," +
				"$prices := (for $row in //table[@id='historical_price']//tr[not(@class)] " +
				"let $date := $row//td[@class='lm']/text() " +
				"return <price>{$row//td[@class='rgt'][4]/text()}</price>) " +
				"return <prices symbol=\"{$symbol}\"><sum>{sum($prices/text())}</sum><count>{count($prices/text())}</count></prices>";

		String intermediateQuery = "<prices><sum>{sum(//sum)}</sum><count>{sum(//count)}</count></prices>";
		
		String finalizationXQuery = "declare namespace tns=\"" + Configuration.NAMESPACE + "\";\n" +
				"<Portfolio>" +
				"{for $name in distinct-values(//price/@symbol) " +
				"let $prices := //price[@symbol=$name] " +
				"return <stock symbol=\"{$name}\" avg=\"{avg($prices/text())}\">{for $price in $prices " +
				"return <price date=\"{$price/@date}\" value=\"{$price/text()}\">" +
				"</price>}</stock>" +
				"}</Portfolio>";
		
		finalizationXQuery = "<result><count>{sum(//count)}</count><avg>{sum(//sum) div sum(//count)}</avg></result>";

		finalizationXQuery.toString();
		

		String query = "for $body in //body " +
				"let $symbol := substring-before(substring-after($body//div[@id='gbar']/a[1]/@href,'?q='), '&amp;')," +
				"$prices := (for $row in $body//table[@id='historical_price']//tr[not(@class)] " +
				"let $date := $row//td[@class='lm']/text() " +
				"return <price>{$row//td[@class='rgt'][4]/text()}</price>) " +
				"return <prices symbol=\"{$symbol}\"><sum>{sum($prices/text())}</sum><count>{count($prices/text())}</count></prices>";
		
		WAQLQuery queries = new WAQLQuery();
		queries.setQuery(query);

//		System.out.println("Running 'Portfolio' test unoptimized...");
//		runRequests(queries, request, "PortfolioUnoptimized", false);
		
		queries = new WAQLQuery();
		queries.addPreparationQuery("0", preparationQuery);
		queries.setIntermediateQuery(intermediateQuery);
		queries.setQuery(finalizationXQuery);

//		System.out.println("Running 'Portfolio' test optimized...");
//		runRequests(queries, request, "PortfolioOptimized", false);

		System.out.println("Running 'Portfolio' test...");
		runRequests(queries, request, "Portfolio", false);
		
	}

	private static void runVotingRequests() throws Exception {

		String query = 	"declare namespace tns=\"http://test.aggr.infosys.tuwien.ac.at/\";\n" +
						"let $yesVotes := sum(//tns:yesVotes), $noVotes := sum(//tns:noVotes) " +
						"return <VotingResult><tns:yesVotes xmlns:tns=\"http://test.aggr.infosys.tuwien.ac.at/\">" +
							"{$yesVotes}" +
						"</tns:yesVotes><tns:noVotes xmlns:tns=\"http://test.aggr.infosys.tuwien.ac.at/\">" +
							"{$noVotes}" +
						"</tns:noVotes><tns:result xmlns:tns=\"http://test.aggr.infosys.tuwien.ac.at/\">" +
							"{$yesVotes>$noVotes}" +
						"</tns:result>" +
						"</VotingResult>";

		Object request = //Util.toElement(
				"<inputs><input feature=\"Voting\" to=\"ALL\"><tns:getVote xmlns:tns=\"http://test.aggr.infosys.tuwien.ac.at/\">" +
					"<request>test</request>" +
				"</tns:getVote></input></inputs>"
					//)
					;
		
		WAQLQuery queries = new WAQLQuery();
		queries.setQuery(query);
		queries.setIntermediateQuery(query);
		queries.addPreparationQuery("declare namespace tns=\"http://test.aggr.infosys.tuwien.ac.at/\";\n" +
						"<part><tns:yesVotes xmlns:tns=\"http://test.aggr.infosys.tuwien.ac.at/\">" +
							"{count(//tns:vote[text()='yes'])}" +
						"</tns:yesVotes><tns:noVotes xmlns:tns=\"http://test.aggr.infosys.tuwien.ac.at/\">" +
							"{count(//tns:vote[text()='no'])}" +
						"</tns:noVotes></part>");

		runRequests(queries, request, "Voting");

//		runRequests(queries, request, "VotingNumAggregators");
	}
	
	private static void runBookingRequests() throws Exception {

		String query = 	"declare namespace tns=\"http://test.aggr.infosys.tuwien.ac.at/\";\n" +
						//"<SearchResult>{" +
						"let $flights := (" +
							"for $flight in //tns:flight " +
							"let $price := $flight/tns:price " +
							"order by number($price) ascending " +
							"return $flight) " +
						"return <tns:matchingFlights xmlns:tns=\"http://test.aggr.infosys.tuwien.ac.at/\">" +
							"{subsequence($flights,1,10)}" +
						"</tns:matchingFlights>"
						//+ "}</SearchResult>"
						;

		Object request = "<input to=\"ALL\" feature=\"Booking\"><tns:getFlights xmlns:tns=\"http://test.aggr.infosys.tuwien.ac.at/\">" +
							"<request>test</request>" +
							"<resultsCount><numResults></resultsCount>" +
						"</tns:getFlights></input>";
		
		String interm = query;
	
		WAQLQuery queries = new WAQLQuery();
		queries.setIntermediateQuery(interm);
		queries.setQuery(query);
		
		runRequests(queries, request, "Booking", false);
	}
	
//	private static int getNumNodesInTree(int numChildren, int treeHeight) {
//		if(treeHeight <= 0)
//			return 1;
//		return (int)Math.pow(numChildren, treeHeight) + getNumNodesInTree(numChildren, treeHeight - 1);
//	}
	private static void runRequests(final WAQLQuery queries, final Object request, final String name) throws Exception {
		runRequests(queries, request, name, true);
	}

		
	private static void runRequests(final WAQLQuery queries, final Object request, final String name, final boolean doQuitAfterCompletion) throws Exception {
		
//		String query = queries.getQuery();
//		String preparationQuery = queries.getPreparationQuery();
		
		testName = name;
		
		final AggregationClient client = new AggregationClient(Registry.getRegistryProxy().getGateway().getEPR());

		final List<Throwable> exceptions = new LinkedList<Throwable>();
		
		int[] numsServices = new int[]{10, 50, 100, 200, 500, 750, 1000, 1500, 2000};
		int[] numsTreeHeight = {0, 1, 2};
		int[] numsResults = {500, 100, 10};
		int[] numsChildren = {2, 3};
		int[] numsParallelity = {1, 5, 10};
		Integer[] numsAggregators = {1, 3, 5, /* */7, 10, 15/*, 20, 30*/};
		StrategyChain [] strategies = new StrategyChain[]{ StrategyChain.loadDefault(null) };
		int numServicesForDifferentNumAggregatorsTest = 200;
		int numDeployedAggregators = Registry.getRegistryProxy().getAggregatorNodes().size();
		System.out.println("Number of deployed aggregator nodes: " + numDeployedAggregators);
		
		if(name.equals("Booking")) {
			numsServices = new int[]{/*10, 50,*/ 100/*, 200, 500, 750, 1000, /*1500, 2000*/};
			numsServices = new int[]{10, 50, 100, 200};
			numsTreeHeight = new int[]{1};
			numsResults = new int[]{/*10,*/ 30, /*200*/};
			numsChildren = new int[]{3};
			numsParallelity = new int[]{1, 5, 10, 20, 30, 50, 75, 100};
			numsAggregators = new Integer[]{20};
			StrategyChain c1_1 = StrategyChain.loadFromElement("<StrategyChain><strategy>" +
					"<className>at.ac.tuwien.infosys.aggr.strategy.AggregationStrategyPerformance</className>" +
					"<maxBranchFactor>14</maxBranchFactor>" +
					"<maxRequestsPerAggregator>250</maxRequestsPerAggregator>" +
					"<maxRequestsToDelegatePerPartner>250</maxRequestsToDelegatePerPartner>" +
					"</strategy></StrategyChain>", null);
			StrategyChain c1_2 = StrategyChain.loadFromElement("<StrategyChain><strategy>" +
					"<className>at.ac.tuwien.infosys.aggr.strategy.AggregationStrategyPerformance</className>" +
					"<maxBranchFactor>13</maxBranchFactor>" +
					"<maxRequestsPerAggregator>150</maxRequestsPerAggregator>" +
					"<maxRequestsToDelegatePerPartner>150</maxRequestsToDelegatePerPartner>" +
					"</strategy></StrategyChain>", null);
			StrategyChain c1_3 = StrategyChain.loadFromElement("<StrategyChain><strategy>" +
					"<className>at.ac.tuwien.infosys.aggr.strategy.AggregationStrategyPerformance</className>" +
					"<maxBranchFactor>10</maxBranchFactor>" +
					"<maxRequestsPerAggregator>40</maxRequestsPerAggregator>" +
					"<maxRequestsToDelegatePerPartner>120</maxRequestsToDelegatePerPartner>" +
					"</strategy></StrategyChain>", null);
			StrategyChain c1_4 = StrategyChain.loadFromElement("<StrategyChain><strategy>" +
					"<className>at.ac.tuwien.infosys.aggr.strategy.AggregationStrategyPerformance</className>" +
					"<maxBranchFactor>5</maxBranchFactor>" +
					"<maxRequestsPerAggregator>50</maxRequestsPerAggregator>" +
					"<maxRequestsToDelegatePerPartner>200</maxRequestsToDelegatePerPartner>" +
					"</strategy></StrategyChain>", null);
			StrategyChain c2 = StrategyChain.loadFromElement("<StrategyChain><strategy>" +
					"<className>at.ac.tuwien.infosys.aggr.strategy.AggregationStrategySimple</className>" +
					"</strategy></StrategyChain>", null);
			StrategyChain c3 = StrategyChain.loadFromElement("<StrategyChain><strategy>" +
					"<className>at.ac.tuwien.infosys.aggr.strategy.AggregationStrategyFixed</className>" +
					"</strategy></StrategyChain>", null);
			strategies = new StrategyChain[]{c3, c2, c1_1};
			strategies = new StrategyChain[]{c3, c1_1, c1_2, c1_3, c1_4, c2};
			strategies = new StrategyChain[]{c1_4, c2, c3};
			if(fixedStrategy >= 0) {
				strategies = new StrategyChain[]{strategies[fixedStrategy]};
			}
			
		} else if(name.equals("BookingNumAggregators")) {
			numsServices = new int[]{50/*, 100, 200*/};
			numsTreeHeight = new int[]{2};
			numsResults = new int[]{1};
			numsChildren = new int[]{3};
			numsParallelity = new int[]{50, 100, 1, 25};
			numsAggregators = new Integer[]{1,/*3,*/5,/*7,*/10,15,20,/*25,30*/};
			StrategyChain c1 = StrategyChain.loadFromElement("<StrategyChain><strategy>" +
					"<className>at.ac.tuwien.infosys.aggr.strategy.AggregationStrategySimple</className>" +
					"</strategy></StrategyChain>", null);
//			StrategyChain c2 = StrategyChain.loadFromElement("<StrategyChain><strategy>" +
//					"<className>at.ac.tuwien.infosys.aggr.strategy.AggregationStrategyPerformance</className>" +
//					"<maxBranchFactor>10</maxBranchFactor>" +
//					"<maxRequestsPerAggregator>50</maxRequestsPerAggregator>" +
//					"<maxRequestsToDelegatePerPartner>200</maxRequestsToDelegatePerPartner>" +
//					"</strategy></StrategyChain>", null);
//			strategies = new StrategyChain[]{c1, c2};
			strategies = new StrategyChain[]{c1};
			numServicesForDifferentNumAggregatorsTest = -1; // means "all"
		} else if(name.equals("Rendering")) {
			numsServices = new int[]{15, 30, 45};
			numsTreeHeight = new int[]{0, 1, 2};
			numsResults = new int[]{10, 100, 500};
			numsChildren = new int[]{2, 3};
		} else if(name.equalsIgnoreCase("PortfolioOptimized") || name.equalsIgnoreCase("PortfolioUnoptimized")) {
			numsServices = new int[]{1, 5, 10, 20, 30, 40, 50, 60 /*70, 100/*, 150, 200*/};
			numsParallelity = new int[]{1/*, 5, 10*/};
			numsResults = new int[]{10, 100, 200};
			numsTreeHeight = new int[]{2};
			numsChildren = new int[]{3};
			StrategyChain c1 = StrategyChain.loadFromElement("<StrategyChain><strategy>" +
					"<className>at.ac.tuwien.infosys.aggr.strategy.AggregationStrategyPerformance</className>" +
					"<maxBranchFactor>3</maxBranchFactor>" +
					"<maxRequestsPerAggregator>15</maxRequestsPerAggregator>" +
					"<maxRequestsToDelegatePerPartner>60</maxRequestsToDelegatePerPartner>" +
					"</strategy></StrategyChain>", null);
			strategies = new StrategyChain[]{c1};
		} else if(name.contains("Voting")) {
			numsServices = new int[]{100, 200, 400, 700, 1000};
			numsResults = new int[]{1};
			numsParallelity = new int[]{1};
			numsTreeHeight = new int[]{2,1,0};
			numsChildren = new int[]{2,3};
			StrategyChain c1 = StrategyChain.loadFromElement("<StrategyChain><strategy>" +
					"<className>at.ac.tuwien.infosys.aggr.strategy.AggregationStrategyFixed</className>" +
					"</strategy></StrategyChain>", null);
			strategies = new StrategyChain[]{c1};
		}

		result = new GenericTestResult();
		for(StrategyChain strategy : strategies) {
			Registry.getRegistryProxy().setNumAggregatorNodes(1000);
			List<AggregatorNode> aggregators = Registry.getRegistryProxy().getAggregatorNodes();
			for(AggregatorNode a : aggregators) {
				AggregatorNodeProxy p = new AggregatorNodeProxy(a);
				p.setStrategy(strategy);
			}
			if(aggregators.size() != numDeployedAggregators) {
				throw new Exception("Illegal number of aggregators, expected: " + numDeployedAggregators + " , actual: " + aggregators.size());
			}
			EndpointReference gatewayEPR = Registry.getRegistryProxy().getGateway().getEPR();
			AggregatorNodeProxy p = new AggregatorNodeProxy(gatewayEPR);
			p.setStrategy(strategy);
			
			int strategyKey = Arrays.asList(strategies).indexOf(strategy);
			
			for(final int numServices: numsServices) {
				final IterationResult iter = new IterationResult();
				result.getIterations().add(iter);
				Registry.getRegistryProxy().setNumServiceNodes(numServices);
				
				for(int numTreeHeight: numsTreeHeight) {
					for(int numChildren : numsChildren) {
						for(int numAggregators : numsAggregators) {
							if(numAggregators >= Collections.max(Arrays.asList(numsAggregators)) || 
									(strategy.getStrategy().get(0) instanceof AggregationStrategySimple
											&& numServices == numServicesForDifferentNumAggregatorsTest) ||
											numServicesForDifferentNumAggregatorsTest <= 0) {
							
								Registry.getRegistryProxy().setNumAggregatorNodes(1000);
								startMemoryProfiling(numDeployedAggregators);
								Thread.sleep(1000);
								
								Registry.getRegistryProxy().setNumAggregatorNodes(numAggregators);
								
								String[] features = new String[]{name};
								if(name.contains("Portfolio")) {
									features = new String[]{"stockpriceYahoo", "stockpriceGoogle"};
								}

								
								String ID = null;
								if(strategy.getStrategy().get(0).getClassName().contains("Fixed")) {
									ID = client.createTopology("tree(" + numChildren + "," + numTreeHeight + ")", features);
								}
								final String topologyID = ID;
								
								
								for(int parallelityLevel : numsParallelity) {
									
									if(parallelityLevel < 10 || numServices <= 1000) {
									
										for(final int numResults: numsResults) {
											TestUtil.numServiceResponseSize = numResults;
											
											final String key = "s" + numServices + "h" + numTreeHeight + "a" + numAggregators + "c" + numChildren + "r" + numResults + "p" + parallelityLevel + "st" + strategyKey;
											System.out.print(key + ": ");
											
											final LinkedBlockingQueue<Object> queue = new LinkedBlockingQueue<Object>();
											long beforeIterations = 0;
											for(int i = -2; i < numIterations && i * parallelityLevel <= 200; i++) {
												final int iFinal = i;
												PerformanceInterceptor.getDefaultInterceptor().reset();
												Object requestString = request;
												if(name.equals("Rendering")) {
													requestString = ((String)requestString).replaceAll("<level>", "" + numResults);
												} else if(name.contains("Portfolio")) {
													requestString = ((String)requestString).replaceAll("<level>", "" + numServices);
													requestString = ((String)requestString).replaceAll("<numResults>", "" + numResults);
		//											requestString = ((String)requestString).replaceAll("<tickerSymbols>", 
		//													TestUtil.generateListString(tickerSymbols, 9));
												} else if(name.contains("Booking")) {
													requestString = ((String)requestString).replaceAll("<numResults>", "" + numResults);
												}
												final Object requestStringFinal = requestString;
												Runnable r = new Runnable() {
													
													public void run() {
														long before = System.currentTimeMillis();
														long after = 0;
														try {
															Element response = client.aggregate(topologyID, queries, requestStringFinal);
															after = System.currentTimeMillis();
															//Util.print(response);
															//Util.print((Element)response.getFirstChild());
															List<Element> childElements = util.xml.getChildElements((Element)response.getFirstChild().getFirstChild());
															if(name.contains("Voting")) {
																int noVotes = 0; int yesVotes = 0;
																for(Element e : childElements) {
																	if(e.getTagName().contains("yesVotes"))
																		yesVotes = Integer.parseInt(e.getTextContent());
																	if(e.getTagName().contains("noVotes"))
																		noVotes = Integer.parseInt(e.getTextContent());
																}
																if((yesVotes + noVotes) != numServices) {
																	util.xml.print(response);
																	throw new Exception("Illegal number of votes: " + yesVotes + "/" + noVotes + " - " + numServices);
																}
																System.out.println("Votes: " + yesVotes + "/" + noVotes + " - " + numServices + " , " + (after-before) + "ms");
															} else if(name.equals("Rendering")) {
																//Util.print(response);
																//if(childElements.size() != 15 * 100) {
																//	throw new Exception("Illegal number of rendered pixels: " + childElements.size());
																//}
															} else if(name.contains("Portfolio")) {
																Object temp = XQueryProcessor.getInstance(). execute(response, "sum(//count)", false);
																System.out.println("Quotes: " + temp + " - " + numServices + " , " + (after-before) + "ms");
																if(((Double)temp) != numResults * numServices) {
																	//Util.print(response);
																	throw new Exception("Illegal number of stock prices: " + temp);
																}
															} else if(name.startsWith("Portfolio")) {
																util.xml.print(response);
															} else if(name.contains("Booking")) {
																int count = 0; 
																for(Element e : childElements) {
																	if(e.getTagName().contains("flight"))
																		count++;
																}
																if(count != 10) {
																	util.xml.print(response);
																	throw new Exception("Illegal number of flights: " + count + " - " + numServices);
																}
																System.out.println("Flights: " + count + " - " + numServices + " , " + (after-before) + "ms");
															}

															if(iFinal >= 0)
																iter.addEntry(key, (after-before));
															if(iFinal == 0)
																queue.put(System.currentTimeMillis());
															else
																queue.put(new Object());
														} catch (Exception e) {
															exceptions.add(e);
															try {
																if(iFinal == 0)
																	queue.put(System.currentTimeMillis());
																else
																	queue.put(new Object());
															} catch (Exception e1) { }
															System.out.println("Exception occurred in iteration " + iFinal + " of test " + key + ": " + e + (e.getCause() != null ? " - cause: " + e.getCause() : ""));
														}
													}
												};
												
												for(int j = 0; j < parallelityLevel; j++)
													new Thread(r).start();
												for(int j = 0; j < parallelityLevel; j++) {
													Object o = queue.take();
													if(o instanceof Long)
														beforeIterations = (Long)o;
												}
											}
											long afterIterations = System.currentTimeMillis();
											
											long duration = (afterIterations - beforeIterations);
											System.out.println("Duration: " + duration);
											iter.addEntry(key + "duration", duration);
											
											System.out.println(result.getMean(key));
											System.out.println(result.getValues(key));
											System.gc();
											System.gc();
											Thread.sleep(2500 + numServices);
										}
									}
								}
								
								String key = "s" + numServices + "h" + numTreeHeight + "c" + numChildren;
								getMemoryProfiles(iter, key);
							}
						}
					}
				}
			}
		}
		
		saveResult();
		
		for(Throwable t : exceptions) {
			t.printStackTrace();
		}
		if(exceptions.size() > 0)
			System.out.println("Number of occurred exceptions: " + exceptions.size());
		
		if(doQuitAfterCompletion)
			System.exit(0);
	}
	
	public static void saveResult() {
		String name = "etc/performanceTestResults_" + testName + ".xml";
		if(fixedStrategy >= 0) 
			name = "etc/performanceTestResults_" + testName + ".s" + fixedStrategy + ".xml";
		if(doSave)
			result.save(name);
	}
	
	private static void startMemoryProfiling(int expectedNumAggregators) throws Exception {
		List<AggregatorNode> aggregators = Registry.getRegistryProxy().getAggregatorNodes();
		if(aggregators.size() != expectedNumAggregators) {
			throw new Exception("Illegal number of aggregators, expected: " + expectedNumAggregators + " , actual: " + aggregators.size());
		}
		for(AggregatorNode a : aggregators) {
			AggregatorNodeProxy proxy = new AggregatorNodeProxy(a);
			proxy.startMemoryProfiler();
			proxy.getPerformanceInfo(false);
		}
		try { Thread.sleep(1000); } catch (InterruptedException e) { }
	}
	private static void getMemoryProfiles(IterationResult iter, String key) throws Exception {
		int counter = 1;
		for(AggregatorNode a : Registry.getRegistryProxy().getAggregatorNodes()) {
			AggregatorNodeProxy p = new AggregatorNodeProxy(a.getEPR());
			AggregatorPerformanceInfo info = p.getPerformanceInfo(false);
			System.out.println(a.getEPR().getAddress() + ": " + (long)info.getUsedMemory());
			iter.addEntry(key + "_m" + (counter++), (long)info.getUsedMemory());
		}
	}

	public static void main(String[] args) throws Exception {
		
		if(args.length > 0 && (args[0].equals("client") 
				|| args[0].equals("Voting")
				|| args[0].equals("Booking")
				|| args[0].equals("Rendering")
				|| args[0].equals("Portfolio"))) {
			
//			String hostAggregators = Configuration.getValue("test.host.aggregators");
//			String hostServices = Configuration.getValue("test.host.services");
//			ServiceStarter.addAggregatorsToRegistry(15, hostAggregators);
//			ServiceStarter.addDataServicesToRegistry(2000, hostServices, new String[]{"Voting", "Booking", "Rendering", "stockpriceGoogle"});
//			
//			ServiceStarter.startGateway();
			
			if(args.length > 1)
				fixedStrategy = Integer.parseInt(args[1]);
			
			if(args[0].equals("client") || args[0].equals("Portfolio"))
				runPortfolioRequests();
			if(args[0].equals("client") || args[0].equals("Booking"))
				runBookingRequests();
			if(args[0].equals("client") || args[0].equals("Voting"))
				runVotingRequests();
			if(args[0].equals("client") || args[0].equals("Rendering"))
				runRenderRequests();
			
			System.exit(0);
			
		} else if(args.length > 0 && args[0].equals("infinite")) {
			
//			doOutput = false;
			doSave = false;
			
//			String hostAggregators = Configuration.getValue("test.host.aggregators");
//			String hostServices = Configuration.getValue("test.host.services");
//			ServiceStarter.addAggregatorsToRegistry(15, hostAggregators);
//			ServiceStarter.addDataServicesToRegistry(2000, hostServices, new String[]{"Voting", "Booking", "Rendering", "stockpriceGoogle"});
			
//			ServiceStarter.startGateway();
			
			int counter = 0;
			while(true) {
				runPortfolioRequests();
				Thread.sleep(2000);
				System.out.println("Round " + (counter++) + " completed.");
			}
			
		} else if(args.length > 0 && args[0].equals("registry")) {
		
			ServiceStarter.startRegistry();
			
		} else if(args.length > 0 && args[0].equals("aggregators")) {
		
			ServiceStarter.startAggregators(15);

		} else if(args.length > 0 && args[0].equals("terminate")) {
					
//			String hostAggregators = Configuration.getValue("test.host.aggregators");
//			String hostServices = Configuration.getValue("test.host.services");
//			ServiceStarter.addAggregatorsToRegistry(15, hostAggregators);
//			ServiceStarter.addDataServicesToRegistry(2000, hostServices, new String[]{"Voting"});
			for(final AggregatorNode n : Registry.getRegistryProxy().getAggregatorNodes()) {
				new Thread() {
					public void run() {
						try {
							WebServiceClient.getClient(n.getEPR()).invoke(new RequestInput(
								util.xml.toElement("<tns:terminate xmlns:tns=\"" + Configuration.NAMESPACE + "\"/>"))
								.getRequest(), 0);
						} catch(Exception e) { } // swallow
					}
				}.start();
			}
//			try {
//				WebServiceClient.getClient(Registry.getProxy().getDataServiceNodes("Voting").get(0).getEPR()).invoke(new RequestInput(
//						Util.toElement("<tns:terminate xmlns:tns=\"http://test.aggr.infosys.tuwien.ac.at/\"/>")), 0);
//			} catch (Exception e) { }
			System.exit(0);
		} else {
	
			runIntegratedTest();
	
		}
		
	}

}
