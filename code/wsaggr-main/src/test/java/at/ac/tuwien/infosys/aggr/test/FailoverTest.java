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

import java.util.LinkedList;
import java.util.List;

import org.junit.Ignore;

import at.ac.tuwien.infosys.aggr.AggregationClient;
import at.ac.tuwien.infosys.ws.WebServiceClient;
import at.ac.tuwien.infosys.aggr.node.AggregatorNode;
import at.ac.tuwien.infosys.aggr.proxy.RegistryProxy;
import at.ac.tuwien.infosys.aggr.request.AggregationRequest;
import at.ac.tuwien.infosys.aggr.request.RequestInput;
import at.ac.tuwien.infosys.aggr.util.ServiceStarter;
import at.ac.tuwien.infosys.test.TestServiceStarter;
import at.ac.tuwien.infosys.util.Configuration;
import at.ac.tuwien.infosys.util.Util;

@Ignore
public class FailoverTest {

	private static List<Process> processes = new LinkedList<Process>();
	
	public static void main(String[] args) throws Exception {
		
		Runtime.getRuntime().addShutdownHook(new Thread() {
			public void run() {
				try {
					Util util = new Util();
					RegistryProxy.resetCache();
					for(AggregatorNode n : new RegistryProxy(ServiceStarter.getDefaultGatewayEPR()).getAggregatorNodes()) {
						try {
							WebServiceClient.getClient(n.getEPR()).invoke(
									new RequestInput(util.xml.toElement(
											"<tns:terminate xmlns:tns=\"" + Configuration.NAMESPACE + "\"/>")).getRequest());
						} catch (Exception e) { }
					}
					for(Process p : processes) {
						p.destroy();
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
		
		try {
			Util util = new Util();
			
			TestServiceStarter.setupDefault(0);
			
			for(int i = 0; i < 5; i ++) {
				String id = i < 10 ? "0" + i : "" + i;
				int port = Integer.parseInt("90" + id);
				String cmd = "./run.sh aggregator localhost " + port + " " + 
						ServiceStarter.getDefaultGatewayEPR().getAddress();
				System.out.println(cmd);
				Process p = Runtime.getRuntime().exec(cmd);
				processes.add(p);
			}
			Thread.sleep(15000);
			
			String request = util.io.readFile(FailoverTest.class.getResourceAsStream("failoverTest.xml"));
			AggregationRequest req = util.xml.toJaxbObject(AggregationRequest.class, util.xml.toElement(request));
			
			AggregationClient gateway = new AggregationClient(ServiceStarter.getDefaultGatewayEPR());
			RegistryProxy registry = new RegistryProxy(ServiceStarter.getDefaultGatewayEPR());
			
			gateway.aggregate(req);
			
			for(int i = 0; i < 100; i ++) {
				for(AggregatorNode n : registry.getAggregatorNodes()) {
					System.out.println("Terminating aggregator " + n.getEPR().getAddress());
					try {
						WebServiceClient.getClient(n.getEPR()).invoke(
								new RequestInput(util.xml.toElement(
										"<tns:terminate xmlns:tns=\"" + 
										Configuration.NAMESPACE + "\"/>")).getRequest());
					} catch (Exception e) { }
					Thread.sleep(5*1000);
				}
				Thread.sleep(5*60*1000);
			}
		} catch (Exception e) {
			e.printStackTrace();
		} 
		
	}
	
}
