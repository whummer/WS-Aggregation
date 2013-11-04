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

package at.ac.tuwien.infosys.events.schema.test;

import org.w3c.dom.Element;

import at.ac.tuwien.infosys.aggr.AggregationClient;
import at.ac.tuwien.infosys.aggr.util.ServiceStarter;
import at.ac.tuwien.infosys.events.EventReceiverService;
import at.ac.tuwien.infosys.events.schema.ESDSchema;
import at.ac.tuwien.infosys.aggr.request.AggregationRequest;
import at.ac.tuwien.infosys.aggr.xml.XPathProcessor;
import at.ac.tuwien.infosys.util.Util;

@org.junit.Ignore
public class EventSchemaTest {

	public void test() throws Exception {
		Util util = new Util();
		
		ServiceStarter.setupDefault(2);
		
		Element tests = util.xml.toElement(util.io.readFile(
				EventSchemaTest.class.getResourceAsStream("eventSchemaTest.xml")));

		//EventServiceRBAC producer = new EventServiceRBAC(
		//		new EndpointReference(new URL("http://localhost:8787/rbac?wsdl")));
		//producer.deploy("http://localhost:8787/rbac");
		EventReceiverService receiver = new EventReceiverService(true);
		receiver.deploy("http://localhost:40405/client");
	
		AggregationClient c = new AggregationClient(ServiceStarter.getDefaultGatewayEPR());
		
		for(Object t : XPathProcessor.evaluateAsList("//test", tests)) {
		
			Element schema = (Element)XPathProcessor.evaluateAsList("descendant::schema", (Element)t).get(0);
			ESDSchema root = util.xml.toJaxbObject(ESDSchema.class, schema);
			String query = root.toQuery();
			System.out.println("query: " + query);
	
			AggregationRequest r = util.xml.toJaxbObject(AggregationRequest.class, 
					util.xml.toElement(EventSchemaTest.class.getResourceAsStream(
							"eventingQueryRBAC.xml")));
			
			r.getQueries().getPreparationQueries("1").get(0).setValue(query);
			
			c.aggregate(r);
		}
		
		Thread.sleep(2000);
		//producer.start();
	}
	
	public static void main(String[] args) throws Exception {
		EventSchemaTest test = new EventSchemaTest();
		test.test();
	}

}
