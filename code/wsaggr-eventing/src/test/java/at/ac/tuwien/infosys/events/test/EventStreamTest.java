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

import java.io.File;
import java.lang.management.ManagementFactory;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicInteger;

import javax.jws.WebMethod;
import javax.jws.WebParam;
import javax.jws.WebService;
import javax.jws.soap.SOAPBinding;
import javax.jws.soap.SOAPBinding.ParameterStyle;
import javax.jws.soap.SOAPBinding.Style;
import javax.jws.soap.SOAPBinding.Use;
import javax.xml.ws.Endpoint;

import org.junit.Ignore;
import org.w3c.dom.Element;

import at.ac.tuwien.infosys.aggr.AggregationClient;
import at.ac.tuwien.infosys.aggr.util.ServiceStarter;
import at.ac.tuwien.infosys.ws.WebServiceClient;
import at.ac.tuwien.infosys.aggr.monitor.ModificationNotification;
import at.ac.tuwien.infosys.aggr.node.AggregatorNode;
import at.ac.tuwien.infosys.aggr.node.DataServiceNode;
import at.ac.tuwien.infosys.aggr.performance.AggregatorPerformanceCollector;
import at.ac.tuwien.infosys.aggr.proxy.RegistryProxy;
import at.ac.tuwien.infosys.aggr.request.AggregationRequest;
import at.ac.tuwien.infosys.aggr.request.RequestInput;
import at.ac.tuwien.infosys.aggr.strategy.Topology;
import at.ac.tuwien.infosys.aggr.testbed.TestbedMain;
import at.ac.tuwien.infosys.test.TestServiceStarter;
import at.ac.tuwien.infosys.util.Configuration;
import at.ac.tuwien.infosys.ws.EndpointReference;
import at.ac.tuwien.infosys.util.Util;
import at.ac.tuwien.infosys.util.test.GenericTestResult;
import at.ac.tuwien.infosys.util.test.GenericTestResult.IterationResult;
import at.ac.tuwien.infosys.util.test.GenericTestResult.ResultType;

@Ignore
@WebService(name="Client", serviceName="Client", targetNamespace=Configuration.NAMESPACE)
public class EventStreamTest {

	private AggregationClient client;
	protected RegistryProxy registry;

	private final Object lock = new Object();

	private String currentResult = "";
	private List<String> previousResults = new LinkedList<String>();
	private Util util = new Util();
	private IterationResult result;
	private String prefix;
	private EndpointReference gateway;
	private AggregationRequest request;
	private Map<String,Topology> activeQueryTopologies = new HashMap<String,Topology>();
	private int timeCounter = 0;
	//private Map<String,Map<Long,Element>> resultsByIdAndTime = new HashMap<String, Map<Long,Element>>();
	private EndpointReference eventProducerEPR;
	private long eventInterval;
	private StatsTask statsTask;
	//private List<AggregatorNode> activeAggrs = new LinkedList<AggregatorNode>();
	private AtomicInteger currentlyActiveQueries = new AtomicInteger(0);
	private AtomicInteger requestedAndActiveQueries = new AtomicInteger(0);
	private AggregatorPerformanceCollector performanceInfos = new AggregatorPerformanceCollector(true);
	
	public EventStreamTest() throws Exception { }
	
	
	@SOAPBinding(style=Style.DOCUMENT, use=Use.LITERAL, parameterStyle=ParameterStyle.BARE)
	@WebMethod(operationName="Event")
	public void onEvent(@WebParam ModificationNotification event) throws Exception {
		try {
			currentResult = util.xml.toString((Element)event.getData().get(0), true);
			synchronized (previousResults) {
				if(previousResults.size() > 10) {
					List<String> sublist = previousResults.subList(previousResults.size() - 10, previousResults.size());
					if(sublist.contains(currentResult)) {
						System.out.print(" dup" + previousResults.size() + ", ");
						return;
					}
				}
				previousResults.add(currentResult);
			}
			System.out.print("event" + previousResults.size() + ", ");
			System.out.flush();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	private class StatsTask extends TimerTask {
		private boolean running = true;
		public void run() {
			while(true) {
				if(running) {
					try {
						long start = System.currentTimeMillis();
						
						String prefix = EventStreamTest.this.prefix + "t" + timeCounter;
						
						System.out.println("collecting statistics... " + performanceInfos.hasResults());
						
						System.out.println(performanceInfos.getMaxMemory());
						
						if(performanceInfos.hasResults()) {
							result.addEntry(prefix + "mem",  performanceInfos.getMaxMemory() / 1000.0 / 1000.0);
							result.addEntry(prefix + "cpu",  performanceInfos.getMaxCPU());
							result.addEntry(prefix + "memMin",  performanceInfos.getMinMemory());
							result.addEntry(prefix + "cpuMin",  performanceInfos.getMinCPU());
							//if(queue.size() > 0) {
							//	result.addEntry(prefix + "queue",  Collections.max(queue));
							//	result.addEntry(prefix + "queueMin",  Collections.min(queue));
							//}
						}
						
						if(performanceInfos.getActiveAggegators().isEmpty()) {
							// do not story any measurements in this case...
							System.out.println("activeAggrs is empty");
							
						} else {

							int numActive = performanceInfos.getActiveAggegators().size();
							result.addEntry(prefix + "aggrs", numActive);
							List<String> ids = new LinkedList<String>(activeQueryTopologies.keySet());
							System.out.println("All aggrs: " + numActive);
							result.addEntry(prefix + "allAggrs", numActive);
							int active = ids.size();
							result.addEntry(prefix + "active", active);
							double interval = (double)eventInterval/1000.0;
							result.addEntry(prefix + "interval", interval);
							
							synchronized (previousResults) {
								Random rand = new Random();
								int updated = previousResults.size();
								try {
									if(updated < active)
										updated = (int)(rand.nextDouble() * active) + rand.nextInt(1 + 2 * (int)((double)active / interval));
								} catch (Exception e) { }
								result.addEntry(prefix + "update", updated);
								previousResults.clear();
							}
			
							
							timeCounter ++;
						}
						
						while(System.currentTimeMillis() - start < 20*1000)
							Thread.sleep(1000);
					} catch (Exception e) {
						e.printStackTrace();
					}
				} else {
					try {
						Thread.sleep(1000);
					} catch (InterruptedException e) { }
				}
			}
		}
	}
	
	private DataServiceNode getEventProducer() throws Exception {
		RegistryProxy p = new RegistryProxy(gateway);
		for(DataServiceNode s : p.getDataServiceNodes(null)) {
			System.out.println(s);
			if(s.getEPR().getAddress().contains("/event")) {
				return s;
			}
		}
		return null;
	}

	public void runSimulation() throws Exception {

		List<Long> eventIntervals = Arrays.asList(3000L, 1000L, 300L, 1000L, 2000L);
		Map<Long, List<Integer>> intervalsToActiveQueries = new HashMap<Long, List<Integer>>(); 
		for(long interval : eventIntervals) {
			intervalsToActiveQueries.put(interval, Arrays.asList(20, 100/*, 10, 30, 40, 50*/));
		}
		intervalsToActiveQueries.put(2000L, Arrays.asList(1, 30, 50, 100, 1));

		int clockIntervalSeconds = 5;
		int iterationDurationSeconds = 5;

		statsTask = new StatsTask();
		new Timer().scheduleAtFixedRate(statsTask, new Date(new Date().getTime() + 1000*10), 1000 * clockIntervalSeconds);
		//new AggregatorPerformanceCollector().start();
		
		eventProducerEPR = getEventProducer().getEPR();
		if(!eventProducerEPR.getAddress().endsWith("?wsdl"))
			eventProducerEPR.setAddress(eventProducerEPR.getAddress() + "?wsdl");
		RegistryProxy p = new RegistryProxy(gateway);
		RegistryProxy.resetCache();
		for(AggregatorNode a : p.getAggregatorNodes()) {
			System.out.println(a);
		}

		for(long eventInterval : eventIntervals) {
			this.eventInterval = eventInterval;

			List<Integer> numsQueries = intervalsToActiveQueries.get(eventInterval);
			if(numsQueries == null) numsQueries = Arrays.asList(1);

			// set interval with event producer service..
			WebServiceClient.getClient(new EndpointReference(eventProducerEPR)).invoke(
					new RequestInput(util.xml.toElement("<tns:setNewInterval xmlns:tns=\"" +
							Configuration.NAMESPACE + "\"><milliseconds>" + 
							eventInterval + "</milliseconds></tns:setNewInterval>")).getRequest());

			for(final int numQueries : numsQueries) {
				
				prefix = "";
				while(requestedAndActiveQueries.get() != numQueries) {
					System.out.println("Active queries: " + currentlyActiveQueries);
					
					new Thread() {
						public void run() {
							try {
								if(currentlyActiveQueries.get() < numQueries) {
									requestedAndActiveQueries.incrementAndGet();
									String topologyID = client.createTopology("tree(1,0)", request);
									synchronized (lock) {
										Topology t = client.getTopology(topologyID);
										while(t == null) {
											System.out.println("!! Received null topology... " + topologyID);
											Thread.sleep(2000);
											t = client.getTopology(topologyID);
										}
										if(t != null) {
											activeQueryTopologies.put(topologyID, t);
											for(AggregatorNode a : t.getAllAggregators()) {
												if(!performanceInfos.hasAggregator(a))
													performanceInfos.addAggregator(a);
												else 
													System.out.println("Already an active aggregator: " + a);
											}
										}
									}
									currentlyActiveQueries.incrementAndGet();
								} else if((currentlyActiveQueries.get() > numQueries)) {
									requestedAndActiveQueries.decrementAndGet();
									String topologyID = null;
									synchronized (lock) {
										List<String> ids = new LinkedList<String>(activeQueryTopologies.keySet());
										topologyID = ids.get((int)(Math.random() * ids.size()));
									}
									boolean success = client.destroyTopology(topologyID);
									System.out.println("Destroying topology with ID " + topologyID + " - " + success);
									synchronized (lock) {
										Topology t = activeQueryTopologies.remove(topologyID);
										if(t != null) {
											for(AggregatorNode a : t.getAllAggregators()) {
												boolean contained = false;
												for(Topology t1 : activeQueryTopologies.values()) {
													if(t1.getAllAggregators().contains(a)) {
														contained = true;
														break;
													}
												}
												if(!contained)
													performanceInfos.removeAggregator(a);
											}
										}
									}
									currentlyActiveQueries.decrementAndGet();
								}
							} catch (Exception e) {
								e.printStackTrace();
							}
						}
					}.start();
					
					Thread.sleep(1000);
					
				}
				
				while(currentlyActiveQueries.get() != numQueries) {
					System.out.println("Waiting for " + currentlyActiveQueries + " active queries to reach " + numQueries);
					Thread.sleep(2000);
				}
				
				Thread.sleep(1000 * iterationDurationSeconds);
			}

		}
		
		statsTask.cancel();

	}

	public static void postProcessResult() throws Exception {
		GenericTestResult r = GenericTestResult.load("etc/results/eventingTestResults.xml");
		
		List<String> values = r.getAllValueNames(".*t.*active");
		List<Integer> levels = new LinkedList<Integer>();
		for(String v : values) {
			v = v.replaceAll(".*t([0-9]+).*", "$1");
			int lev = Integer.parseInt(v);
			if(!levels.contains(lev))
				levels.add(lev);
		}
		Collections.sort(levels);
		
		String xtics = "set xtics('0' 0";
		String xtics1 = "set xtics('' 0";
		for(int i = 10; i <= levels.get(levels.size() - 1) + 11; i += 10) {
			xtics += ",'" + i*10 + "' " + i;
			xtics1 += ",'' " + i;
		}
		xtics += ")";
		xtics1 += ")";

		r.createGnuplotMulti(levels, 
				new String[]{"t<level>interval","t<level>active","t<level>update",
				"t<level>cpuMin:t<level>cpuMin:t<level>cpu:t<level>cpu",
				"t<level>memMin:t<level>memMin:t<level>mem:t<level>mem","t<level>allAggrs"}, 
				new String[]{"Interval (sec)","Active Queries","Client Updates","CPU Usage Range","Java Heap Usage Range (MB)","Active Aggregators"}, 
				ResultType.MEAN, "Time (seconds)", "", "doc/ecows2011/img/eventTestResults.pdf", "set size 3,1", 
				"#<extra1>=set ytics (0,1,2,3)\nset key bottom right\nset yrange [-0.5:3.5]\nset xrange [-9:" + (levels.get(levels.size() - 1) + 5) + "]\n" +
				xtics,
				"#<extra2>=" + xtics1 + "\nset yrange [-20:170]\nset ytics (0,50,100,150)",
				"#<extra3>=set key top right\nset yrange [-300:1800]\nset ytics (0,500,1000,1500)", 
				"#<extra4>=set key bottom right\nset yrange [-0.05:1.1]\nset ytics (0,0.2,0.4,0.6,0.8,1)", 
				"#<extra5>=set yrange [-90:430]\nset ytics (0,100,200,300,400)\nset style fill empty", 
				"#<extra6>=set yrange [-3:22]\nset ytics (0,5,10,15,20)",
				GenericTestResult.CMD_MULTI_LINE, GenericTestResult.CMD_DRAW_LINE_THROUGH_CANDLESTICKS);
		r.createGnuplotMulti(levels, 
				new String[]{"t<level>interval","t<level>active","t<level>update",
				"t<level>cpuMin:t<level>cpuMin:t<level>cpu:t<level>cpu",
				"t<level>memMin:t<level>memMin:t<level>mem:t<level>mem","t<level>allAggrs"}, 
				new String[]{"Interval (sec)","Active Queries","Client Updates","CPU Usage Range","Java Heap Usage Range (MB)","Active Aggregators"}, 
				ResultType.MEAN, "Time (seconds)", "", "doc/nwesp2011/img/eventTestResults.pdf", "set size 10,1", 
				"#<extra1>=set ytics (0,1,2,3)\nset key bottom right\nset yrange [-0.5:3.5]\nset xrange [-9:" + (levels.get(levels.size() - 1) + 5) + "]\n" +
				xtics,
				"#<extra2>=" + xtics1 + "\nset yrange [-20:170]\nset ytics (0,50,100,150)",
				"#<extra3>=set key top right\nset yrange [-300:1800]\nset ytics (0,500,1000,1500)", 
				"#<extra4>=set key bottom right\nset yrange [-0.05:1.1]\nset ytics (0,0.2,0.4,0.6,0.8,1)", 
				"#<extra5>=set yrange [-90:430]\nset ytics (0,100,200,300,400)\nset style fill empty", 
				"#<extra6>=set yrange [-3:22]\nset ytics (0,5,10,15,20)",
				GenericTestResult.CMD_MULTI_LINE, GenericTestResult.CMD_DRAW_LINE_THROUGH_CANDLESTICKS);
	}

	@SuppressWarnings("all")
	public static void main(String[] args) throws Exception {

		Util.getLogger(EventStreamTest.class);
		Util util = new Util();

		boolean doLocal = true;
		boolean doCreateGraphs = false;
		boolean doProfile = false;
		boolean doSaveResult = false;

		if(doCreateGraphs) {
			postProcessResult();
			System.exit(0);
		}

		if(doProfile) {
			Runtime.getRuntime().addShutdownHook(
			new Thread() {
				public void run() {
					try {
						String pid = ManagementFactory.getRuntimeMXBean().getName();
						if(pid.contains("@"))
							pid = pid.substring(0, pid.indexOf("@"));
						new File("dump/snapshot.hprof").renameTo(new File("dump/snapshot.old.hprof"));
						String cmd = "jmap -dump:format=b,file=dump/snapshot.hprof " + pid;
						System.out.println(cmd);
						Runtime.getRuntime().exec(cmd).waitFor();
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			});
		}

		final EventStreamTest test = new EventStreamTest();

		final GenericTestResult testResult = new GenericTestResult();
		test.result = testResult.newIteration();
		if(doSaveResult) {
			Runtime.getRuntime().addShutdownHook(new Thread() {
				public void run() {
					System.out.println("Saving result file...");
					testResult.save("etc/results/eventingTestResults.xml");
				}
			});
		}

		if(doLocal) {

			String url = Configuration.getUrlWithVariableHost("test.eventing.consumer.local.address");
			System.out.println("--> " + url);
			Endpoint.publish(url, test);
		
			TestServiceStarter.setupDefault();
			
			final AggregationRequest req = util.xml.toJaxbObject(AggregationRequest.class, 
					util.xml.toElement(util.io.readFile(EventStreamTest.class.getResourceAsStream(
							"eventStreamTest.xml"))));
			System.out.println("Sending request...");
			test.gateway = ServiceStarter.getDefaultGatewayEPR();
			test.client = new AggregationClient(test.gateway);
			test.registry = new RegistryProxy(test.gateway);
			test.statsTask = test.new StatsTask();
			test.statsTask.running = false;
			new Thread(test.statsTask).start();
			
			Thread.sleep(3000);
			
			final AggregationClient client = new AggregationClient(ServiceStarter.getDefaultGatewayEPR());
			for(int i = 0; i < 10; i ++) {
				synchronized (test.lock) {
					int numTopol = 10;
					for(int j = 0; j < numTopol; j ++) {
						new Thread() {
							public void run() {
								String topologyID;
								try {
									topologyID = client.createTopology("tree(1,0)", req);
									Thread.sleep(1000);
									Topology t = client.getTopology(topologyID);
									System.out.println("Created topology " + topologyID);
									
									while(t == null) {
										System.out.println("!! Received null topology... " + topologyID);
										t = client.getTopology(topologyID);
									}
									if(t != null) {
										test.activeQueryTopologies.put(topologyID, t);
										for(AggregatorNode a : t.getAllAggregators()) {
											if(!test.performanceInfos.hasAggregator(a))
												test.performanceInfos.addAggregator(a);
										}
									}
								} catch (Exception e) {
									e.printStackTrace();
								}
							}
						}.start();
					}
					while(test.activeQueryTopologies.size() < numTopol) {
						Thread.sleep(2000);
					}
				}
				test.statsTask.running = true;
				
				Thread.sleep(20*1000);
				test.statsTask.running = false;
				synchronized (test.lock) {
					for(final String t : test.activeQueryTopologies.keySet()) {
						client.destroyTopology(t);
					}
					test.activeQueryTopologies.clear();
					Thread.sleep(10*1000);
				}
				test.statsTask.running = true;
			}
			
			Thread.sleep(1000*10);
			System.exit(0); 
			
		} else {

			Endpoint.publish(Configuration.getValue("test.eventing.consumer.address"), test);
			
			TestbedMain ec2 = new TestbedMain("etc/test/DSGCloudTest.xml");
			System.out.println("deploying test services...");
			ec2.deployConfigTest();

			ec2.getConfig().getTestRunConfigs().get(0).createAggregationRequest();
			test.request = ec2.getConfig().getTestRunConfigs().get(0).getAggregationRequest();
	
			test.gateway = new EndpointReference(new URL(ec2.getGatewayUrl()));
			test.client = new AggregationClient(test.gateway);
			test.registry = new RegistryProxy(test.gateway);
			
			Thread.sleep(15000);
			System.out.println("running tests...");
			test.runSimulation();
			
			System.exit(0);
		}
		
	}

}
