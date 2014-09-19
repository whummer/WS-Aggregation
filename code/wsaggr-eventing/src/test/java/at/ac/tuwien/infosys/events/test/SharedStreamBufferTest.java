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

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.rmi.registry.LocateRegistry;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import javax.management.MBeanServer;
import javax.management.MBeanServerConnection;
import javax.management.MBeanServerFactory;
import javax.management.MBeanServerInvocationHandler;
import javax.management.MXBean;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXConnectorServer;
import javax.management.remote.JMXConnectorServerFactory;
import javax.management.remote.JMXServiceURL;

import org.junit.Ignore;
import org.w3c.dom.Element;

import io.hummer.util.Util;
import io.hummer.util.test.GenericTestResult;
import io.hummer.util.test.GenericTestResult.IterationResult;
import io.hummer.util.test.GenericTestResult.ResultType;
import at.ac.tuwien.infosys.aggr.events.query.EventStream;
import at.ac.tuwien.infosys.aggr.events.query.EventQuerier;
import at.ac.tuwien.infosys.aggr.events.query.EventQuerier.EventQueryListener;
import at.ac.tuwien.infosys.events.query.EventQuerierMXQuery;

@Ignore
public class SharedStreamBufferTest {

	//private static Util util = new Util();
	private static boolean useSingleEventStream = false;
	private static int testNum = 0;
	private static boolean testRunning = false;
	private static IterationResult result;
	private static int timeCounter;
	private static AtomicInteger numEvents = new AtomicInteger();
	private static long lastEventTime = 0;
	private static int numQueriers = 0;
	
	private static EventQuerierMXQuery createQuerier(String query, EventStream stream) throws Exception {
		EventQuerierMXQuery q = new EventQuerierMXQuery();
		if(stream == null)
			stream = q.newStream();
		q.initQuery(null, query, stream);
		q.addListener(new EventQueryListener() {
			public void onResult(EventQuerier store, Element newResult) {
				numEvents.getAndIncrement();
				//util.print(newResult);
				//System.out.println(util.toString(newResult).hashCode());
				lastEventTime = System.currentTimeMillis();
			}
		});
		return q;
	}

	@MXBean
	public static interface InfoMXBean {
		int getNumEvents();
	}
	public static class InfoMXBeanImpl implements InfoMXBean {
		public int getNumEvents() {
			return numEvents.get();
		}
	}

	private static void runTest() throws Exception {

		JMXServiceURL address = new JMXServiceURL("rmi","localhost",1099);
		address = new JMXServiceURL("service:jmx:rmi:///jndi/rmi://localhost:1099/jmxrmi" + testNum + "_" + numQueriers);
		MBeanServer server = MBeanServerFactory.createMBeanServer();
		server.registerMBean(ManagementFactory.getMemoryMXBean(), new ObjectName(ManagementFactory.MEMORY_MXBEAN_NAME));
		server.registerMBean(ManagementFactory.getThreadMXBean(), new ObjectName(ManagementFactory.THREAD_MXBEAN_NAME));
		server.registerMBean(ManagementFactory.getOperatingSystemMXBean(), new ObjectName(ManagementFactory.OPERATING_SYSTEM_MXBEAN_NAME));
		server.registerMBean(new InfoMXBeanImpl(), new ObjectName("wsaggr:type=InfoBean"));
		JMXConnectorServer cntorServer = JMXConnectorServerFactory.newJMXConnectorServer(address, null, server);
		cntorServer.start();

		final Util util = new Util();
		final String query1 = "declare namespace x = \"wsaggr\";" +
					"declare variable $input external; " +
					"for sliding window $w in $input/stock " +
						"start at $s when true() end at $e when ($e - $s) ge 2 " +
					"return <foo11>{$w}</foo11>";
		//query = util.readFile(EventStreamTest.class.getResourceAsStream("windowQueryTest.txt"));

		final List<EventStream> streams = new LinkedList<EventStream>();

		final EventQuerierMXQuery querier = new EventQuerierMXQuery();

		EventStream singleEventStream = null;
		for(int i = 0; i < numQueriers; i ++) {
			if(useSingleEventStream && singleEventStream == null) {
				singleEventStream = querier.newStream();
			}
			EventStream s = useSingleEventStream ? singleEventStream : querier.newStream();
			synchronized (streams) {
				if(!streams.contains(s))
					streams.add(s);						
			}
			EventQuerierMXQuery q = createQuerier(query1, s);
			q.toString();
		}
		Thread.sleep(2000);
		
//		new Thread() {
//			public void run() {
//				try {
//					
//				} catch (Exception e) {
//					e.printStackTrace();
//				}
//			}
//		}.start();
		
		ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
		
		//Random r = new Random();
		long t1 = System.currentTimeMillis();
		long before = t1;
		for(int i = 0; i < 3000; i ++) {
			String event = "<stock symbol=\"" + (i % 3) + "\">" + (i % 100) + "</stock>";
			synchronized (streams) {
				for(EventStream s : streams) {
					querier.addEvent(s, util.xml.toElement(event));
				}
			}
			//Thread.sleep(1);
			if(i > 0 && i % 1000 == 0) {
				System.out.println((Runtime.getRuntime().totalMemory() - 
							Runtime.getRuntime().freeMemory()) + 
						" - " + Runtime.getRuntime().freeMemory() + 
						" - " + Runtime.getRuntime().totalMemory() + 
						" - " + Runtime.getRuntime().maxMemory());
				long t2 = System.currentTimeMillis();
				System.out.println("Took " + ((t2 - t1)/1000) + " seconds (" + i + " events)");
				t1 = System.currentTimeMillis();
			}
			Thread.sleep(10);
		}
		long after = System.currentTimeMillis();
		System.out.println("test finished - " + ((after - before)/1000) + " seconds in total");
		//q.close();
		
		new Thread() {
			public void run() {
				try {
					while(true) {
						Thread.sleep(200);
						if(lastEventTime > 0 && (System.currentTimeMillis() - lastEventTime) > 500) {
							System.out.println("Total number of results received: " + numEvents.get());
							System.exit(0);
						}
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}.start();
		
		//System.exit(0);
		
	}

	public static void main(String[] args) throws Exception {
		
		if(true) {
			GenericTestResult r = GenericTestResult.load("../../etc/results/sharedStreamTestResults.xml");
			List<String> levels1 = r.getAllLevelIDsByPattern("t0q20t([0-9]+).*", 1);
			List<String> levels2 = r.getAllLevelIDsByPattern("t0q10t([0-9]+).*", 1);
			System.out.println(levels1);
			System.out.println(levels2);
			//r.createGnuplot(levels, new String[]{"t0t<level>mem", "t0t<level>cpu", "t0t<level>num"}, 
			//		new String[]{"Memory (KB)", "CPU Time (ms)", "Processed Events"}, 
			//		ResultType.MEAN, "Time", "Value", "doc/ecows2011/img/streamTest.pdf", "set key bottom right");
			//r.createGnuplot(levels, new String[]{"t1q10t<level>mem", "t0q10t<level>mem", "t1q20t<level>mem", "t0q20t<level>mem"}, 
			//		new String[]{"10 Queries Shared", "10 Queries Non-Shared", "20 Queries Shared", "20 Queries Non-Shared"}, 
			//		ResultType.MEAN, "Time", "Memory (MB)", "doc/ecows2011/img/streamTestMem.pdf", "set key bottom right");
			//r.createGnuplot(levels, new String[]{"t1q10t<level>cpu","t0q10t<level>cpu","t1q20t<level>cpu","t0q20t<level>cpu"}, 
			//		new String[]{"20 Queries Shared", "20 Queries Non-Shared"}, 
			//		ResultType.MEAN, "Time", "CPU", "doc/ecows2011/img/streamTestCpu.pdf");
			
			/* two figures in one graph */
			r.createGnuplot(levels1, new String[]{"t1q10t<level>num", "t0q10t<level>num", "t1q20t<level>num", "t0q20t<level>num"}, 
					new String[]{"10 Queries, Shared", "10 Queries, Non-Shared", "20 Queries, Shared", "20 Queries, Non-Shared"}, 
					ResultType.MEAN, "Time (seconds)", "Window Query Results", "../../papers/ecows2011/img/streamTestEvents.pdf", 
					"set key top right", "set grid", "set size 1,0.75", GenericTestResult.CMD_FLIP_AXES);
			
			/* two figures split up into two graphs */
			//String values = r.getPlottableAverages(levels1, new String[]{"t1q20t<level>num", "t0q20t<level>num"}, ResultType.MEAN);
			//System.out.println(values);
			r.createGnuplot(levels1, new String[]{"t1q20t<level>num", "t0q20t<level>num"}, 
					new String[]{"20 Queries, Shared", "20 Queries, Non-Shared"}, 
					ResultType.MEAN, "", "Window Query Results", "../../papers/ijcisim2012/img/sharedBufferTest20.pdf", 
					"set key top right", "set grid", "set size 0.75,0.85", GenericTestResult.CMD_FLIP_AXES);
			r.createGnuplot(levels2, new String[]{"t1q10t<level>num", "t0q10t<level>num"}, 
					new String[]{"10 Queries, Shared", "10 Queries, Non-Shared"}, 
					ResultType.MEAN, "Time (seconds)", "Window Query Results", "../../papers/ijcisim2012/img/sharedBufferTest10.pdf", 
					"set key top right", "set grid", "set size 0.8,0.85", GenericTestResult.CMD_FLIP_AXES);
			
			System.exit(0);
		}

//		args = new String[]{"1"};
//		try {
//			LocateRegistry.createRegistry(1099);
//		} catch (Exception e) { }

		if(args.length > 0) {
			if(args[0].equals("1")) {
				useSingleEventStream = true;
				testNum = 1;
			} else if(args[0].equals("0")) {
				useSingleEventStream = false;
				testNum = 0;
			}
			numQueriers = Integer.parseInt(args[1]);
			runTest();
			return;
		}

		try {
			LocateRegistry.createRegistry(1099);
		} catch (Exception e) { }


		new Thread() {
			public void run() {
				try {
					Thread.sleep(3000);
					long previousTimestamp = 0;
					long previousCpuTime = 0;
					List<Double> previousCpuFractions = new LinkedList<Double>();
					List<Long> previousMemUsages = new LinkedList<Long>();
					Integer currentRmiTestNum = null;
					MBeanServerConnection mbsc = null;
					while(true) {
						try {
							if(testRunning) {
								if(currentRmiTestNum == null || currentRmiTestNum != testNum) {
									JMXServiceURL address = new JMXServiceURL("service:jmx:rmi:///jndi/rmi://localhost:1099/jmxrmi" + testNum + "_" + numQueriers);
									JMXConnector cntor = JMXConnectorFactory.connect(address, null);
									mbsc = cntor.getMBeanServerConnection();
									previousCpuFractions.clear();
									previousMemUsages.clear();
									previousTimestamp = 0;
									previousCpuTime = 0;
									currentRmiTestNum = testNum;
								}
								
								String prefix = "t" + testNum + "q" + numQueriers + "t" + (timeCounter++);
								
								MemoryMXBean memBean  =
						               ManagementFactory.newPlatformMXBeanProxy(  
						                        mbsc, ManagementFactory.MEMORY_MXBEAN_NAME,
						                        MemoryMXBean.class);
								OperatingSystemMXBean osBean = ManagementFactory.newPlatformMXBeanProxy(
							            mbsc, ManagementFactory.OPERATING_SYSTEM_MXBEAN_NAME, OperatingSystemMXBean.class);
								InfoMXBean info = (InfoMXBean)MBeanServerInvocationHandler.newProxyInstance(
						            mbsc, new ObjectName("wsaggr:type=InfoBean"), InfoMXBean.class, false);

								/* access to "getProcessCpuTime" is restricted, hence use reflection here.. */
								long cpu = (Long)osBean.getClass().getMethod("getProcessCpuTime").invoke(osBean);
								long nanoTime = System.nanoTime();
								long used = memBean.getHeapMemoryUsage().getUsed();
								if(previousCpuTime > 0) {
									System.out.println(info.getNumEvents());
									double sum = 0;
									for(long l : previousMemUsages)
										sum += l;
									result.addEntry(prefix + "mem", (sum)/previousMemUsages.size()/1000000);
									sum = 0;
									for(double l : previousCpuFractions)
										sum += l;
									result.addEntry(prefix + "cpu", (double)(sum)/(double)previousCpuFractions.size());
									result.addEntry(prefix + "num", info.getNumEvents());
								}
								previousMemUsages.add(used);
								if(previousMemUsages.size() > 1)
									previousMemUsages.remove(0);
								double cpuFrac = (double)(cpu - previousCpuTime) / (double)(nanoTime - previousTimestamp) / (double)osBean.getAvailableProcessors();
								if(cpuFrac > 0 && cpuFrac <= 1.0) {
									previousCpuFractions.add(cpuFrac);
								}
								if(previousCpuFractions.size() > 2)
									previousCpuFractions.remove(0);
								previousCpuTime = cpu;
								previousTimestamp = nanoTime;
							}
						} catch (java.rmi.ConnectException e) {
							if(testRunning)
								e.printStackTrace();
						} catch (Exception e) {
							e.printStackTrace();
						}
						Thread.sleep(2000);
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}.start();

		final List<Process> processes = new LinkedList<Process>();
		final GenericTestResult test = new GenericTestResult();
		Runtime.getRuntime().addShutdownHook(new Thread() {
			public void run() {
				test.save("etc/results/sharedStreamTestResults.xml");
				for(Process p : processes) {
					p.destroy();
				}
			}
		});
		result = test.newIteration();
		
		testRunning = true;
		for(int queriers : Arrays.asList(10, 20)) {
			for(boolean single : Arrays.asList(true, false)) {
				timeCounter = 0;
				useSingleEventStream = single;
				numQueriers = queriers;
				testNum = single ? 1 : 0;
				Process p = Runtime.getRuntime().exec("./run.sh " + SharedStreamBufferTest.class.getName() + " " +  testNum + " " + numQueriers);
				processes.add(p);
				int code = p.waitFor();
				System.out.println("code: " + code);
			}
		}
		
//		useSingleEventStream = true;
//		testRunning = true;
//		testNum = 1;
//		System.out.println("./run.sh " + SharedStreamBufferTest.class.getName() + " 1");
//		Process p1 = Runtime.getRuntime().exec("./run.sh " + SharedStreamBufferTest.class.getName() + " 1 " + numQueriers);
//		processes.add(p1);
//		int code = p1.waitFor();
//		System.out.println("code: " + code);
		//processes.remove(p1);
		//testRunning = false;
		
		
		//processes.remove(p2);
		
		System.exit(0);
	}
	
}
