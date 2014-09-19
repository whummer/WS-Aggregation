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
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import javax.xml.ws.Endpoint;

import org.junit.Ignore;

import at.ac.tuwien.infosys.aggr.AggregationClient;
import at.ac.tuwien.infosys.aggr.monitor.MonitoringSpecification;
import at.ac.tuwien.infosys.aggr.node.AggregatorNode;
import at.ac.tuwien.infosys.aggr.node.DataServiceNode;
import at.ac.tuwien.infosys.aggr.node.Registry;
import at.ac.tuwien.infosys.aggr.proxy.AggregatorNodeProxy;
import at.ac.tuwien.infosys.aggr.request.AbstractInput;
import at.ac.tuwien.infosys.aggr.request.AggregationRequest;
import at.ac.tuwien.infosys.aggr.request.EventingInput;
import at.ac.tuwien.infosys.aggr.request.InputTargetExtractor;
import at.ac.tuwien.infosys.aggr.request.RequestInput;
import at.ac.tuwien.infosys.aggr.request.WAQLQuery;
import at.ac.tuwien.infosys.aggr.strategy.Topology;
import at.ac.tuwien.infosys.aggr.strategy.TopologyUtil;
import at.ac.tuwien.infosys.aggr.util.ServiceStarter;
import at.ac.tuwien.infosys.events.EventReceiverService;
import at.ac.tuwien.infosys.test.TestServiceStarter;

@Ignore
public class EventStreamMigrationTest {

	private static AggregationRequest getRequest() throws Exception {
		List<DataServiceNode> dataServices = Registry.getRegistryProxy().getDataServiceNodes("Eventing");
		int i = 1;
		String requestID = UUID.randomUUID().toString();
		AggregationRequest r1 = new AggregationRequest(-1, requestID, null, new AbstractInput.RequestInputs(), new WAQLQuery());
		EventingInput in = new EventingInput();
		in.setExternalID("0");
		in.setServiceURL(dataServices.get((int)(Math.random() * (double)dataServices.size())).getEPR().getAddress());
		in.setServiceURL(in.getServiceURL() + ("#foo" + i));
		in.setTheContent(XMLUtil.getInstance().toElement(
				"<config><wse:Filter xmlns:wse=\"http://schemas.xmlsoap.org/ws/2004/08/eventing\"><size>1000" +
				"</size></wse:Filter></config>"));
		r1.getAllInputs().add(in);
		r1.getQueries().addPreparationQuery("0","for sliding window $w in $input " +
				"start when true() " +
				"end when false() " + // never close the query window --> input buffer should grow infinitely
				"return $w");
		return r1;
	}

	public static void main(String[] args) throws Exception {
		
		boolean doCreateGraphs = true;
		
		if(doCreateGraphs) {
			GenericTestResult result = GenericTestResult.load("etc/results/migrationTestResults.xml");
			List<String> levels = result.getAllLevelIDsByPattern("t([0-9]+)time", 1);
			List<String> sizes = new LinkedList<String>();
			List<String> times = new LinkedList<String>();
			for(String l : levels) {
				sizes.add("t" + l + "size");
				times.add("t" + l + "time");
			}
			// TODO: adjust to new signature of createGnuplot(..) method!
			result.createGnuplot(times.toArray(new String[0]), sizes.toArray(new String[0]), 
					new String[]{"Migration Duration"}, ResultType.REGRESSION, "Event Buffer Size (KB)", 
					"Duration (sec)", "doc/doa2011/img/testMigration.pdf", "set grid", "set size 1,0.7");
			//CytoscapeGraphUtil.createCytoscapeGraph(result, "doc/doa2011/img/testTopoGraph.pdf");
			System.exit(0);
		}
		
		
		TestServiceStarter.setupDefault();
		AggregationClient gateway = new AggregationClient(Registry.getRegistryProxy().getGateway().getEPR());
		List<AggregatorNode> aggregators = Registry.getRegistryProxy().getAggregatorNodes();
		AggregationClient client = new AggregationClient(ServiceStarter.getDefaultGatewayEPR());
		
		String url = Configuration.getValue("test.eventing.consumer.local.address");
		Endpoint.publish(url, new EventReceiverService(false));
		
		AggregationRequest r = getRequest();
		AbstractNode eventingService = InputTargetExtractor.extractDataSourceNode((EventingInput)r.getAllInputs().get(0), null);
		WebServiceClient.getClient(eventingService.getEPR()).invoke(new RequestInput(
				XMLUtil.getInstance().toElement("<tns:setNewInterval xmlns:tns=\"" + Configuration.NAMESPACE + "\">" +
						"<milliseconds>100</milliseconds></tns:setNewInterval>")).getRequest());
		r.setMonitor(new MonitoringSpecification(new EndpointReference(new URL(url + "?wsdl"))));
		String topologyID = null;

		final GenericTestResult result = new GenericTestResult();
		IterationResult test = result.newIteration();
		Runtime.getRuntime().addShutdownHook(new Thread() {
			public void run() {
				System.out.println("Saving result file...");
				result.save("etc/results/migrationTestResults.xml");
			}
		});
		
		int count = 0; 
		for(int i = 0; i < 3; i ++) {
			topologyID = client.createTopology(null, r);
			
			Thread.sleep(2000);
			List<Topology> topologies = TopologyUtil.collectAllTopologies();
			AggregatorNode fromAggr = topologies.get(0).getTargetServiceRequests().keySet().iterator().next();
			AggregatorNode toAggr = aggregators.get((aggregators.indexOf(fromAggr) + 1) % aggregators.size());
			
			for(int j = 0; j < 40; j ++) {
				Topology topology = gateway.getTopology(topologyID);
				long before = System.currentTimeMillis(); 
				int size = new AggregatorNodeProxy(toAggr).inheritInput(r, topology, fromAggr);
				long after = System.currentTimeMillis(); 
				System.out.println("\nsize: " + size + ", time: " + (after-before) + "\n");
				test.addEntry("t" + count + "size", size / 1000);
				test.addEntry("t" + count + "time", ((double)(after-before) - 1000) / 1000.0); // substract 1 sec here because aggregator sleeps 1 sec when performing migration..
				AggregatorNode tmp = fromAggr;
				fromAggr = toAggr;
				toAggr = tmp;
				Thread.sleep(5500);
				count++;
			}
			gateway.destroyTopology(topologyID);
		}
		
		System.out.println("Test finished.");
		Thread.sleep(60*1000);
		System.exit(0);
	}
	
}
