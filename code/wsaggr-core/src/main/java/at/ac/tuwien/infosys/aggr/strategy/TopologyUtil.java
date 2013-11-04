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
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;

import at.ac.tuwien.infosys.aggr.node.AggregatorNode;
import at.ac.tuwien.infosys.aggr.node.Registry;
import at.ac.tuwien.infosys.aggr.proxy.AggregatorNodeProxy;
import at.ac.tuwien.infosys.aggr.proxy.RegistryProxy;
import at.ac.tuwien.infosys.util.Util;

public class TopologyUtil {

	static Logger logger = Util.getLogger();

	protected static int getTreeBranchFactorFromTypeString(String type) {
		if(type.startsWith("tree(")) {
			type = type.substring("tree(".length());
			type = type.substring(0, type.indexOf(","));
			return Integer.parseInt(type.trim());
		}
		return 0;
	}
	protected static int getTreeHeightFromTypeString(String type) {
		if(type.startsWith("tree(")) {
			type = type.substring("tree(".length());
			type = type.substring(type.indexOf(",") + 1);
			type = type.substring(0, type.indexOf(")"));
			return Integer.parseInt(type.trim());
		}
		return 0;
	}
	protected static int getNumServicesPerAggregator(int numAggregators, int totalServices) {
		int i = (int)Math.ceil(((double)totalServices)/(double)numAggregators);
		return i;
	}
	protected static int getNumNodesInTree(int numChildren, int treeHeight) {
		if(treeHeight <= 0)
			return 1;
		return (int)Math.pow(numChildren, treeHeight) + getNumNodesInTree(numChildren, treeHeight - 1);
	}

	public static Set<AggregatorNode> getActiveAggregators(List<Topology> tops) {
		Set<AggregatorNode> result = new HashSet<AggregatorNode>();
		for(Topology t : tops) {
			result.addAll(t.getAllAggregators());
		}
		return result;
	}
	
	public static int getNumActiveAggregators(List<Topology> tops) {
		return getActiveAggregators(tops).size();
	}

	public static Map<AggregatorNode,Integer> getTopologiesPerAggregator(List<Topology> tops) {
		Map<AggregatorNode,Integer> result = new HashMap<AggregatorNode, Integer>();
		for(Topology t : tops) {
			for(AggregatorNode a : t.getAllAggregators()) {
				if(!result.containsKey(a)) {
					result.put(a, 0);
				}
				result.put(a, result.get(a) + 1);
			}
		}
		return result;
	}
	public static Map<AggregatorNode,Integer> getInputsPerAggregator(List<Topology> tops) {
		Map<AggregatorNode,Integer> result = new HashMap<AggregatorNode, Integer>();
		for(Topology t : tops) {
			for(AggregatorNode a : t.getAllAggregators()) {
				if(!result.containsKey(a)) {
					result.put(a, 0);
				}
				result.put(a, result.get(a) + t.getAllRequests(a).size());
			}
		}
		return result;
	}
	
	public static List<Topology> collectAllTopologies() throws Exception {
		RegistryProxy.resetCache();
		Map<String,Topology> tops = new HashMap<String, Topology>();
		for(AggregatorNode n : Registry.getRegistryProxy().getAggregatorNodes()) {
			try {
				AggregatorNodeProxy aggr = new AggregatorNodeProxy(n);
				for(Topology t : aggr.getTopologies()) {
					if(t.getMasterAggregator().equals(n)) {
						tops.put(t.getTopologyID(), t);
					}
				}
			} catch (Exception e) {
				//logger.warn("Cannot collect topologies from aggregator: " + n.getEPR().getAddress());
			}
		}
		return new LinkedList<Topology>(tops.values());
	}
	
	
}
