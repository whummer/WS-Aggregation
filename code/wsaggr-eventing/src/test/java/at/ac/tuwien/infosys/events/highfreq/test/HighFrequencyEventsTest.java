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

package at.ac.tuwien.infosys.events.highfreq.test;

import java.net.URL;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import javax.xml.ws.Endpoint;

import org.w3c.dom.Element;

import at.ac.tuwien.infosys.aggr.AggregationClient;
import at.ac.tuwien.infosys.aggr.util.ServiceStarter;
import at.ac.tuwien.infosys.aggr.events.query.EventQuerier;
import at.ac.tuwien.infosys.aggr.events.query.EventQuerier.EventQueryListener;
import at.ac.tuwien.infosys.aggr.events.query.EventStream;
import at.ac.tuwien.infosys.aggr.monitor.MonitoringSpecification;
import at.ac.tuwien.infosys.aggr.node.DataServiceNode;
import at.ac.tuwien.infosys.aggr.node.Registry;
import at.ac.tuwien.infosys.aggr.request.AggregationRequest;
import at.ac.tuwien.infosys.aggr.request.EventingInput;
import at.ac.tuwien.infosys.aggr.request.NonConstantInput;
import at.ac.tuwien.infosys.events.EventReceiverService;
import at.ac.tuwien.infosys.events.query.EventQuerierMXQuery;
import at.ac.tuwien.infosys.events.test.EventingTestServiceStarter;
import at.ac.tuwien.infosys.util.Util;
import at.ac.tuwien.infosys.util.par.Parallelization;
import at.ac.tuwien.infosys.util.test.GenericTestResult;
import at.ac.tuwien.infosys.util.test.GenericTestResult.ResultType;
import at.ac.tuwien.infosys.ws.EndpointReference;

@org.junit.Ignore
public class HighFrequencyEventsTest implements EventQueryListener {

	private Util util = new Util();
	private AtomicInteger eventCount = new AtomicInteger();
	private AtomicInteger resultCount = new AtomicInteger();
	private final int maxBufferSize = 6000;
	private final int eventIntervalMS = 30;
	private final EventQuerier querier = new EventQuerierMXQuery();
	private EventStream stream = null;

	public void integrationTest() throws Exception {
		
		ServiceStarter.setupDefault(3);
		EventingTestServiceStarter.startEventProducers(1, 10);
		String eventReceiver = "http://localhost:9887/events";
		EventReceiverService service = new EventReceiverService(false);
		Endpoint.publish(eventReceiver, service);
		List<DataServiceNode> services = Registry.getRegistryProxy().getDataServiceNodes("Eventing");

		AggregationRequest r = new AggregationRequest();
		NonConstantInput in = new EventingInput();
		in.setExternalID("1");
		in.setServiceURL(services.get(0).getEPR().getAddress());
		r.getInputs().addInput(in);
		r.getQueries().addPreparationQuery(in.getExternalID(),
				"for tumbling window $w in $input "
						+ "start at $spos when true() "
						+ "end at $epos when ($epos - $spos) gt 100 "
						+ "return <ticks><a>{$w}</a></ticks>");
		MonitoringSpecification listener = new MonitoringSpecification(
				new EndpointReference(new URL(eventReceiver + "?wsdl")));
		r.setMonitor(listener);

		AggregationClient client = new AggregationClient(
				Registry.getRegistryProxy().getGateway().getEPR(),
				"wsaggr", "wsaggr!");
		Element e = client.aggregate(r);
		util.xml.print(e);
	}
	
	public void test() throws Exception {
		String query = util.xml.toElement(getClass().getResourceAsStream("windowQuery.xml")).getTextContent();
		query = query.trim().replace("\t", " ").replaceAll("  ", " ");
		System.out.println(query);

		stream = querier.newStream(maxBufferSize);
		querier.initQuery(null, query, stream);
		querier.addListener(this);

		Runnable job = new Runnable() {
			public void run() {
				for(int i = 0; i < 10000; i ++) {
					int value = (int)(Math.random() * 10.0);
					value = 1;
					try {
						synchronized (querier) {
							querier.addEvent(stream, util.xml.toElement("<value>" + value + "</value>"));
						}
						eventCount.incrementAndGet();
						Thread.sleep(eventIntervalMS);
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}
		};
		
		long t1 = System.currentTimeMillis();
		Parallelization.runMultiple(job, 10, true);
		long t2 = System.currentTimeMillis();
		double diff = t2 - t1;
		System.out.println("Processed " + eventCount + " events in " + 
				(diff/1000.0) + " seconds, " + 
				((double)eventCount.get()/(diff/1000)) + " events/second");
		System.exit(0);
	}

	@Override
	public void onResult(EventQuerier store, Element newResult) {
		resultCount.incrementAndGet();
		System.out.println("result " + resultCount.get() + " from window query: " + util.xml.toString(newResult));
		System.out.println("buffered, not yet processed input events: " + querier.getNotYetProcessedInputEvents(stream));
		System.out.println("number of events in internal window buffers: " + querier.getNumberOfBufferedEvents(stream));
	}

	public static void main(String[] args) throws Exception {
//		new HighFrequencyEventsTest().test();

		boolean createGraphs = true;
		if(createGraphs) {
			GenericTestResult r = GenericTestResult.load("0_testAgg.result");
			   r.createGnuplot(
					   r.getAllLevelIDsByPattern("cpuload_transformationStateless.xml_200_10_5_(.*)_none", 1), 
			     new String[]{"cpuload_transformationStateless.xml_200_10_5_<level>_none"}, 
			     new String[]{"CPU Load"}, ResultType.MEAN, "Time", "CPU Load", "testResult1.pdf",
			     "set yrange [0:1]");
		} else {
			new HighFrequencyEventsTest().integrationTest();
		}
	}
}
