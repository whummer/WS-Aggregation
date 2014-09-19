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

package at.ac.tuwien.infosys.events.test;

import io.hummer.util.Configuration;
import io.hummer.util.test.GenericTestResult;
import io.hummer.util.test.GenericTestResult.IterationResult;
import io.hummer.util.test.GenericTestResult.ResultType;
import io.hummer.util.ws.AbstractNode;
import io.hummer.util.ws.EndpointReference;
import io.hummer.util.ws.WebServiceClient;
import io.hummer.util.xml.XMLUtil;

import java.net.URL;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.ws.Endpoint;

import org.junit.Ignore;

import at.ac.tuwien.infosys.aggr.AggregationClient;
import at.ac.tuwien.infosys.aggr.monitor.MonitoringSpecification;
import at.ac.tuwien.infosys.aggr.node.AggregatorNode;
import at.ac.tuwien.infosys.aggr.node.DataServiceNode;
import at.ac.tuwien.infosys.aggr.node.Gateway;
import at.ac.tuwien.infosys.aggr.node.Registry;
import at.ac.tuwien.infosys.aggr.performance.AggregatorPerformanceCollector;
import at.ac.tuwien.infosys.aggr.proxy.GatewayProxy;
import at.ac.tuwien.infosys.aggr.proxy.RegistryProxy;
import at.ac.tuwien.infosys.aggr.request.AbstractInput;
import at.ac.tuwien.infosys.aggr.request.AbstractInput.InputWrapper;
import at.ac.tuwien.infosys.aggr.request.AggregationRequest;
import at.ac.tuwien.infosys.aggr.request.EventingInput;
import at.ac.tuwien.infosys.aggr.request.RequestInput;
import at.ac.tuwien.infosys.aggr.request.WAQLQuery;
import at.ac.tuwien.infosys.aggr.strategy.Topology;
import at.ac.tuwien.infosys.aggr.strategy.TopologyOptimizerVNS.OptimizationParameters;
import at.ac.tuwien.infosys.aggr.strategy.TopologyUtil;
import at.ac.tuwien.infosys.aggr.testbed.TestbedMain;
import at.ac.tuwien.infosys.aggr.util.TestUtil;
import at.ac.tuwien.infosys.events.EventReceiverService;
import at.ac.tuwien.infosys.test.TestServiceStarter;

@Ignore
public class SelfOptimizationTest {

	private static AggregatorPerformanceCollector performances = new AggregatorPerformanceCollector(false);
	private static Map<AggregatorNode, Map<DataServiceNode, List<AggregationRequest>>> inputsPerAggr;
	private static MonitoringSpecification listener;
	private static AggregationClient client;
	//private static Util util = new Util();
	private static boolean doCreateGraphs = true;
	private static boolean onlyLocal = false;
	private static boolean deployCloud = false;
	private static int scenarioID;

	private static void fillUpServiceReferencesInRegistry(int totalNum)
			throws Exception {
		List<DataServiceNode> nodes = Registry.getRegistryProxy()
				.getDataServiceNodes("Eventing");
		int currentNum = nodes.size();
		while (currentNum < totalNum) {
			for (DataServiceNode d : nodes) {
				EndpointReference epr = new EndpointReference(d.getEPR());
				epr.setAddress(epr.getAddress() + "#foo" + currentNum);
				Registry.getRegistryProxy().addDataServiceNode("Eventing",
						new DataServiceNode(epr));
				currentNum++;
				if (currentNum >= totalNum)
					break;
			}
		}
		RegistryProxy.resetCache();
		int numServices = Registry.getRegistryProxy().getDataServiceNodes(
				"Eventing").size();
		if (numServices != totalNum) {
			throw new Exception("Unexpected number of data sources: "
					+ numServices);
		}
	}

	private static AggregationRequest getRequest(List<Integer> dataServiceIDs,
			int numDependencies, int queryWindowLength) throws Exception {
		List<DataServiceNode> dataServices = Registry.getRegistryProxy()
				.getDataServiceNodes("Eventing");

		String requestID = UUID.randomUUID().toString();
		AggregationRequest r1 = new AggregationRequest(-1, requestID, null,
				new AbstractInput.RequestInputs(), new WAQLQuery());
		int counter = 1;
		for (int dataServiceID : dataServiceIDs) {
			EventingInput in = new EventingInput();
			in.setExternalID("" + counter);
			in.setServiceURL(dataServices.get(dataServiceID).getEPR()
					.getAddress());
			String dependency = (counter <= numDependencies) ? ("$"
					+ dataServiceIDs.size() + "{''}") : "<b/>";
			in
					.setTheContent(XMLUtil
							.getInstance()
							.toElement(
									"<config><wse:Filter xmlns:wse=\"http://schemas.xmlsoap.org/ws/2004/08/eventing\"><size>10"
											+ "</size></wse:Filter>"
											+ dependency + "</config>"));
			r1.getInputs().addInput(in);
			r1.getQueries().addPreparationQuery(in.getExternalID(),
					"for tumbling window $w in $input "
							+ "start at $spos when true() "
							+ "end at $epos when ($epos - $spos) gt "
							+ queryWindowLength + " "
							+ "return <ticks><a>{$w}</a></ticks>");
			counter++;
		}
		r1.setMonitor(listener);
		return r1;
	}

	private static void takeSnapshot(GenericTestResult result,
			IterationResult test, int setting, int time) throws Exception {
		List<Topology> topologies = TopologyUtil.collectAllTopologies();

		// collect aggregator performance infos
		performances.setDetailed(true);
		performances.run();

		inputsPerAggr = new HashMap<AggregatorNode, Map<DataServiceNode, List<AggregationRequest>>>();
		List<InputWrapper> dataSources = new LinkedList<InputWrapper>();
		List<AggregatorNode> aggregators = Registry.getRegistryProxy()
				.getAggregatorNodes();
		Map<AggregatorNode, Map<InputWrapper, Double>> streamRates = new HashMap<AggregatorNode, Map<InputWrapper, Double>>();
		Map<AggregatorNode, Map<AggregatorNode, Double>> interactRates = new HashMap<AggregatorNode, Map<AggregatorNode, Double>>();
		String prefix = "s" + setting + "t" + time;

		for (Topology t : topologies) {
			for (AggregatorNode a : t.getTargetServiceRequests().keySet()) {
				if (!inputsPerAggr.containsKey(a)) {
					inputsPerAggr
							.put(
									a,
									new HashMap<DataServiceNode, List<AggregationRequest>>());
				}
				Map<DataServiceNode, List<AggregationRequest>> aggrReqs = inputsPerAggr
						.get(a);
				Map<DataServiceNode, LinkedList<AggregationRequest>> map = t
						.getTargetServiceRequests(a);
				for (DataServiceNode d : map.keySet()) {
					LinkedList<AggregationRequest> list = map.get(d);
					if (!aggrReqs.containsKey(d)) {
						aggrReqs.put(d, new LinkedList<AggregationRequest>());
					}
					aggrReqs.get(d).addAll(list);
					for (AggregationRequest r : list) {
						InputWrapper w = new InputWrapper(r.getAllInputs().get(
								0));
						if (!dataSources.contains(w))
							dataSources.add(w);
						if (!streamRates.containsKey(a))
							streamRates.put(a,
									new HashMap<InputWrapper, Double>());
						if (!streamRates.get(a).containsKey(w))
							streamRates.get(a).put(w, 0.0);
						double newRate = performances.getDataTransfer(a, w);
						streamRates.get(a).put(w,
								streamRates.get(a).get(w) + newRate);
					}
				}
			}
			for (AggregatorNode fromAggr : t.getPartners().keySet()) {
				for (AggregatorNode toAggr : t.getPartners(fromAggr)) {
					if (!interactRates.containsKey(fromAggr))
						interactRates.put(fromAggr,
								new HashMap<AggregatorNode, Double>());
					double newVal = performances.getInterAggregatorTransfer(
							fromAggr, toAggr);
					interactRates.get(fromAggr).put(toAggr, newVal);
				}
			}
		}
		for (InputWrapper w : dataSources) {
			int index = dataSources.indexOf(w);
			test.addEntry(prefix + "d" + index + "weight",
					getSubscribedAggregators(topologies, w).size());
		}
		for (AggregatorNode n : inputsPerAggr.keySet()) {
			int aggrIndex = aggregators.indexOf(n);
			int numInputs = getInputsByAggregator(n).size();
			if (numInputs > 0) {
				test.addEntry(prefix + "a" + aggrIndex + "numStreams",
						inputsPerAggr.get(n).size());
				test
						.addEntry(prefix + "a" + aggrIndex + "numInputs",
								numInputs);
			}
		}
		for (AggregatorNode fromAggr : interactRates.keySet()) {
			for (AbstractNode toNode : interactRates.get(fromAggr).keySet()) {
				int fromIndex = aggregators.indexOf(fromAggr);
				String toIndex = "a" + aggregators.indexOf(toNode);
				test.addEntry(prefix + "l" + fromIndex + "_" + toIndex
						+ "dataRate", interactRates.get(fromAggr).get(toNode));
			}
		}
		for (AggregatorNode fromAggr : streamRates.keySet()) {
			for (InputWrapper w : streamRates.get(fromAggr).keySet()) {
				int fromIndex = aggregators.indexOf(fromAggr);
				String toIndex = "d" + dataSources.indexOf(w);
				double rate = streamRates.get(fromAggr).get(w);
				if (rate > 0) {
					test.addEntry(prefix + "l" + fromIndex + "_" + toIndex
							+ "dataRate", rate);
				}
			}
		}
		// fill up those node links that exist according to the topologies
		// but did not yield any measurements.
		for (Topology t : topologies) {
			for (AggregatorNode fromAggr : t.getAllAggregators()) {
				for (AggregatorNode toAggr : t.getPartners(fromAggr)) {
					int fromIndex = aggregators.indexOf(fromAggr);
					String toIndex = "a" + aggregators.indexOf(toAggr);
					String key = prefix + "l" + fromIndex + "_" + toIndex
							+ "dataRate";
					if (result.getValues(key, false).size() <= 0) {
						test.addEntry(key, 1); // set dummy rate of 1 KB/s
					}
				}
				for (AggregationRequest r : t.getAllRequests(fromAggr)) {
					InputWrapper w = new InputWrapper(r.getAllInputs().get(0));
					int dsIndex = dataSources.indexOf(w);
					if (dsIndex >= 0) {
						int fromIndex = aggregators.indexOf(fromAggr);
						String toIndex = "d" + dsIndex;
						String key = prefix + "l" + fromIndex + "_" + toIndex
								+ "dataRate";
						if (result.getValues(key, false).size() <= 0) {
							test.addEntry(key, 1); // set dummy rate of 1 KB/s
						}
					} else {
						System.out
								.println("Could not find data source index of "
										+ w + " in " + dataSources);
					}
				}
			}
		}

		// add performance data to test result file
		double totalMem = 0;
		double totalCPU = 0;
		for (AggregatorNode a : inputsPerAggr.keySet()) {
			int aggrIndex = aggregators.indexOf(a);
			int numInputs = getInputsByAggregator(a).size();
			if (numInputs > 0) {
				double mem = performances.getMemory(a);
				double bufMem = performances.getBufferMemory(a) / 1000000.0; // output
																				// in
																				// megabytes
				double cpu = performances.getCPU(a);
				double rate = performances.getDataTransfer(a);
				totalMem += mem;
				totalCPU += cpu;
				test.addEntry(prefix + "a" + aggrIndex + "mem", mem);
				test.addEntry(prefix + "a" + aggrIndex + "bufMem", bufMem);
				test.addEntry(prefix + "a" + aggrIndex + "cpu", cpu);
				test.addEntry(prefix + "a" + aggrIndex + "rate", rate);
			}
		}
		test.addEntry(prefix + "mem", totalMem);
		test.addEntry(prefix + "cpuAvg", totalCPU / (double)inputsPerAggr.size());

		// save some general info:
		test.addEntry(prefix + "numReqs", topologies.size());
		test.addEntry(prefix + "numAggrs", inputsPerAggr.size());
		test.addEntry(prefix + "bufferMem",
				performances.getBufferMemoryTotal() / 1000000.0); // output in
																	// megabytes
		test.addEntry(prefix + "aggrDataRate", performances
				.getInterAggregatorTransfer());
		test
				.addEntry(prefix + "maxAggrLoad", performances
						.getMaxDataTransfer());
		test
				.addEntry(prefix + "minAggrLoad", performances
						.getMinDataTransfer());
		test
				.addEntry(prefix + "avgAggrLoad", performances
						.getAvgDataTransfer());
		test.addEntry(prefix + "diffAggrLoad", performances
				.getMaxMinusMinDataTransfer());
		test.addEntry(prefix + "aggrLoad", performances.getDataTransfer());
		test
				.addEntry(prefix + "aggrEventFreq", performances
						.getDataFrequency());

	}

	private static List<AggregationRequest> getInputsByAggregator(
			AggregatorNode a) {
		List<AggregationRequest> result = new LinkedList<AggregationRequest>();
		for (DataServiceNode d : inputsPerAggr.get(a).keySet()) {
			result.addAll(inputsPerAggr.get(a).get(d));
		}
		return result;
	}

	private static List<AggregatorNode> getSubscribedAggregators(
			List<Topology> tops, InputWrapper w) {
		List<AggregatorNode> result = new LinkedList<AggregatorNode>();
		for (Topology t : tops) {
			for (AggregatorNode a : t.getTargetServiceRequests().keySet()) {
				for (AggregationRequest r : t.getAllRequests(a)) {
					if (new InputWrapper(r.getAllInputs().get(0)).equals(w)) {
						result.add(a);
						break;
					}
				}
			}
		}
		return result;
	}

	private static OptimizationParameters getParameters(int scenarioID) {
		List<List<Double>> parameterSettings = new LinkedList<List<Double>>();
		parameterSettings.add(Arrays.asList(0.0, 0.0, 0.0, 1.0));
		parameterSettings.add(Arrays.asList(0.0, 0.0, 1.0, 0.0));
		parameterSettings.add(Arrays.asList(0.0, 1.0, 0.0, 0.0));
		parameterSettings.add(Arrays.asList(0.0, 1.0, 1.0, 1.0));

		OptimizationParameters params = new OptimizationParameters();
		List<Double> parameters = parameterSettings.get(scenarioID);
		params.penaltyForMigration = parameters.get(0);
		params.penaltyForDataTransmission = parameters.get(1);
		params.penaltyForAggrCardinalityDiff = parameters.get(2);
		params.penaltyForRedundantDataSources = parameters.get(3);
		params.limitForCpuUsage = 0.9;
		params.automaticallyRestartLoop = false;
		params.doApplyMigrations = true;
		params.loopDurationMS = 20000;
		return params;
	}

	private static GatewayProxy getOptimizer() throws Exception {
		if (onlyLocal)
			return new GatewayProxy(Registry.getRegistryProxy().getGateway()
					.getEPR());
		return new GatewayProxy(new EndpointReference(new URL(
				"http://dublin.vitalab.tuwien.ac.at:8895/optimizer?wsdl")));
	}

	public static void doCytographTests() throws Exception {
		int rounds = 3;
		int queriesPerRound = 10;
		int inputsPerQuery = 2;

		final GenericTestResult result = new GenericTestResult();
		IterationResult test = result.newIteration();
		Runtime.getRuntime().addShutdownHook(new Thread() {
			public void run() {
				System.out.println("Saving result file...");
				result.save("etc/results/optimizationTestResults" + scenarioID
						+ ".xml");
			}
		});

		/** set number of data services */
		fillUpServiceReferencesInRegistry(10);
		int numDataServices = Registry.getRegistryProxy().getDataServiceNodes(
				"Eventing").size();
		System.out.println("num services: " + numDataServices);

		/** now, terminate all existing queries */
		getOptimizer().setOptimization(false, null);
		/** restart aggregator nodes */
		restartAggregators();

		/** set parameters for background optimization on optimizer node */
		OptimizationParameters params = getParameters(scenarioID);

		int dataServiceIDCounter = 0;
		for (int i = 1; i <= rounds; i++) {

			System.out
					.println("Starting some queries (with round robin input assignment)...");
			for (int j = 0; j < queriesPerRound; j++) {
				List<Integer> dataServiceIDs = new LinkedList<Integer>();
				for (int k = 0; k < inputsPerQuery; k++) {
					dataServiceIDs.add((dataServiceIDCounter++)
							% numDataServices);
				}
				AggregationRequest req = getRequest(dataServiceIDs, 1, 10);
				client.createTopology(req);
			}

			while (TopologyUtil.collectAllTopologies().size() < i * rounds) {
				Thread.sleep(5000);
			}
			Thread.sleep(30000);

			System.out.println("Taking snapshot 1...");
			takeSnapshot(result, test, scenarioID, i * 2 - 1);
			System.out.println("Starting optimization...");
			getOptimizer().setOptimization(true, params);
			Thread.sleep(60000);

			getOptimizer().setOptimization(false, null);
			Thread.sleep(30000);
			System.out.println("Taking snapshot 2...");
			takeSnapshot(result, test, scenarioID, i * 2);

		}
	}

	public static void doLargeScaleTest() throws Exception {
		int rounds = 20;
		int queriesPerRound = 1;
		int inputsPerQuery = 3;
		int iterations = 2;

		final GenericTestResult result = new GenericTestResult();
		IterationResult test = result.newIteration();
		Runtime.getRuntime().addShutdownHook(new Thread() {
			public void run() {
				System.out.println("Saving result file...");
				result.save("etc/results/optimizationLargeTestResults"
						+ scenarioID + ".xml");
			}
		});

		/** set number of data services */
		fillUpServiceReferencesInRegistry(10);
		int numDataServices = Registry.getRegistryProxy().getDataServiceNodes(
				"Eventing").size();

		/** restart aggregator nodes */
		restartAggregators();

		for (int iter = 1; iter <= iterations; iter++) {
			System.out.println("starting iteration " + iter);

			try {
				getOptimizer().setOptimization(false, null);
			} catch (Exception e) {
				e.printStackTrace();
			}

			/** set parameters for background optimization on optimizer node */
			OptimizationParameters params = getParameters(scenarioID);
			params.automaticallyRestartLoop = true;
			getOptimizer().setOptimization(true, params);

			for (int i = 1; i <= rounds; i++) {

				System.out
						.println("Starting some queries (with round robin input assignment)...");
				for (int j = 0; j < queriesPerRound; j++) {
					int startID = (i - 1) * queriesPerRound * inputsPerQuery
							+ j * inputsPerQuery;
					List<Integer> dataServiceIDs = new LinkedList<Integer>();
					for (int k = 0; k < inputsPerQuery; k++) {
						dataServiceIDs.add((startID + k) % numDataServices);
					}
					AggregationRequest req = getRequest(dataServiceIDs, 1, 20);
					client.createTopology(req);
				}

				int size = TopologyUtil.collectAllTopologies()
						.size();
				while (size < i * queriesPerRound) {
					System.out
							.println("Waiting until all queries have been started ("
									+ size + "/" + (i * queriesPerRound) + ").");
					Thread.sleep(5000);
				}
				Thread.sleep(15000);

				System.out.println("Taking snapshot " + i + "...");
				long t1 = System.currentTimeMillis();
				takeSnapshot(result, test, scenarioID, i);
				System.out.println("Taking snapshot took "
						+ (System.currentTimeMillis() - t1) + "ms");

			}

		}
	}

	private static void restartAggregators() throws Exception {
		if (!onlyLocal) {
			TestUtil.restartAggregators();
		}
	}
	

	public static void main(String[] args) throws Exception {
		Logger.getAnonymousLogger().getParent().setLevel(Level.OFF);

		doCreateGraphs = false;
		onlyLocal = true;
		deployCloud = false;
		boolean startGatewayAndAggrs = false;
		boolean doLargeScaleTest = true;
		boolean doDestroyTopologies = false;
		scenarioID = 1;

		if (args.length > 0 && args[0].equals("graph"))
			doCreateGraphs = true;
		if (doCreateGraphs) {
			if (doLargeScaleTest) {

				GenericTestResult result = new GenericTestResult();

				for (int s : Arrays.asList(0, 1, 2, 3)) {
					GenericTestResult temp = GenericTestResult
							.load("etc/results/optimizationLargeTestResults"
									+ s + ".xml");
					result.mergeWith(temp);
				}
				// "bufferMem", "aggrDataRate" "maxAggrLoad" "diffAggrLoad"
				String prefix1 = "s0t<level>";
				String prefix2 = "s1t<level>";
				String prefix3 = "s2t<level>";
				String prefix4 = "s3t<level>";
				result.createGnuplot(Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9,
						10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20),
						new String[] { prefix4 + "bufferMem",
								prefix1 + "bufferMem", prefix3 + "bufferMem",
								prefix2 + "bufferMem" }, new String[] {
								"w_D=1,w_L=0,w_T=0", "w_D=0,w_L=1,w_T=0",
								"w_D=0,w_L=0,w_T=1", "w_D=1,w_L=1,w_T=1" },
						ResultType.MEAN, "Nr. of Active Queries", "Megabytes",
						"doc/doa2011/img/testOptLargeMem.pdf", "set grid",
						"set size 0.8,0.7");
				result.createGnuplot(Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9,
						10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20),
						new String[] { prefix1 + "aggrDataRate",
								prefix2 + "aggrDataRate",
								prefix3 + "aggrDataRate",
								prefix4 + "aggrDataRate" }, new String[] {
								"w_D=1,w_L=0,w_T=0", "w_D=0,w_L=1,w_T=0",
								"w_D=0,w_L=0,w_T=1", "w_D=1,w_L=1,w_T=1" },
						ResultType.MEAN, "Nr. of Active Queries",
						"Kilobytes/Second",
						"doc/doa2011/img/testOptLargeInterAggr.pdf",
						"set grid", "set size 0.8,0.7", "set yrange [-5:*]");
				result.createGnuplot(Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9,
						10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20),
						new String[] { prefix1 + "maxAggrLoad",
								prefix2 + "maxAggrLoad",
								prefix3 + "maxAggrLoad",
								prefix4 + "maxAggrLoad",
								prefix1 + "minAggrLoad",
								prefix2 + "minAggrLoad",
								prefix3 + "minAggrLoad",
								prefix4 + "minAggrLoad" }, new String[] {
								"w_D=1,w_L=0,w_T=0", "w_D=0,w_L=1,w_T=0",
								"w_D=0,w_L=0,w_T=1", "w_D=1,w_L=1,w_T=1", "",
								"", "", "" }, ResultType.MEAN,
						"Nr. of Active Queries", "Kilobytes/Second",
						"doc/doa2011/img/testOptLargeAggrLoad.pdf", "set grid",
						"set size 0.8,0.7");
				result
						.createGnuplot(
								Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9, 10,
										11, 12, 13, 14, 15, 16, 17, 18, 19, 20),
								new String[] {
										prefix1 + "minAggrLoad:" + prefix1
												+ "minAggrLoad:" + prefix1
												+ "maxAggrLoad:" + prefix1
												+ "maxAggrLoad",
										prefix2 + "minAggrLoad:" + prefix2
												+ "minAggrLoad:" + prefix2
												+ "maxAggrLoad:" + prefix2
												+ "maxAggrLoad",
										prefix3 + "minAggrLoad:" + prefix3
												+ "minAggrLoad:" + prefix3
												+ "maxAggrLoad:" + prefix3
												+ "maxAggrLoad",
										prefix4 + "minAggrLoad:" + prefix4
												+ "minAggrLoad:" + prefix4
												+ "maxAggrLoad:" + prefix4
												+ "maxAggrLoad" },
								new String[] { "w_D=1,w_L=0,w_T=0",
										"w_D=0,w_L=1,w_T=0",
										"w_D=0,w_L=0,w_T=1",
										"w_D=1,w_L=1,w_T=1" },
								ResultType.MEAN,
								"Nr. of Active Queries",
								"Kilobytes/Second",
								"doc/doa2011/img/testOptLargeAggrLoad.pdf",
								"set grid",
								"set size 1,0.7",
								"set xtics (1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20)",
								"set xrange [0:21]", "set yrange [0:19]",
								"set style fill pattern border");
				result.createGnuplot(Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9,
						10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20),
						new String[] { prefix1 + "aggrEventFreq",
								prefix2 + "aggrEventFreq",
								prefix3 + "aggrEventFreq",
								prefix4 + "aggrEventFreq" }, new String[] {
								"w_D=1,w_L=0,w_T=0", "w_D=0,w_L=1,w_T=0",
								"w_D=0,w_L=0,w_T=1", "w_D=1,w_L=1,w_T=1" },
						ResultType.MEAN, "Nr. of Active Queries",
						"Events/Minute",
						"doc/doa2011/img/testOptLargeAggrLoadDiff.pdf",
						"set grid", "set size 0.7,0.7", "set yrange [0:2500]");
			} else {
				for (int i : Arrays.asList(0, 1, 2, 3)) {
					GenericTestResult result = GenericTestResult
							.load("etc/results/optimizationTestResults" + i
									+ ".xml");
					CytoscapeGraphUtil.createCytoscapeGraph(result,
							"doc/doa2011/img/testTopoGraph_" + i
									+ "_<time>.pdf", i);
				}
			}
			System.exit(0);
		}

		if (onlyLocal && startGatewayAndAggrs) {

			TestServiceStarter.setupDefault(5);

		} else if (deployCloud) {

			// start Cloud instances
			TestbedMain euca = new TestbedMain("etc/test/DSGCloudTest.xml");
			// euca.deployConfigTest();
			euca.startupInstances();
			System.exit(0); // TODO: remove
			// start eventing services
			System.out.println("Starting event producers..");
			EventingTestServiceStarter.startEventProducers(4);

		} else {

			// remove all existing data services from registry
			RegistryProxy.resetCache();
			System.out.println("Removing existing data services from registry");
			List<DataServiceNode> services = Registry.getRegistryProxy()
					.getDataServiceNodes(null);
			for (DataServiceNode d : new LinkedList<DataServiceNode>(services)) {
				Registry.getRegistryProxy().removeDataServiceNode(d);
			}
			RegistryProxy.resetCache();
			services = Registry.getRegistryProxy().getDataServiceNodes(null);
			if (services.size() > 0)
				throw new RuntimeException(
						"There are still some data services in the registry: "
								+ services);
			EventingTestServiceStarter.startEventProducers(2);

		}

		RegistryProxy.resetCache();
		client = new AggregationClient(Registry.getRegistryProxy().getGateway()
				.getEPR(), true);

		List<AggregatorNode> aggregators = Registry.getRegistryProxy()
				.getAggregatorNodes();

		performances.addAllAggregators(aggregators);

		String url = Configuration
				.getUrlWithVariableHost("test.eventing.consumer.bindaddress");
		Endpoint.publish(url, new EventReceiverService(false, null));

		for (DataServiceNode eventingService : Registry.getRegistryProxy()
				.getDataServiceNodes("Eventing")) {
			WebServiceClient
					.getClient(eventingService.getEPR())
					.invoke(
							new RequestInput(
									XMLUtil
											.getInstance()
											.toElement(
													"<tns:setNewInterval xmlns:tns=\""
															+ Configuration.NAMESPACE
															+ "\">"
															+ "<milliseconds>1500</milliseconds></tns:setNewInterval>"))
							.getRequest());
		}
		listener = new MonitoringSpecification(new EndpointReference(new URL(
				url + "?wsdl")));

		if (doLargeScaleTest) {
			doLargeScaleTest();
		} else {
			doCytographTests();
		}

		System.out.println("Test finished.");

		// again, terminate all existing queries
		if (doDestroyTopologies) {
			Gateway.getGatewayProxy().setOptimization(false, null);
			for (Topology t : TopologyUtil.collectAllTopologies()) {
				client.destroyTopology(t.getTopologyID());
			}
		}
		Thread.sleep(1000);
		System.exit(0);
	}

}
