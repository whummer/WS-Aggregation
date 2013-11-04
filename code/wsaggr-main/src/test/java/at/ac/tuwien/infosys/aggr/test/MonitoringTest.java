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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;

import org.junit.Ignore;
import org.w3c.dom.Element;

import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import at.ac.tuwien.infosys.aggr.AggregationClient;
import at.ac.tuwien.infosys.aggr.request.AggregationRequest;
import at.ac.tuwien.infosys.test.TestServiceStarter;
import at.ac.tuwien.infosys.util.Configuration;
import at.ac.tuwien.infosys.ws.EndpointReference;
import at.ac.tuwien.infosys.util.Util;

@Ignore
@SuppressWarnings("all")
public class MonitoringTest implements HttpHandler {

	private static Util util = new Util();

	public static void main(String[] args) throws Exception {
		
		String in = util.readFile(MonitoringTest.class.getResourceAsStream("monitoringTest.xml"));
		Element element = util.xml.toElement(in);
		
		AggregationRequest request = util.xml.toJaxbObject(AggregationRequest.class, element);
		//System.out.println(Util.toString(request));
		
		TestServiceStarter.setupDefault();
		
		String address = Configuration.getRegistryAddress();
		AggregationClient c = new AggregationClient(new EndpointReference(
			"<wsa:EndpointReference xmlns:wsa=\"http://schemas.xmlsoap.org/ws/2004/08/addressing\" " +
				"xmlns:tns=\"" + Configuration.NAMESPACE + "\">" +
				"<wsa:Address>" + address + "</wsa:Address>" +
				"<wsa:ServiceName PortName=\"GatewayPort\">" +
					"tns:GatewayService" +
				"</wsa:ServiceName>" +
			"</wsa:EndpointReference>"));
		
		new MonitoringTest().setupNotificationListener();
		
		//Element result = c.aggregate(request);
		//Util.print(result);
		
		c.createTopology("tree(2,2)", request);
	}

	public void setupNotificationListener() throws Exception {
		HttpServer server = HttpServer.create(new InetSocketAddress(9022), 0);
		HttpContext context = server.createContext("/wsaggrEvents");
		context.setHandler(this);
		server.start();
	}

	public void handle(HttpExchange ex) throws IOException {
		String temp = null;
		String request = "";
		BufferedReader br = new BufferedReader(new InputStreamReader(ex.getRequestBody()));
		while((temp = br.readLine()) != null) {
			request += temp + "\n";
		}
		System.out.println(request);
	}
	
}
