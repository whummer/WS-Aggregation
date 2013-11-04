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

package at.ac.tuwien.infosys.aggr.testbed;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.apache.log4j.Logger;
import org.w3c.dom.Element;

import com.amazonaws.services.ec2.model.Instance;

import at.ac.tuwien.infosys.ws.Service;
import at.ac.tuwien.infosys.ws.SoapService;
import at.ac.tuwien.infosys.ws.WebServiceClient;
import at.ac.tuwien.infosys.aggr.proxy.RegistryProxy;
import at.ac.tuwien.infosys.aggr.request.RequestInput;
import at.ac.tuwien.infosys.aggr.testbed.config.Config;
import at.ac.tuwien.infosys.aggr.testbed.config.DataSourceConfig;
import at.ac.tuwien.infosys.aggr.testbed.config.TestRunConfig;
import at.ac.tuwien.infosys.aggr.testbed.messaging.DeployRestRequest;
import at.ac.tuwien.infosys.aggr.testbed.messaging.DeployServiceRequest;
import at.ac.tuwien.infosys.aggr.testbed.messaging.DeploySoapRequest;
import at.ac.tuwien.infosys.aggr.testbed.messaging.Request;
import at.ac.tuwien.infosys.aggr.testbed.messaging.StopRequestServerRequest;
import at.ac.tuwien.infosys.ws.EndpointReference;
import at.ac.tuwien.infosys.util.Util;

public class TestbedMain {
	private static final Logger LOGGER = Util.getLogger(TestbedMain.class);
	
	List<Node> nodePool;
	
	String gatewayUri;
	EndpointReference gateway;
	String configFile;
	Config config;
	
	public TestbedMain(String configFile) {
		this.configFile = configFile;
		this.config = Config.createFromFile(configFile);
	}
	
	public static void main(String[] args) throws Exception {
		org.apache.log4j.PropertyConfigurator.configure("log4j.properties");
		
		TestbedMain tm = new TestbedMain("etc/test/config.my.xml");
		
		tm.deployConfigTest();
//		tm.runTests();
	}
	
	public void runTests() throws Exception {
		List<TestRunConfig> testRunConfigs = config.getTestRunConfigs();

		WebServiceClient client = WebServiceClient.getClient(new EndpointReference(new URL(gatewayUri)));

		Util util = new Util();
		for(TestRunConfig trc : testRunConfigs) {
			RequestInput input = new RequestInput(util.xml.toElement(trc.request));
			Element result = client.invoke(input.getRequest()).getResultAsElement();
			System.out.println("done request, result: ");
			util.xml.print(result);
		}
	}
	
	public void startupInstances() {
		nodePool = new NodeProvider(config).getNodes(
				config.getEC2Config().getNumInstances());
	}
	
	public void deployConfigTest() throws Exception {
		Config config = Config.createFromFile(configFile);
		
		startupInstances();
		
		// restart all service starter nodes. This is required for the service 
		// starter to reload all jar and class files, which may have been updated
		// in the meantime (e.g., by the Dropbox mechanism).
		LOGGER.info("Restarting all remote service starter nodes.");
		for(Node n : nodePool) {
			n.terminateForRestart();
		}
		Thread.sleep(5000);
		
		RoundRobin strat = new RoundRobin(nodePool, config);
		
		gatewayUri = strat.startGateway();
		
		System.out.println("started gateway: " + gatewayUri);
		gateway = new EndpointReference(new URL(gatewayUri));
		
		Thread.sleep(7000);
		
		RegistryProxy registry = new RegistryProxy(gateway);
		int totalStarted = strat.startAggregators();
		for(int i = 0; i < 3; i ++) {
			RegistryProxy.resetCache();
			if(registry.getAggregatorNodes().size() >= totalStarted)
				break;
			Thread.sleep(5000);
		}
		
		List<DeployServiceRequest> requests = 
			createDeployServiceRequests(config);
		
		strat.deployServices(requests);
	}
	
	public void stopNodes() {
		for(Node node : nodePool)
			node.stop();
	}
	
	public void test() {
		Config config = Config.createFromFile(configFile);
		NodeProvider deployer = new NodeProvider(config);
		
		boolean redeploy = false;
		
		// Instance holen
		List<Instance> instances;
		if(redeploy) {
			LOGGER.info("Redeploying");
			instances = deployer.getRunningInstances(1);
			String hostname = Node.getAvailableHost(instances.get(0), deployer.ec2client, deployer.config);
			try {
				Request request = new StopRequestServerRequest();
				new RequestSender(hostname, 6).sendRequest(request);
			} catch (IOException e) {
				LOGGER.warn("Sending StopRequestServerRequest failed");
			}
			instances = deployer.getRunningInstancesRedeploy(1);
		} else {
			instances = deployer.getRunningInstances(1);
		}
		
		// RestService erzeugen
		GenericRestService restService = new GenericRestService();
		
		HashMap<String, String> responses = new HashMap<String, String>();
		responses.put("foo", "bar");
		restService.setResponses(responses);
		
		String hostname = Node.getAvailableHost(instances.get(0), deployer.ec2client, deployer.config);
		String uri = "http://" + hostname + ":8085/rest";
		
		System.out.println("Responses: " + restService.getResponses());
		
		// RestRequest erzeugen
		DeployRestRequest restRequest = new DeployRestRequest(restService, uri, gateway);
		
		// SoapService erzeugen
		String soapUri = "http://" + hostname + ":8086/soap";
		
		String path = "/TODO";
		String classname = "at.ac.tuwien.infosys.aggr.testbed.playground.SoapService1";
		Object service = (Object) createObjectFromClassname(path, classname);
		SoapService soapService = null;
		if(service instanceof SoapService)
			soapService = (SoapService)service; //soapService = (SoapService service);
		
		// SoapRequest erzeugen
		DeploySoapRequest soapRequest = new DeploySoapRequest(soapService, soapUri, gateway);
		
		// Request senden
		try {
			new RequestSender(hostname).sendRequest(restRequest);
			new RequestSender(hostname).sendRequest(soapRequest);
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		for(String s : responses.keySet())
			LOGGER.info("Service operation available: " + uri + "/" + s);
	}
	
	public Object createObjectFromClassname(String classname) {
		try {
			LOGGER.debug("Loading class " + classname);
			return (Object) Class.forName(classname).newInstance();
		} catch (InstantiationException e) {
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		}
		
		return null;
	}
	
	public Object createObjectFromClassname(String classname,
			String path) {
	
		// base path to the package
		File file = new File(path);
		
		LOGGER.info("Loading class " + classname + " from path " + path);
		
		try {
			// Convert File to a URL
			URL url = file.toURI().toURL();
			URL[] urls = new URL[]{url};
			
			ClassLoader cl = new URLClassLoader(urls);
			
			// Load the class. full qualified classname should be located in
			// the directory "path"
			Class<?> cls = cl.loadClass(classname);
			
			return (Object) cls.newInstance();
		} catch (Exception e) {
			e.printStackTrace();
		}

		return null;
	}
	
	/**
	 * Creates DeployServiceRequests from DataSourceConfigs in the given Config
	 * @param config	The Config to create the DeployServiceRequests from
	 * @return		List of DeployServiceRequests created from the given Config
	 */
	private List<DeployServiceRequest> createDeployServiceRequests(
			Config config) {
		
		List<DeployServiceRequest> requests = new ArrayList<DeployServiceRequest>();
		
		for(DataSourceConfig dsc : config.getDataSourceConfigs()) {
			requests.addAll(createParallelDeployServiceRequests(dsc));
		}
		
		return requests;
	}
	
	/**
	 * Creates a number of DeployServiceRequests to be run parallel on one
	 * host. The number of DeployServiceRequests created is determined by.
	 * In the
	 * the given dsc.parallelInstances field.
	 * @param dsc	The DataSourceConfig to create the DeployServiceRequests
	 * 				from.
	 * @return	List of the created DeployServiceRequests
	 */
	private List<DeployServiceRequest> createParallelDeployServiceRequests(
			DataSourceConfig dsc) {
		
		List<DeployServiceRequest> requests = new ArrayList<DeployServiceRequest>();
		
		DeployServiceRequest dsr;
		for(int i = 1; i <= dsc.parallelInstances; i++) {
			dsr = createDeployServiceRequest(dsc);
			
			String uri = UriReplacer.replaceInstanceNo(dsr.getUri(), i);
			dsr.setUri(uri);
			
			requests.add(dsr);
		}
		
		return requests;
	}
	
	/**
	 * Creates one DeployServiceRequest from a given DataSourceConfig
	 * @param dsc	The {@link DataSourceConfig} to create the {@link DeployServiceRequest} from
	 * @return	The created DeployServiceRequest
	 */
	private DeployServiceRequest createDeployServiceRequest(
			DataSourceConfig dsc) {
		
		DeployServiceRequest request;
		if(dsc.type.equals("REST")) {
			request = new DeployRestRequest();
		} else if(dsc.type.equals("SOAP")) {
			request = new DeploySoapRequest();
		} else {
			LOGGER.error("DataSource does not contain valid type field");
			return null;
		}
		request.setRegistry(gateway);
		
		Service service;
		if(dsc.classname != null) {
			if(dsc.classpath != null) {
				LOGGER.debug("Creating Service from classname: " + 
						dsc.classname);
				
				service = (Service) createObjectFromClassname(
						dsc.classname, dsc.classpath);
				request.setService(service);
				
				LOGGER.debug("Using service url: " + dsc.url);
				
				request.setUri(dsc.url);
				return request;
			} else {
				service = (Service) createObjectFromClassname(dsc.classname);
				request.setService(service);
				request.setUri(dsc.url);
				return request;
			}
		} else {
			/*
			 * TODO build Service from DataSourceOperationConfigs
			 * 
			 * One way to do it would be to create Java code out of the config
			 * and then load the Class from the source file.
			 */
		}
		
		return null;
	}
	
	public void setConfigFile(String configFile) {
		this.configFile = configFile;
	}
	
	public String getGatewayUrl() {
		return gatewayUri;
	}
	
	public Config getConfig() {
		return config;
	}
}
