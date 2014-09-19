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

import java.net.BindException;
import java.net.URL;
import java.util.List;

import javax.xml.ws.Endpoint;

import org.w3c.dom.Element;

import at.ac.tuwien.infosys.aggr.AggregationClient;
import at.ac.tuwien.infosys.aggr.monitor.MonitoringSpecification;
import at.ac.tuwien.infosys.aggr.node.DataServiceNode;
import at.ac.tuwien.infosys.aggr.node.Registry;
import at.ac.tuwien.infosys.aggr.request.AggregationRequest;
import at.ac.tuwien.infosys.aggr.request.EventingInput;
import at.ac.tuwien.infosys.aggr.request.NonConstantInput;
import at.ac.tuwien.infosys.aggr.util.BurstServiceStarter;
import at.ac.tuwien.infosys.aggr.util.ServiceStarter;
import at.ac.tuwien.infosys.events.EventReceiverService;
import at.ac.tuwien.infosys.events.test.EventingTestServiceStarter;
import io.hummer.util.Util;
import io.hummer.util.ws.EndpointReference;

@org.junit.Ignore
public class TestSetup {

	private Util util = new Util();

	public void doTestSetup() throws Exception {

		// ServiceStarter.setupDefault(3);
		ServiceStarter.startHSQLDBServer();
		try {
			ServiceStarter.startRegistry();
		} catch (BindException e) {
		}
		try {
			ServiceStarter.startGateway();
		} catch (BindException e) {
		}
		BurstServiceStarter.startAggregators(5);

		EventingTestServiceStarter.startEventProducers(1, 5000);
		String eventReceiver = "http://localhost:9887/events";
		EventReceiverService service = new EventReceiverService(false);
		Endpoint.publish(eventReceiver, service);
		List<DataServiceNode> services = Registry.getRegistryProxy()
				.getDataServiceNodes("Eventing");

		int id = 1;
		AggregationRequest r = new AggregationRequest();
		for (DataServiceNode dsn : services) {
			NonConstantInput in = new EventingInput();
			in.setExternalID("" + id++);
			in.setServiceURL(dsn.getEPR().getAddress());
			r.getInputs().addInput(in);

			r.getQueries().addPreparationQuery(
					in.getExternalID(),
					"for tumbling window $w in $input "
							+ "start at $spos when true() "
							+ "end at $epos when ($epos - $spos) eq 0"
							+ "return <ticks><a>{$w}</a></ticks>");
		}
		MonitoringSpecification listener = new MonitoringSpecification(
				new EndpointReference(new URL(eventReceiver + "?wsdl")));
		r.setMonitor(listener);

		AggregationClient client = new AggregationClient(Registry
				.getRegistryProxy().getGateway().getEPR(), "wsaggr", "wsaggr!");
		Element e = client.aggregate(r);
		util.xml.print(e);

	}

	public static void main(String[] args) throws Exception {
		new TestSetup().doTestSetup();
	
	}
}
