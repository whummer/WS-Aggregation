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

import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import javax.xml.ws.Endpoint;

import org.apache.log4j.Logger;

import at.ac.tuwien.infosys.util.Util;
import at.ac.tuwien.infosys.ws.Service;

import com.sun.grizzly.http.SelectorThread;
//import com.sun.jersey.api.container.grizzly.GrizzlyWebContainerFactory;

/**
 * @author Daniel Domberger
 *
 */
public class RequestExecuter {
	
	private static final Logger LOGGER = Util.getLogger(RequestExecuter.class);
	
	String runCmd = "./run.sh";
	
	// port -> package name
	private Map<Integer,String> packageNames = new HashMap<Integer, String>();
	// port -> selector thread
	private Map<Integer,SelectorThread> selectors = new HashMap<Integer, SelectorThread>();
	// uri -> endpoint
	private Map<String,Endpoint> endpoints = new HashMap<String,Endpoint>();
	
	private RequestServer parentRequestServer;
	
	public RequestExecuter(RequestServer parentRequestServer,
			BootstrapWrapper bootstrapper) {
		this.parentRequestServer = parentRequestServer;
	}
	
	public void deployRestService(Object service, String baseUri) {

		removeService(baseUri);
		
		final Map<String, String> initParams = new HashMap<String, String>();
		
		String pkg = service.getClass().getPackage().getName();
		String newInitPkgs;
		
		try {
			LOGGER.info("Deploying REST Service at uri: " + baseUri);
			URL url = new URL(baseUri);
			int port = url.getPort();
			
			if(!packageNames.containsKey(port))
				packageNames.put(port, "");
			
			newInitPkgs = packageNames.get(port) + " " + pkg;
			packageNames.put(port, newInitPkgs);
			
			LOGGER.debug("Set package names to: " + 
					packageNames.get(url.getPort()));
			
			initParams.put("com.sun.jersey.config.property.packages", 
					newInitPkgs);
		
			if(selectors.containsKey(url.getPort())) {
				SelectorThread selectorThread = selectors.get(url.getPort());
				selectorThread.stopEndpoint();
				LOGGER.debug("Port in use, selectorThread stopped: " +
						selectors);
			}

			LOGGER.debug("Getting new SelectorThread: " + baseUri + ", " +
					initParams);
			// TODO doesn't compile (as of 2013-02-01)
			//SelectorThread selectorThread = 
			//	GrizzlyWebContainerFactory.create(baseUri, initParams);
			//LOGGER.debug("SelectorThread created: " + selectorThread);
			//selectors.put(url.getPort(), selectorThread);
		} catch (IllegalArgumentException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void deploySoapService(Object service, String uri) {
		removeService(uri);
		LOGGER.info("Deploying SOAP Service at uri: " + uri);
		Endpoint e = Endpoint.publish(uri, service);
		endpoints.put(uri, e);
	}

	private void removeService(String uri) {
		Endpoint e = endpoints.remove(uri);
		if(e != null) {
			LOGGER.info("Undeploying SOAP Service at uri: " + uri);
			((Service)e.getImplementor()).onUndeploy();
			e.stop();
		}
	}
	
	public void startAggregator(int port, String gatewayUri, String aggregatorUrl) {
		ProcessBuilder pbChmod = new ProcessBuilder("chmod +x run.sh");
		ProcessBuilder pb = new ProcessBuilder(runCmd, "aggregator",
				aggregatorUrl, Integer.toString(port), gatewayUri);
		
		try {
			pbChmod.start().waitFor();
			pb.start();
		} catch (Exception e){
			e.printStackTrace();
		}
	}
	
	public void startGateway(String gatewayUri) {
		ProcessBuilder pbChmod = new ProcessBuilder("chmod +x run.sh");
		ProcessBuilder pb = new ProcessBuilder(runCmd, "gateway");
		
		try {
			pbChmod.start().waitFor();
			pb.start();
			// add this sleep command to wait for the gateway to start up
			Thread.sleep(5000);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public void stopRequestServer() {
		parentRequestServer.stop();
	}
	
	public RequestServer getParentRequestServer() {
		return parentRequestServer;
	}
}
