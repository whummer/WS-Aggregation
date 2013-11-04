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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;

import at.ac.tuwien.infosys.aggr.request.AbstractInput;
import at.ac.tuwien.infosys.aggr.request.AggregationRequest;
import at.ac.tuwien.infosys.aggr.request.NonConstantInput;
import at.ac.tuwien.infosys.aggr.testbed.config.AggregatorConfig;
import at.ac.tuwien.infosys.aggr.testbed.config.Config;
import at.ac.tuwien.infosys.aggr.testbed.config.TestRunConfig;
import at.ac.tuwien.infosys.aggr.testbed.messaging.DeployServiceRequest;
import at.ac.tuwien.infosys.util.Util;
import at.ac.tuwien.infosys.util.xml.XMLUtil;

/**
 * @author Daniel Domberger
 *
 */
public class RoundRobin implements DeploymentStrategy {
	private static final Logger LOGGER = Util.getLogger(RoundRobin.class);
	
	List<Node> nodes;		// all nodes (unnamedNodes + namedNodes)
	List<Node> unnamedNodes;
	Map<String, Node> namedNodes;
	Config config;
	
	String gatewayUri;
	
	int aggregatorDefaultPort = 8060;
	
	/* Index of Node used last */
	int last = -1;
	
	public RoundRobin(List<Node> nodes, Config config) {
		this.nodes = nodes;
		unnamedNodes = new ArrayList<Node>();
		unnamedNodes.addAll(nodes);
		namedNodes = new HashMap<String, Node>();
		this.config = config;
	}

	/**
	 * Starts Aggregators according to the given AggregatorConfig
	 * @param aggrConfig	AggregatorConfig defining host, number of
	 * 						aggregators and port of the first aggregator to use
	 * 						for the started aggregator(s)
	 * @return	Number of Aggregators started
	 */
	public int startAggregators() {
		List<AggregatorConfig> aggregatorConfigs = config.getAggregatorConfigs();
		
		int parallelAggregators;
		int totalStarted = 0;
		LOGGER.info("Nr of AggregatorConfigs: " + aggregatorConfigs.size());
		
		for(AggregatorConfig aggrConfig : aggregatorConfigs) {
			parallelAggregators = 1;
			if(aggrConfig.parallelAggregators > 0)
				parallelAggregators = aggrConfig.parallelAggregators;
			
			LOGGER.info("Starting " + parallelAggregators + " parallel aggregators");
			
			for(int i = 1; i <= parallelAggregators; i++) {
				startAggregator(aggrConfig);
				totalStarted++;
			}
		}
		return totalStarted;
	}
	
	private void startAggregator(AggregatorConfig aggrConfig) {
		Node node;
		int port;
		
		if(aggrConfig.host != null) {
			node = getNamedNode(aggrConfig.host);
		} else {
			node = nextNode();
		}
		
		port = aggrConfig.port;
		
		if(port == 0)
			port = aggregatorDefaultPort;
		
		while(node.getAggregatorPorts().contains(port))
			port++;
		
		try {
			node.startAggregator(port, gatewayUri);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Returns a named Node with the given name. If no Node with the given
	 * name exists, names an unnamed Node and returns it.
	 * @param name	The name of the Node
	 * @return	The Node with given name
	 */
	private Node getNamedNode(String name) {
		if(! namedNodes.containsKey(name)) {
			if(unnamedNodes.size() <= 0) {
				LOGGER.error("No unnamed Node left for naming it: " + name +
						". Using next node");
				return nextNode();
			} else {
				Node node = unnamedNodes.get(0);
				node.setNodeName(name);
				unnamedNodes.remove(0);
				namedNodes.put(name, node);
				
				LOGGER.info("Node '" + name + "' has public DNS name: " + 
						node.getPublicHostname());
			}
		}
		return namedNodes.get(name);
	}
	
	/**
	 * Starts Gateway on the configured host
	 * @return	returns the gatewayUri
	 */
	public String startGateway() {
		UriReplacer uriReplacer = new UriReplacer(config.getGatewayConfig().uri);
		String nodeName = uriReplacer.getHostId();
		Node node = getNamedNode(nodeName);
		try {
			gatewayUri = node.startGateway(uriReplacer);
			return gatewayUri;
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		return null;
	}
	
	private void replaceUrisInTestruns() throws Exception {
		List<TestRunConfig> testRunConfigs = config.getTestRunConfigs();
		UriReplacer uriReplacer;
		
		Node node;
		for(TestRunConfig trc : testRunConfigs) {
			AggregationRequest ar = trc.createAggregationRequest();
			for(AbstractInput ai : ar.getAllInputs()) {
				if(ai instanceof NonConstantInput) {
					NonConstantInput nci = (NonConstantInput) ai;
					if(nci.getServiceURL() != null) {
						LOGGER.info("Replacing uri: " + nci.getServiceURL());
						uriReplacer = new UriReplacer(nci.getServiceURL());
						String hostId = uriReplacer.getHostId();
						if(hostId != null) {
							node = getNamedNode(uriReplacer.getHostId());
							String uri = uriReplacer.replaceHostname(node.getPublicHostname());
							nci.setServiceURL(uri);
							LOGGER.info("Replaced uri: " + nci.getServiceURL());
						}
					}
					if(nci.getServiceEPR() != null) {
						String epr = XMLUtil.getInstance().toString(nci.getServiceEPR());
						uriReplacer = new UriReplacer(epr);
						String hostId = uriReplacer.getHostId();
						if(hostId != null) {
							node = getNamedNode(uriReplacer.getHostId());
							String uri = uriReplacer.replaceHostname(node.getPublicHostname());
							nci.setServiceURL(uri);
							LOGGER.info("Replaced uri: " + nci.getServiceURL());
						}
					}
				}
			}
			trc.request = XMLUtil.getInstance().toElement(ar);
		}
	}
	
	private Node nextNode() {
		last++;
		if(last >= nodes.size())
			last = 0;
		
		return nodes.get(last);
	}
	
	/* (non-Javadoc)
	 * @see at.ac.tuwien.infosys.aggr.testbed.DeploymentStrategy#deployService(java.util.List)
	 */
	@Override
	public void deployServices(List<DeployServiceRequest> serviceRequests) {
		UriReplacer replacer;
		Node node = null;
		String finalUri;
		String hostId;
		for(DeployServiceRequest request : serviceRequests) {
			try {
				replacer = new UriReplacer(request.getUri());
				hostId = replacer.getHostId();
				
				if(replacer.hasFixedHostname()) {
					// determine node with fixed url
					boolean found = false;
					for(int i = 0; i < nodes.size() + 1; i ++) {
						URL url = new URL(request.getUri());
						node = nextNode();
						if(node.getPublicHostname().equals(url.getHost())) {
							found = true;
							break;
						}
					}
					if(!found) {
						throw new RuntimeException("Could not find host with URL " + request.getUri());
					}
				} else if(hostId.equals("")) {
					// if no HostId present in the Requests URI
					// use roundrobin to determine node to be used for deployment
					node = nextNode();
				} else {
					// if HostId is present, use it to determine Node to use
					node = getNamedNode(replacer.getHostId());
				}
				// and dont forget to replace the hostname placeholder in the request uri
				finalUri = replacer.replaceHostname(node.getPublicHostname());
				request.setUri(finalUri);
				
				// finally deploy it
				node.deployService(request);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		
		try {
			replaceUrisInTestruns();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
