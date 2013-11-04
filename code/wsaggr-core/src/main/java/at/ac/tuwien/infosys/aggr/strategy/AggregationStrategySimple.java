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

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import at.ac.tuwien.infosys.util.Util;
import at.ac.tuwien.infosys.ws.AbstractNode;
import at.ac.tuwien.infosys.aggr.node.AggregatorNode;
import at.ac.tuwien.infosys.aggr.node.Registry;
import at.ac.tuwien.infosys.aggr.performance.GarbageCollector;
import at.ac.tuwien.infosys.aggr.request.AbstractInput;
import at.ac.tuwien.infosys.aggr.request.AggregationRequest;
import at.ac.tuwien.infosys.aggr.request.RequestInput;

@XmlRootElement(name="strategy")
public class AggregationStrategySimple extends AggregationStrategy {

	private boolean roundRobin = true;
	private boolean random = false;
	private Util util = new Util();
	
	private static final Map<AggregatorNode,Long> aggregatorToLastUsageTime = 
			new HashMap<AggregatorNode, Long>();
	private static final Set<AggregatorNode> inaccessibleAggregators = 
			new HashSet<AggregatorNode>();
	private static final int REMOVE_INACCESSIBLE_AGGREGATORS_TIMEOUT_SEC = 60*5;
	
	public AggregationStrategySimple(AbstractNode owner) {
		super(owner);
	}

	/**
	 * default constructor needed by JAXB, should not be used by the programmer
	 */
	@Deprecated
	public AggregationStrategySimple() { }
	
	@Override
	public void generateRequests(String topologyID, List<AbstractInput> inInputs,
			Map<AbstractNode, List<RequestInput>> outRequests, AggregationRequest originalRequest) throws Exception {
		
		extractRequestsThatTargetAllServices(inInputs);
		
		assignAllInputsToDataServices(inInputs, outRequests);
	}
	
	@Override
	public void handleUnreachableNode(AggregatorNode node) {
		inaccessibleAggregators.add(node);
		GarbageCollector.deleteAfter(inaccessibleAggregators, node, 
				REMOVE_INACCESSIBLE_AGGREGATORS_TIMEOUT_SEC);
	}

	@Override
	public void suggestMasterAggregator(String topologyID, AggregationRequest request, 
			List<AggregatorNode> masterSuggestions) throws Exception {
		if(isRoundRobin()) {
			List<AggregatorNode> allNodes = Registry.getRegistryProxy().getAggregatorNodes();
			allNodes.removeAll(inaccessibleAggregators);
			Set<AggregatorNode> nodes = new HashSet<AggregatorNode>(aggregatorToLastUsageTime.keySet());
			long minTime = Long.MAX_VALUE;
			AggregatorNode minNode = null;
			for(AggregatorNode a : allNodes) {
				if(!nodes.contains(a)) {
					if(util.net.isPortOpen(a)) {
						aggregatorToLastUsageTime.put(a, System.currentTimeMillis());
						masterSuggestions.add(a);
						return;
					} else {
						handleUnreachableNode(a);
					}
				} else {
					long time = aggregatorToLastUsageTime.get(a);
					if(time < minTime) {
						minTime = time;
						minNode = a;
					}
				}
			}
			if(minNode == null)
				minNode = Registry.getRegistryProxy().getRandomAggregatorNode();
			aggregatorToLastUsageTime.put(minNode, System.currentTimeMillis());
			masterSuggestions.add(minNode);
		} else if(isRandom()) {
			List<AggregatorNode> allNodes = Registry.getRegistryProxy().getAggregatorNodes();
			allNodes.removeAll(inaccessibleAggregators);
			if(allNodes.size() > 0) {
				AggregatorNode rnd = allNodes.get((int)(Math.random() * allNodes.size()));
				masterSuggestions.add(rnd);
			}
		} else {
			super.suggestMasterAggregator(topologyID, request, masterSuggestions);
		}
	}
	
	@Override
	public void resetCache() throws Exception {
	}
	
	@XmlElement
	public boolean isRandom() {
		return random;
	}
	public void setRandom(boolean random) {
		this.random = random;
	}
	@XmlElement
	public boolean isRoundRobin() {
		return roundRobin;
	}
	public void setRoundRobin(boolean roundRobin) {
		this.roundRobin = roundRobin;
	}
}
