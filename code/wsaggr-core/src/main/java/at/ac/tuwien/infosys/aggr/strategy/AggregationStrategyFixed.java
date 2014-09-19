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
package at.ac.tuwien.infosys.aggr.strategy;

import io.hummer.util.Configuration;
import io.hummer.util.Util;
import io.hummer.util.ws.AbstractNode;
import io.hummer.util.ws.EndpointReference;
import io.hummer.util.ws.WebServiceClient;
import io.hummer.util.ws.request.InvocationResult;

import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import javax.xml.bind.annotation.XmlRootElement;

import org.w3c.dom.Element;

import at.ac.tuwien.infosys.aggr.node.AggregatorNode;
import at.ac.tuwien.infosys.aggr.node.DataServiceNode;
import at.ac.tuwien.infosys.aggr.node.Registry;
import at.ac.tuwien.infosys.aggr.request.AbstractInput;
import at.ac.tuwien.infosys.aggr.request.AggregationRequest;
import at.ac.tuwien.infosys.aggr.request.ConstantInput;
import at.ac.tuwien.infosys.aggr.request.RequestInput;
import at.ac.tuwien.infosys.aggr.request.RequestInput.TargetType;

@XmlRootElement(name="strategy")
public class AggregationStrategyFixed extends AggregationStrategy {

	private static final long CLEAN_INTERVAL_MS = 1000*60*3;
	private static final long LEASE_TIME_MS = 1000*60*60;

	protected Map<String, Topology> topologies = new HashMap<String, Topology>();
	protected Util util = new Util();
	private long nextCleanTime = System.currentTimeMillis() + CLEAN_INTERVAL_MS;
	private Map<String, AggregatorNode> masters = new HashMap<String, AggregatorNode>();
	
	public AggregationStrategyFixed(AbstractNode owner) {
		super(owner);
	}

	/**
	 * default constructor needed by JAXB, should not be used by the programmer
	 */
	@Deprecated
	public AggregationStrategyFixed() { }
	
	@Override
	public void suggestMasterAggregator(String topologyID, AggregationRequest request, List<AggregatorNode> masterSuggestions) throws Exception {
		if(topologyID == null || topologyID.trim().equals(""))
			return;
		
		AggregatorNode master = null;
		synchronized (masters) {
			master = masters.get(topologyID);
		}
		if(master == null)
			throw new IllegalArgumentException("Illegal topology ID specified.");
		masterSuggestions.add(master);
	}
	
	@Override
	public boolean updateTopology(String topologyID, Object updates)
			throws Exception {
		synchronized (topologies) {
			if(topologies.get(topologyID) == null)
				topologies.put(topologyID, new Topology());
			topologies.get(topologyID).setLastUsedNow();
			boolean partnersCleared = false;
			boolean targetsCleared = false;
			if(updates instanceof Element) {
				for(Element e : util.xml.getChildElements((Element)updates)) {
					//Util.print(e);
					if(e.getTagName().contains("newChildAggregatorNode")) {
						if(!partnersCleared) {
							getPartners(topologyID).clear();
							partnersCleared = true;
						}
						EndpointReference epr = new EndpointReference(e);
						AggregatorNode n = new AggregatorNode(epr, false);
						getPartners(topologyID).add(n);
					} else if(e.getTagName().contains("newTargetNode")) {
						EndpointReference epr = new EndpointReference(e);
						if(topologies.get(topologyID) == null)
							topologies.put(topologyID, new Topology());
						if(!targetsCleared) {
							topologies.get(topologyID).clearTargetServices(epr.getFeature());
							targetsCleared = true;
						}
						try {
							DataServiceNode n = new DataServiceNode();
							n.setEPR(epr);
							getTargetServices(topologyID, epr.getFeature()).add(n);
						} catch (Exception ex) {
							logger.warn("Unable to update topology.", ex);
						}
					}
				}
			}
		}
		if(System.currentTimeMillis() > nextCleanTime) {
			nextCleanTime = System.currentTimeMillis() + CLEAN_INTERVAL_MS;
			cleanUnusedTopologies();
		}
		return true;
	}
	
	@Override
	public void generateRequests(String topologyID, List<AbstractInput> inInputs, 
			Map<AbstractNode,List<RequestInput>> outRequests, AggregationRequest originalRequest) throws Exception {
		
		Topology t = null;
		synchronized (topologies) {
			t = topologies.get(topologyID);
		}
		if(t == null)
			throw new Exception(owner + " Unknown topology ID: " + topologyID);
		t.setLastUsedNow();
		
		
		assignToAllRequests(topologyID, inInputs, outRequests);
		
		
		insertDataServiceInputs(topologyID, inInputs, outRequests);
		
	}

	@Override
	public void resetCache() throws Exception {
		synchronized (topologies) {
			topologies.clear();
		}
		util = new Util();
	}
	
	protected void assignToAllRequests(String topologyID, List<AbstractInput> inputs, Map<AbstractNode,List<RequestInput>> result) throws Exception {
		
		Topology t = null;
		synchronized (topologies) {
			t = topologies.get(topologyID);
		}
		
		List<AggregatorNode> partners = t.getPartners((AggregatorNode)owner);
		
		// extract to="ALL" inputs
		for(int i = 0; i < inputs.size(); i ++) {
			if(inputs.get(i) instanceof RequestInput) {
				RequestInput input = (RequestInput)inputs.get(i);
				input.topologyID = topologyID;
				if(input.getTo() == TargetType.ALL) {
					for(AggregatorNode partner : partners) {
						List<RequestInput> partnerInputs = result.get(partner);
						if(partnerInputs == null) {
							partnerInputs = new LinkedList<RequestInput>();
							result.put(partner, partnerInputs);
						}
						partnerInputs.add(input);
					}
					inputs.remove(i--);
					
					List<DataServiceNode> services = t.getTargetServices().get(input.getFeature());
					if(services != null) {
						for(DataServiceNode service : services) {
							RequestInput newInput = new RequestInput(input);
							newInput.setFeature(null);
							newInput.setTo(TargetType.ONE);
							newInput.setServiceEPR(service.getEPR());
							List<RequestInput> targetInputs = result.get(service);
							if(targetInputs == null) {
								targetInputs = new LinkedList<RequestInput>();
								result.put(service, targetInputs);
							}
							targetInputs.add(newInput);
						}
					}
				}
			}
		}
	}
	
	protected void insertDataServiceInputs(String topologyID, List<AbstractInput> inputs, Map<AbstractNode,List<RequestInput>> result) throws Exception {
		
		while(inputs.size() > 0) {
			AbstractInput tmp = inputs.remove(0);
			
			RequestInput in = null;
			if(tmp instanceof RequestInput)
				in = (RequestInput)tmp;
			else if(tmp instanceof ConstantInput)
				continue;
			
			DataServiceNode node = new DataServiceNode();
			if(in.getServiceEPR() != null) {
				node.setEPR(in.getServiceEPR());
			} else if(in.getServiceURL() != null) {
				node.setEPR(new EndpointReference(new URL(in.getServiceURL())));
			} else if(in.getFeature() != null) {
				if(in.getTo() == TargetType.ALL)
					throw new Exception("Input should not be to=\"ALL\" at this place.." + in);
				node = getRandomTargetService(topologyID, in.getFeature());
				if(node == null) {
					System.out.println("WARNING: to=\"ONE\" request not handled by FixedAggregationStrategy handler.. " +
							"If this is the last strategy in the chain, the overall aggregation will lack this result!");
				}
			} else {
				throw new Exception("No endpoint information in request input: " + in);
			}
			if(!result.containsKey(node))
				result.put(node, new LinkedList<RequestInput>());
			result.get(node).add(in);
		}
		
		boolean doContinue = false;
		if(!doContinue)
			return;
		
		// TODO: remove or merge!
		
		//int requestCount = 0;
		String[] features = RequestInput.getFeaturesFromInputs(inputs);
		for(String feature : features) {
			System.out.println(owner + " Feature: " + feature);
			List<DataServiceNode> featureNodes = getTargetServices(topologyID, feature);
			if(featureNodes.size() <= 0)
				continue;
			List<RequestInput> featureInputs = RequestInput.extractRequests(inputs, feature);
			int requestsPerService = 1;
			requestsPerService = (int)Math.ceil((double)featureInputs.size() / (double)featureNodes.size());
			if(requestsPerService <= 0)
				requestsPerService = 1;
			for(DataServiceNode node : featureNodes) {
				System.out.println(owner + " Node: " + node);
				for(int i = 0; i < requestsPerService && featureInputs.size() > 0; i++) {
					RequestInput input = featureInputs.get(0);
					if(input.getTo() != TargetType.ALL)
						featureInputs.remove(input);
					if(!result.containsKey(node)) {
						result.put(node, new LinkedList<RequestInput>());
					}
					result.get(node).add(input);
					//requestCount ++;
				}
			}
		}
		List<RequestInput> hardcodedTargetInputs = 
			RequestInput.extractRequestsWithoutFeature(inputs);
		for(RequestInput input : hardcodedTargetInputs) {
			DataServiceNode node = new DataServiceNode();
			node.setEPR(new EndpointReference(new URL(input.getServiceURL())));
			if(!result.containsKey(node)) {
				result.put(node, new LinkedList<RequestInput>());
			}
			result.get(node).add(input);
		}
	}

	private void cleanUnusedTopologies() {
		synchronized (topologies) {
			List<String> IDs = new LinkedList<String>(topologies.keySet());
			for(String ID: IDs) {
				if(System.currentTimeMillis() > (topologies.get(ID).getLastUsed() + LEASE_TIME_MS)) {
					topologies.remove(ID);
					System.out.println("Removed unused topology " + ID);
				}
			}
		}
	}
	
	private List<AggregatorNode> getPartners(String topologyID) {
		if(topologyID == null)
			return new ArrayList<AggregatorNode>();
		synchronized (topologies) {
			if(!topologies.containsKey(topologyID))
				topologies.put(topologyID, new Topology());
			return topologies.get(topologyID).getPartners((AggregatorNode)owner);
		}
	}
	private DataServiceNode getRandomTargetService(String topologyID, String feature) throws Exception {
		List<DataServiceNode> list = getTargetServices(topologyID, feature);
		if(list.size() <= 0)
			return null;
		return list.get((int)(list.size() * Math.random()));
	}
	private List<DataServiceNode> getTargetServices(String topologyID, String feature) throws Exception {
		if(topologyID == null)
			return Registry.getRegistryProxy().getDataServiceNodes(feature);
		synchronized (topologies) {
			if(!topologies.containsKey(topologyID))
				topologies.put(topologyID, new Topology());
			Map<String, LinkedList<DataServiceNode>> nodes = topologies.get(topologyID).getTargetServices();
			if(nodes.get(feature) == null) 
				nodes.put(feature, new LinkedList<DataServiceNode>());
			return nodes.get(feature);
		}
	}
	
	/**
	 * creates a new topology of aggregator nodes and returns the
	 * global identifier for this new topology.
	 * @param type A string specifying the type of the topology, 
	 * e.g., "tree(3,2)" for a tree with branching factor 3 and 
	 * height 2 (i.e., a total of 13 nodes).
	 * @return new topology ID
	 * @throws Exception
	 */
	@Override
	public String createTopology(String type, List<String> feature) throws Exception {
		
		String topologyID = UUID.randomUUID().toString();
		int branchFactorOfAggregatorTree = getTreeBranchFactorFromTypeString(type);
		int treeHeight = getTreeHeightFromTypeString(type);
		int numAggregatorNodes = getNumNodesInTree(branchFactorOfAggregatorTree, treeHeight);
		
		List<AggregatorNode> aggregators = 
			new LinkedList<AggregatorNode>(
					Registry.getRegistryProxy().getAggregatorNodes(numAggregatorNodes));
		for(String oneFeature : feature) {
			List<AggregatorNode> aggregatorsCopy = new LinkedList<AggregatorNode>(aggregators);
			List<DataServiceNode> businessLogicNodes =
				new LinkedList<DataServiceNode>(
						Registry.getRegistryProxy().getDataServiceNodes(oneFeature));
			
			int numBusinessServiceNodes = businessLogicNodes.size();
			int nodesPerAggregator = (int)((double)numBusinessServiceNodes / (double)numAggregatorNodes);
	
			int index = new Random(System.currentTimeMillis()).nextInt(aggregatorsCopy.size());
			index = 0; // TODO: make random again! only for testing purposes...
			AggregatorNode master = aggregatorsCopy.remove(index);
			synchronized (masters) {
				masters.put(topologyID, master);				
			}

			
			buildTree(topologyID, master, aggregatorsCopy, branchFactorOfAggregatorTree, treeHeight);
			
			int counter = 0;
			for(AggregatorNode node: aggregators) {
				counter++;
				String request = "<tns:updateTopology " +
					"xmlns:tns=\"" + Configuration.NAMESPACE + "\">" +
					"<topologyID>" + topologyID + "</topologyID>" +
					"<updates>";
				// TODO: needed?
				// node.getTargetServices(topologyID).clear();
				for(int j = 0; j < numBusinessServiceNodes; j ++) {
					if((counter < aggregators.size() && j == nodesPerAggregator) || businessLogicNodes.isEmpty())
						break;
					DataServiceNode n = businessLogicNodes.remove(0);
					EndpointReference epr = n.getEPR();
					epr.setFeature(oneFeature);
					request += epr.toString("newTargetNode");
				}
				request += "</updates></tns:updateTopology>";
				WebServiceClient client = WebServiceClient.getClient(node.getEPR());
				RequestInput input = new RequestInput(util.xml.toElement(request));
				InvocationResult r = client.invoke(input.getRequest());
				Element result = (Element)r.getResult();
				
				result.toString(); // TODO: assert result == true
			}
		}
		
		return topologyID;
	}
	

	protected void buildTree(String topologyID, AggregatorNode node, 
			List<AggregatorNode> nodes, int childCount, int height) 
			throws Exception {
		
		List<AggregatorNode> thisChildren = new ArrayList<AggregatorNode>();
		String request = "<tns:updateTopology " +
				"xmlns:tns=\"" + Configuration.NAMESPACE + "\">" +
				"<topologyID>" + topologyID + "</topologyID>" +
				"<updates>";
		for(int i = 0; i < childCount; i ++) {
			if(nodes.size() <= 0 || height <= 0)
				return;
			AggregatorNode n = nodes.remove(0);
			request += n.getEPR().toString("newChildAggregatorNode");
			thisChildren.add(n);
		}
		request += "</updates></tns:updateTopology>";
		WebServiceClient client = WebServiceClient.getClient(node.getEPR());
		RequestInput input = new RequestInput(util.xml.toElement(request));
		InvocationResult r = client.invoke(input.getRequest());
		Element result = (Element)r.getResult();

		result.toString(); // TODO: assert result == true
		
		for(AggregatorNode n : thisChildren) {
			buildTree(topologyID, n, nodes, childCount, height - 1);
		}
	}

	protected int getTreeBranchFactorFromTypeString(String type) {
		if(type.startsWith("tree(")) {
			type = type.substring("tree(".length());
			type = type.substring(0, type.indexOf(","));
			return Integer.parseInt(type.trim());
		}
		return 0;
	}
	protected int getTreeHeightFromTypeString(String type) {
		if(type.startsWith("tree(")) {
			type = type.substring("tree(".length());
			type = type.substring(type.indexOf(",") + 1);
			type = type.substring(0, type.indexOf(")"));
			return Integer.parseInt(type.trim());
		}
		return 0;
	}
	protected int getNumNodesInTree(int numChildren, int treeHeight) {
		if(treeHeight <= 0)
			return 1;
		return (int)Math.pow(numChildren, treeHeight) + getNumNodesInTree(numChildren, treeHeight - 1);
	}
}
