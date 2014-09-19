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
package at.ac.tuwien.infosys.test;

import io.hummer.util.Configuration;
import io.hummer.util.ws.EndpointReference;

import java.net.BindException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import at.ac.tuwien.infosys.aggr.node.DataServiceNode;
import at.ac.tuwien.infosys.aggr.node.Registry;
import at.ac.tuwien.infosys.aggr.util.ServiceStarter;

import com.sun.net.httpserver.HttpServer;

public class TestServiceStarter {

	public static Map<Integer,HttpServer> servers = new HashMap<Integer, HttpServer>();
	public static boolean useBuiltInServer = true;
	
	static {
		Logger.getAnonymousLogger().getParent().setLevel(Level.OFF);
		org.eclipse.jetty.util.log.Log.setLog(null);
	}
	
	public static void startDataServices(int businessServices, String host, int port, String[] features) throws Exception {
		Logger.getAnonymousLogger().getParent().setLevel(Level.OFF);
		if(host == null)
			host = Configuration.getHost(Configuration.PROP_BINDHOST, Configuration.PROP_HOST);
		for(int i = 1; i <= businessServices; i++) {
			String address = "http://" + host + ":" + port + "/service" + i + "instance";
			EndpointReference epr = new EndpointReference(
					"<wsa:EndpointReference xmlns:wsa=\"http://schemas.xmlsoap.org/ws/2004/08/addressing\" " +
					"xmlns:tns=\"" + Configuration.NAMESPACE + "\">" +
						"<wsa:Address>" + address + "</wsa:Address>" +
						"<wsa:ServiceName PortName=\"BusinessServicePort\">" +
							"tns:BusinessServiceService" +
						"</wsa:ServiceName>" +
					"</wsa:EndpointReference>");
			BusinessService s = new BusinessService();
			s.setEPR(epr);
			s.deploy(address);
		}
		addDataServicesToRegistry(businessServices, host, port, features);
		System.out.println(businessServices + " 'business' services started.");
	}
	public static void addDataServicesToRegistry(int businessServices, String host, int port, String[] features) throws Exception {
		for(int i = 1; i <= businessServices; i++) {
			String address = "http://" + host + ":" + port + "/service" + i + "instance";
			EndpointReference epr = new EndpointReference(
					"<wsa:EndpointReference xmlns:wsa=\"http://schemas.xmlsoap.org/ws/2004/08/addressing\" " +
					"xmlns:tns=\"" + Configuration.NAMESPACE + "\">" +
						"<wsa:Address>" + address + "</wsa:Address>" +
						"<wsa:ServiceName PortName=\"BusinessServicePort\">" +
							"tns:BusinessServiceService" +
						"</wsa:ServiceName>" +
					"</wsa:EndpointReference>");
			DataServiceNode n = new DataServiceNode();
			n.setEPR(epr);
			for(String feature : features) {
				Registry.getRegistryProxy().addDataServiceNode(feature, n);
			}
		}
	}
	
	public static void start(int aggregators, int businessServices, String[] features) throws Exception {
		Logger.getAnonymousLogger().getParent().setLevel(Level.OFF);

		int port = 8889;
		ServiceStarter.startRegistry();
		ServiceStarter.startGateway();
		startDataServices(businessServices, null, port, features);
		ServiceStarter.startAggregators(aggregators);
		
	}

	public static void setupDefault() throws Exception {
		setupDefault(7);
	}

	public static void setupDefault(int numAggregators) throws Exception {
		ServiceStarter.setupDefault(numAggregators);
		int port = 8889;
		try {
			startDataServices(10, null, port, new String[]{"Voting", "Booking", "Rendering", "stockpriceGoogle"});	
		} catch (BindException e) {}
	}
	
	public static void main(String[] args) throws Exception {
		Logger.getAnonymousLogger().getParent().setLevel(Level.WARNING);

		if(args.length < 1 || args[0].equals("help")) {
			System.out.println("Usage: java " + TestServiceStarter.class.getName() + " (gateway|aggregators|services)");
			setupDefault();
			System.out.println("Default setup completed.");
		}
		else if(args[0].equals("services")) {
			int num = 500;
			int port = 8889;
			String host = null;
			if(args.length >= 2)
				num = Integer.parseInt(args[1]);
			if(args.length >= 4) {
				host = args[2];
				port = Integer.parseInt(args[3]);
			} else if(args.length >= 3)
				port = Integer.parseInt(args[2]);
			startDataServices(num, host, port, new String[]{"Voting", "Booking", "Rendering", "stockpriceGoogle"});
			
		} else if(Arrays.asList("optimizer","aggregators","aggregator","registry","gateway","gatewayAndAggregators").contains(args[0])) {
			
			ServiceStarter.main(args);
			
		} else {
			throw new IllegalArgumentException("Unexpected command line parameter(s): '" + args[0] + "'");
		}
		
	}
	
}