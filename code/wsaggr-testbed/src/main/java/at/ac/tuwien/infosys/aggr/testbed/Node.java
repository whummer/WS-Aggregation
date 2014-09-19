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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.log4j.Logger;

import at.ac.tuwien.infosys.aggr.testbed.config.Config;
import at.ac.tuwien.infosys.aggr.testbed.messaging.DeployServiceRequest;
import at.ac.tuwien.infosys.aggr.testbed.messaging.Request;
import at.ac.tuwien.infosys.aggr.testbed.messaging.StartAggregatorRequest;
import at.ac.tuwien.infosys.aggr.testbed.messaging.StartGatewayRequest;
import at.ac.tuwien.infosys.aggr.testbed.messaging.TerminateRequest;
import io.hummer.util.Util;

import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.GetConsoleOutputRequest;
import com.amazonaws.services.ec2.model.Instance;

/**
 * A Node represents an EC2 instance with convenience methods for testbed
 * functionality like Service deployment. It's created by a NodeProvider.
 * @author Daniel Domberger
 */
public class Node {
	private static final Logger LOGGER = Util.getLogger(Node.class);
	
	private RequestSender requestSender = null;
	private Instance instance = null;
	private NodeProvider nodeProvider = null;
	
	private List<DeployServiceRequest> deployedServiceRequests = null;
	private String gatewayUri = null;
	private List<Integer> aggregatorPorts = new ArrayList<Integer>();
	
	private String nodeName;

	/**
	 * @return the nodeName
	 */
	public String getNodeName() {
		return nodeName;
	}

	/**
	 * @param nodeName the nodeName to set
	 */
	public void setNodeName(String nodeName) {
		this.nodeName = nodeName;
	}

	/**
	 * @return the gatewayUrl
	 */
	public String getGatewayUri() {
		return gatewayUri;
	}

	/**
	 * @return the aggregatorPorts
	 */
	public List<Integer> getAggregatorPorts() {
		return aggregatorPorts;
	}
	
	/**
	 * @return the deployedServiceRequests
	 */
	public List<DeployServiceRequest> getDeployedServiceRequests() {
		return deployedServiceRequests;
	}
	
	/**
	 * Constructor
	 * @param instance	The EC2 Instance this node represents
	 * @param provider	The NodeProvider that created this Node
	 */
	public Node(Instance instance, NodeProvider provider) {
		this.instance = instance;
		this.nodeProvider = provider;
		
		try {
			requestSender = new RequestSender(getPublicHostname(), 25);
		} catch (Exception e) {
			LOGGER.warn("Could not establish connection to RequestServer on " +
					getPublicHostname());
		}
	}
	
	/**
	 * Returns the public hostname of this node. Other nodes can reach this
	 * node by the public hostname as well as by private hostname.
	 * @return	the public hostname of this node
	 */
	public String getPublicHostname() {
		return getAvailableHost(instance, nodeProvider.ec2client, nodeProvider.config);
	}
	
	public static String getAvailableHost(Instance instance, AmazonEC2Client ec2client, final Config config) {
		try {
			String host = instance.getPublicDnsName();
			int port = Config.requestPort;
			new Socket(InetAddress.getByName(host), port).close();
			return instance.getPublicDnsName();
		} catch (Exception e) {
			String host = null;
			String dns = instance.getPublicDnsName();
			Pattern p1 = Pattern.compile(".*[^0-9]([0-9]+-[0-9]+-[0-9]+-[0-9]+).*");
			Matcher m1 = p1.matcher(dns);
			if(m1.find()) {
				System.out.println(m1.group(1));
				return m1.group(1).replace("-", ".");
			}
			int count = 0;
			while(host == null && ++count <= 3) {
				try {
					GetConsoleOutputRequest r = new GetConsoleOutputRequest();
					r.setInstanceId(instance.getInstanceId());
					String console = ec2client.getConsoleOutput(r).getOutput();
					console = Util.getInstance().io.readFile(
							javax.mail.internet.MimeUtility.decode(
									new ByteArrayInputStream(console.getBytes()), "base64"));
					// find the following pattern (example): "bound to 192.168.1.12"
					Pattern p = Pattern.compile(".*bound to ([0-9]+\\.[0-9]+\\.[0-9]+\\.[0-9]+).*");
					Matcher m = p.matcher(console);
					m.find();
					String ip = m.group(1);
					return ip;
				} catch (Exception e1) {
					try {
						Thread.sleep(2000);
					} catch (InterruptedException e2) {}
				}
			}
			if(instance.getPublicIpAddress() != null) {
				LOGGER.info("Public DNS host '" + instance.getPublicDnsName() + "' not available, using IP address " + instance.getPublicIpAddress());
				return instance.getPublicIpAddress();
			}
			
			throw new RuntimeException("Unable to determine host name or IP");
		}
	}
	
	/**
	 * Returns the private DNS name of this Node. Since a node can be reached
	 * by another node by public DNS name as well as private DNS name you'll
	 * usually want to use the public hostname unless there are specific
	 * firewalling requirements.
	 * @return	the private dns name of this node.
	 */
	public String getPrivateHostname() {
		return instance.getPrivateDnsName();
	}
	
	public void deployService(DeployServiceRequest request)
			throws IOException {
		
		if(deployedServiceRequests == null)
			deployedServiceRequests = new ArrayList<DeployServiceRequest>();
		
		UriReplacer uriReplacer = new UriReplacer(request.getUri());
		String uri = uriReplacer.replaceHostname(getPublicHostname());
		request.setUri(uri);
		
		LOGGER.info("(" + nodeName + ") " + "Deploying service: " + 
				request.getService().getClass().getName());
		LOGGER.info("(" + nodeName + ") " + "Deployment URI: " + uri);
		
		deployedServiceRequests.add(request);
		
		requestSender.sendRequest(request);
	}
	
	public void sendRequest(Request request) throws IOException {
		requestSender.sendRequest(request);
	}

	/**
	 * Causes the remote node's JVM to terminate and restart, thereby
	 * reloading the jar and class files, which may have changed remotely. 
	 * Since the remote script to start the JVM is wrapped in a continuous 
	 * while-loop, the node should restart immediately and become available
	 * within a few seconds..
	 */
	public void terminateForRestart() throws IOException {
		sendRequest(new TerminateRequest());
	}
	
	/**
	 * Starts a gateway on this Node
	 * @return	The uri where the gateway on this Node will be reachable
	 * @throws IOException	If communication with the Node fails
	 */
	public String startGateway() throws IOException {
		System.out.println("Sending request to start gateway..");
		sendRequest(new StartGatewayRequest());
		String host = getPublicHostname();
		String uri = "http://" + host + ":8890/gateway?wsdl";
		
		gatewayUri = uri;
		
		return uri;
	}
	
	/**
	 * Starts a gateway on this Node using the provided url
	 * @param uriReplacer	The UrlReplacer containing the url to use for the Gateway
	 * 				started on this Node. If the url contains a hostname
	 * 				replacement token, it will be replaced by the hostname of
	 * 				this Node.
	 * @return	The url that has been used to deploy the gateway (i.e. with
	 * 			replacement tokens replaced by Nodes hostname)
	 * @throws IOException	if some network error occurs
	 */
	public String startGateway(UriReplacer uriReplacer) throws IOException {
		LOGGER.info("Starting Gateway on Node (" + nodeName + "): " +
				getPublicHostname());
		
		gatewayUri = uriReplacer.replaceHostname(getPublicHostname());
		
		sendRequest(new StartGatewayRequest(gatewayUri));
		
		LOGGER.debug("(" + nodeName + ") " + "Gateway URI: " + gatewayUri);
		return gatewayUri;
	}
	
	public void startAggregator(int port, String gatewayUri)
			throws IOException {
		System.out.println("Requesting start of aggregator at " + getPublicHostname() + ":" + port);
		Request request = new StartAggregatorRequest(port, gatewayUri, getPublicHostname());
		
		if(aggregatorPorts == null) {
			aggregatorPorts = new ArrayList<Integer>();
		}
		
		aggregatorPorts.add(port);
		
		sendRequest(request);
	}
	
	/**
	 * Stopps this node
	 */
	public void stop() {
		nodeProvider.stopInstance(instance);
	}
}
