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
package at.ac.tuwien.infosys.aggr.util;

import io.hummer.util.Configuration;
import io.hummer.util.Util;
import io.hummer.util.par.GlobalThreadPool;
import io.hummer.util.perf.MemoryAgent;
import io.hummer.util.ws.AbstractNode;
import io.hummer.util.ws.EndpointReference;

import java.io.File;
import java.net.BindException;
import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.server.handler.ResourceHandler;

import at.ac.tuwien.infosys.aggr.account.CredentialsValidationHandler;
import at.ac.tuwien.infosys.aggr.node.AggregatorNode;
import at.ac.tuwien.infosys.aggr.node.BrowserAggregatorNode;
import at.ac.tuwien.infosys.aggr.node.Gateway;
import at.ac.tuwien.infosys.aggr.node.OptimizerNode;
import at.ac.tuwien.infosys.aggr.node.Registry;
import at.ac.tuwien.infosys.aggr.proxy.GatewayProxy;

public class ServiceStarter {

	private static final org.apache.log4j.Logger logger = Util.getLogger(ServiceStarter.class);
	//public static Map<Integer,HttpServer> servers = new HashMap<Integer, HttpServer>(); // TODO remove (?)
	public static boolean useBuiltInServer = true;
	private static Util util = new Util();
	private static org.hsqldb.Server hsqldbServer;
	
	static {
		//System.setProperty("com.sun.net.httpserver.HttpServerProvider", "org.mortbay.jetty.j2se6.JettyHttpServerProvider");
		Logger.getAnonymousLogger().getParent().setLevel(Level.OFF);
		org.eclipse.jetty.util.log.Log.setLog(null);
		/* important: trigger static initializer of Constants which also sets 
		 * important parameters concerning persistence.. */
		Constants.PU.toString();
	}

	private static void startAggregator(String[] args) throws Exception {

		if(args.length <= 2) {
			startAggregator(Integer.parseInt(args[1]));
		} else if(args.length >= 3) {
			int port = -1;
			if(args.length >= 4 && !util.str.isEmpty(args[3]) && args[3].length() > 1) {
				try {
					new URL(args[3]);
					Configuration.setValue(Configuration.PROP_REGISTRY_URL, args[3]);
				} catch (Exception e) {
					logger.info("Invalid URL: " + args[3] + " : " + e);
				}
			}
			try {
				port = Integer.parseInt(args[2]);
			} catch (Exception e) { }
			int numTries = 1;
			if(port <= 0) {
				port = 9701;
				numTries = 10;
			}
			String host = args[1];
			String bindhost = args[1];
			if(host.contains("$") || host.contains("{") || host.trim().equals("null")) {
				host = Configuration.getHost(Configuration.PROP_HOST);
				bindhost = Configuration.getHost(Configuration.PROP_BINDHOST, Configuration.PROP_HOST);
			}
			for(int i = 0; i < numTries; i ++) {
				try {
					startAggregator(host, bindhost, port);
					break;
				} catch (Exception e) {
					port++;
					continue;
				}
			}
		}
		
	}
	public static void startAggregators(int aggregators) throws Exception {
		Logger.getAnonymousLogger().getParent().setLevel(Level.OFF);
		for(int i = 1; i <= aggregators; i++) {
			final int id = i;
			new Thread() {
				public void run() {
					try {
						startAggregator(id);
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}.start();
			Thread.sleep(200);
		}
		System.out.println(aggregators + " aggregator instances started.");
	}
	public static void startBrowserAggregator(int ID) throws Exception {
		String id = ID < 10 ? "0" + ID : "" + ID;
		int port = Integer.parseInt("97" + id);
		String host = Configuration.getHost(Configuration.PROP_HOST);
		String bindhost = Configuration.getHost(Configuration.PROP_BINDHOST,Configuration.PROP_HOST);
		startBrowserAggregator(host, bindhost, port);
	}
	public static void startBrowserAggregator(String host, String bindhost, int port) throws Exception {
		String address = "http://" + host + ":" + port + "/aggregator";
		EndpointReference epr = new EndpointReference(
				"<wsa:EndpointReference xmlns:wsa=\"http://schemas.xmlsoap.org/ws/2004/08/addressing\" " +
				"xmlns:tns=\"" + Configuration.NAMESPACE + "\">" +
					"<wsa:Address>" + address + "</wsa:Address>" +
					"<wsa:ServiceName PortName=\"AggregatorNodePort\">" +
						"tns:AggregatorNodeService" +
					"</wsa:ServiceName>" +
				"</wsa:EndpointReference>");
		BrowserAggregatorNode a = new BrowserAggregatorNode(epr, true, port);
		
		String bindaddress = "http://" + bindhost + ":" + port + "/aggregator";
		a.deploy(bindaddress);

		String wsdlURL = address.endsWith("?wsdl") ? address : (address + "?wsdl");
		long size = MemoryAgent.getSafeDeepSizeOf(a);
		String heap = size <= 0 ? "" : " (Used heap memory: " + size + ")";
		System.out.println("Started aggregator, SOAP/WSDL: " + wsdlURL + heap);
	}
	
	public static AggregatorNode startAggregator(int ID) throws Exception {
		String id = ID < 10 ? "0" + ID : "" + ID;
		int port = Integer.parseInt("97" + id);
		String host = Configuration.getHost(Configuration.PROP_HOST);
		String bindhost = Configuration.getHost(Configuration.PROP_BINDHOST,Configuration.PROP_HOST);
		return startAggregator(host, bindhost, port);
	}
	public static AggregatorNode startAggregator(String host, String bindhost, int port) throws Exception {
		String address = "http://" + host + ":" + port + "/aggregator";
		EndpointReference epr = new EndpointReference(
				"<wsa:EndpointReference xmlns:wsa=\"http://schemas.xmlsoap.org/ws/2004/08/addressing\" " +
				"xmlns:tns=\"" + Configuration.NAMESPACE + "\">" +
					"<wsa:Address>" + address + "</wsa:Address>" +
					"<wsa:ServiceName PortName=\"AggregatorNodePort\">" +
						"tns:AggregatorNodeService" +
					"</wsa:ServiceName>" +
				"</wsa:EndpointReference>");
		AggregatorNode a = new AggregatorNode(epr, true);
		
		String bindaddress = "http://" + bindhost + ":" + port + "/aggregator";
		/* the deploy operation automatically adds the aggregator to the registry.. */
		a.deploy(bindaddress);

		String wsdlURL = address.endsWith("?wsdl") ? address : (address + "?wsdl");
		long size = MemoryAgent.getSafeDeepSizeOf(a);
		String heap = size <= 0 ? "" : " (Used heap memory: " + size + ")";
		System.out.println("Started aggregator, SOAP/WSDL: " + wsdlURL + heap);
		
		return a;
	}

	public static Gateway startGateway() throws Exception {
		String url = Configuration.getUrlWithVariableHost(Configuration.PROP_GATEWAY_URL, Configuration.PROP_HOST);
		EndpointReference epr = new EndpointReference(
				"<wsa:EndpointReference xmlns:wsa=\"http://schemas.xmlsoap.org/ws/2004/08/addressing\" " +
				"xmlns:tns=\"" + Configuration.NAMESPACE + "\">" +
					"<wsa:Address>" + url + "</wsa:Address>" +
					"<wsa:ServiceName PortName=\"GatewayPort\">" +
						"tns:GatewayService" +
					"</wsa:ServiceName>" +
				"</wsa:EndpointReference>");
		return startGateway(epr);
	}
	public static Gateway startGateway(String url) throws Exception {
		return startGateway(new EndpointReference(new URL(url)));
	}
	public static Gateway startGateway(EndpointReference epr) throws Exception {
		Logger.getAnonymousLogger().getParent().setLevel(Level.OFF);
		Gateway gateway = new Gateway();
		
		gateway.setEPR(epr);
		String bindurl = Configuration.getUrlWithVariableHost(Configuration.PROP_GATEWAY_URL, Configuration.PROP_BINDHOST, Configuration.PROP_HOST);
		String url = epr.getAddress();
		String wsdlURL = url.endsWith("?wsdl") ? url : (url + "?wsdl");
		logger.info("Starting gateway, SOAP/WSDL: " + wsdlURL + " , REST/WADL: " + gateway.getWadlURL());
		/* the deploy operation automatically adds the aggregator to the registry.. */
		gateway.deploy(bindurl, new CredentialsValidationHandler());
		return gateway;
	}
	public static OptimizerNode startOptimizer() throws Exception {
		
		String host = Configuration.getHost();
		String address = "http://" + host + ":8895/optimizer";
		EndpointReference epr = new EndpointReference(
				"<wsa:EndpointReference xmlns:wsa=\"http://schemas.xmlsoap.org/ws/2004/08/addressing\" " +
				"xmlns:tns=\"" + Configuration.NAMESPACE + "\">" +
					"<wsa:Address>" + address + "</wsa:Address>" +
					"<wsa:ServiceName PortName=\"OptimizerNodePort\">" +
						"tns:OptimizerNodeService" +
					"</wsa:ServiceName>" +
				"</wsa:EndpointReference>");
		OptimizerNode o = new OptimizerNode(epr);
		
		o.deploy(address);
		o.setOptimization(true, null);
		
		return o;
	}
	
	public static EndpointReference getDefaultGatewayEPR() throws Exception {
		String address = Configuration.getUrlWithVariableHost(Configuration.PROP_GATEWAY_URL, Configuration.PROP_HOST);
		return new EndpointReference(
				"<wsa:EndpointReference xmlns:wsa=\"http://schemas.xmlsoap.org/ws/2004/08/addressing\" " +
				"xmlns:tns=\"" + Configuration.NAMESPACE + "\">" +
					"<wsa:Address>" + address + "</wsa:Address>" +
					"<wsa:ServiceName PortName=\"GatewayPort\">" +
						"tns:GatewayService" +
					"</wsa:ServiceName>" +
				"</wsa:EndpointReference>");
	}
	
	public static void startRegistry() throws Exception {
		Logger.getAnonymousLogger().getParent().setLevel(Level.OFF);

		String address = Configuration.getUrlWithVariableHost(Configuration.PROP_REGISTRY_URL, Configuration.PROP_HOST);
		Registry r = new Registry();
		r.setEPR(new EndpointReference(
			"<wsa:EndpointReference xmlns:wsa=\"http://schemas.xmlsoap.org/ws/2004/08/addressing\" " +
				"xmlns:tns=\"" + Configuration.NAMESPACE + "\">" +
				"<wsa:Address>" + address + "</wsa:Address>" +
				"<wsa:ServiceName PortName=\"GatewayPort\">" +
					"tns:GatewayService" +
				"</wsa:ServiceName>" +
			"</wsa:EndpointReference>"));
		address = Configuration.getUrlWithVariableHost(Configuration.PROP_REGISTRY_URL, Configuration.PROP_BINDHOST, Configuration.PROP_HOST);
		AbstractNode.deploy(r, address);
		logger.info("Started registry, SOAP/WSDL: " + address + " , REST/WADL: " + r.getWadlURL());
	}

	public static void stopHSQLDBServer() {
		if(hsqldbServer != null)
			hsqldbServer.stop();
	}
	public static void startHSQLDBServer() {
		final Util util = new Util();
		/* start embedded HSQLDB server  */
		Runnable r = new Runnable() {
			public void run() {
				try {
					if(!new File("etc/hsqldb").exists()) {
						new File("etc/hsqldb").mkdirs();
					}
					if(util.net.isPortOpen("localhost", 9001)) {
						/* HSQLDB server already running.. */
						logger.info("HSQLDB server already running...");
						return;
					}
					Logger databaseLogger = Logger.getLogger("hsqldb.db"); 
					databaseLogger.setUseParentHandlers(false); 
					databaseLogger.setLevel(Level.WARNING);
					hsqldbServer = new org.hsqldb.Server();
			        hsqldbServer.setLogWriter(null);
			        hsqldbServer.setSilent(true);
			        hsqldbServer.setDatabasePath(0, "file:etc/hsqldb/wsaggr");
			        hsqldbServer.setDatabaseName(0, "wsaggr");
			        hsqldbServer.setNoSystemExit(true);
			        hsqldbServer.start();
					org.apache.log4j.Logger.getLogger("hsqldb.db").setLevel(org.apache.log4j.Level.WARN);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		};
		GlobalThreadPool.execute(r);

	}
	
	public static void setupDefault(int numAggregators) throws Exception {
		startHSQLDBServer();
		try {
			ServiceStarter.startRegistry();
		} catch (BindException e) {}
		try {
			ServiceStarter.startGateway();
		} catch (BindException e) {}
		try {
			if(numAggregators > 0)
				ServiceStarter.startAggregators(numAggregators);
		} catch (BindException e) {}
	}

	public static void main(String[] args) throws Exception {
		Logger.getAnonymousLogger().getParent().setLevel(Level.WARNING);

		if(args.length < 1 || args[0].equalsIgnoreCase("help")) {
			System.out.println("Usage: java " + ServiceStarter.class.getName() + " (gateway|aggregators|services)");
			setupDefault(5);
			return;
		}

		startHSQLDBServer();
		
		if(args[0].equals("aggregators")) {
			startAggregators(10);
		} else if(args[0].equals("aggregator")) {
			startAggregator(args);
		} else if(args[0].equals("gateway")) {
			startGateway();
		} else if(args[0].equals("optimizer")) {
			startOptimizer();
		} else if(args[0].equals("registry")) {
			startRegistry();
		} else if(args[0].equals("gatewayAndAggregator")) {
			startGateway();
			startAggregator(args);
		} else if(args[0].equals("gatewayAndAggregators")) {
			startGateway();
			startAggregators(7);
		} else if(args[0].equals("testServices")) {
			// TODO fix classpath setup
			logger.info("Starting eventing test service.");
			Class.forName("at.ac.tuwien.infosys.aggr.test.EventServiceStockTrade").getMethod(
					"start", int.class, long.class).invoke(null, 1, 1000);
		} else if(args[0].equals("web")) {
			
			URL webappURL = new URL(Configuration.getUrlWithVariableHost(
					Configuration.PROP_WEBAPP_URL, Configuration.PROP_HOST));

			Server server = new Server(webappURL.getPort());
			ContextHandlerCollection contexts = new ContextHandlerCollection();
			server.setHandler(contexts);

			String path = "web/";
			for(int i = 0; i < 2; i ++) {
				if(!new File(path).exists()) {
					path = "../" + path;
				}
			}
			if(!new File(path).exists()) {
				System.out.println("Warning: web directory '" + path + "' does not exist!");
			} else {
				System.out.println("Using web directory '" + path + "'");
			}

			ContextHandler srcroot = new ContextHandler();
	        srcroot.setResourceBase(path);
	        srcroot.setHandler(new ResourceHandler());
	        srcroot.setContextPath(webappURL.getPath());
	        contexts.addHandler(srcroot);
	        
	        server.start();
	        System.out.println("Web application started at " + webappURL);
	        server.join();
		}

	}
	
}
