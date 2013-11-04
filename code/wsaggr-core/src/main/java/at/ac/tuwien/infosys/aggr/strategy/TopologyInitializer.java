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

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.apache.log4j.Logger;

import at.ac.tuwien.infosys.aggr.flow.FlowManager.InputDataDependency;
import at.ac.tuwien.infosys.ws.AbstractNode;
import at.ac.tuwien.infosys.aggr.node.AggregatorNode;
import at.ac.tuwien.infosys.aggr.node.DataServiceNode;
import at.ac.tuwien.infosys.aggr.request.AbstractInput;
import at.ac.tuwien.infosys.aggr.request.AggregationRequest;
import at.ac.tuwien.infosys.aggr.request.InputTargetExtractor;
import at.ac.tuwien.infosys.aggr.request.NonConstantInput;
import at.ac.tuwien.infosys.aggr.request.RequestInput;
import at.ac.tuwien.infosys.util.Util;

/** 
 * This class makes the initial assignment of request inputs to responsible
 * aggregator nodes.
 * 
 * TODO: initially, the idea was to calculate an optimal topology ("optimal" in the
 * sense of optimizing global resource usage) in this
 * class, starting from an initial topology and exploring the search space of 
 * better topologies. However, the topology optimization code has been moved to 
 * {@link TopologyOptimizerVNS}. Hence, the code in this class is more complex
 * than it actually should be.
 * 
 * @author Waldemar Hummer
 */
public class TopologyInitializer implements Runnable {

	private static final Logger logger = Util.getLogger(TopologyInitializer.class);

	private List<AggregatorNode> aggregators = new ArrayList<AggregatorNode>();
	private List<RequestInput> requests = new ArrayList<RequestInput>();
	private AggregatorNode fixedMasterAggregator;
	private TopologySolution bestSolution;
	private boolean[] requestToAggregatorFixed;
	private List<Integer>[] requestToRequestDependencies;
	
	@SuppressWarnings("all")
	public Topology createTopology(String type, Map<AbstractNode,List<AggregationRequest>> fixedMappings, 
			AggregationRequest request, List<AggregatorNode> allAggregators, List<NonConstantInput> requests, 
			List<InputDataDependency> depsReceiverToProviders, AggregatorNode owner) throws Exception {


		Topology topology = new Topology();
		logger.debug("Calculating initial topology...");

		// we assume that the creator of a topology is also the master (root node)
		fixedMasterAggregator = owner;
		
		int branchFactorOfAggregatorTree = TopologyUtil.getTreeBranchFactorFromTypeString(type);
		int treeHeight = TopologyUtil.getTreeHeightFromTypeString(type);
		int numAggregatorNodes = TopologyUtil.getNumNodesInTree(branchFactorOfAggregatorTree, treeHeight);
		this.aggregators = new LinkedList<AggregatorNode>(allAggregators);
		Collections.shuffle(this.aggregators);
		while(this.aggregators.size() > numAggregatorNodes) {
			this.aggregators.remove(0);
		}
		if(!this.aggregators.contains(fixedMasterAggregator)) {
			if(!this.aggregators.isEmpty())
				this.aggregators.remove(0);
			this.aggregators.add(fixedMasterAggregator);
		}
		
		for(AbstractNode node : fixedMappings.keySet()) {
			AggregatorNode aggr = null;
			DataServiceNode data = null;
			if(node instanceof AggregatorNode) {
				aggr = (AggregatorNode)node;
				for(AggregationRequest req : fixedMappings.get(node)) {
					List<AbstractInput> inputs = req.getAllInputs();
					AbstractInput in = inputs.get(0);
					if(in instanceof RequestInput) {
						AbstractNode sourceNode = InputTargetExtractor.extractDataSourceNode((RequestInput)in,owner);
						if(sourceNode instanceof DataServiceNode) {
							data = (DataServiceNode)sourceNode;
							topology.getTargetServiceRequests(aggr, data).add(req);
						} else
							throw new Exception("Composite Aggregations not implemented yet"); // TODO: implement "composite aggregations"..
					}
				}
			} else if(node instanceof DataServiceNode) {
				data = (DataServiceNode)node;
				aggr = (AggregatorNode)owner;
				for(AggregationRequest req : fixedMappings.get(node))
					topology.getTargetServiceRequests(aggr, data).add(req);
			}
		}
		requestToRequestDependencies = (List<Integer>[])Array.newInstance(new ArrayList<Integer>().getClass(), requests.size());
		for(int i = 0; i < requestToRequestDependencies.length; i ++) {
			requestToRequestDependencies[i] = new ArrayList<Integer>();
		}

		for(InputDataDependency dep : depsReceiverToProviders) {
			AbstractInput receiver = dep.to;
			AbstractInput provider = dep.from;
			int from = requests.indexOf(provider);
			int to = requests.indexOf(receiver);
			requestToRequestDependencies[to].add(from);
		}
		
		TopologySolution initial = new TopologySolution();
		initial.aggregatorToAggregators = new int[numAggregatorNodes][branchFactorOfAggregatorTree];
		initial.inputToAggregator = new int[requests.size()];
		requestToAggregatorFixed = new boolean[requests.size()];
		
		// build initial solution
		buildTopology(initial, branchFactorOfAggregatorTree, treeHeight);
		assignRequestsToAggregators(initial, fixedMappings);
		
		bestSolution = initial;
		
		for(int i = 0; i < bestSolution.aggregatorToAggregators.length; i ++) {
			for(int j = 0; j < bestSolution.aggregatorToAggregators[i].length; j ++) {
				int index = bestSolution.aggregatorToAggregators[i][j];
				if(index > 0) {
					AggregatorNode from = aggregators.get(i);
					AggregatorNode to = aggregators.get(index - 1);
					topology.addPartner(from, to);
				}
			}
		}
		for(int i = 0; i < bestSolution.inputToAggregator.length; i ++) {
			int index = bestSolution.inputToAggregator[i];
			AggregatorNode aggr = aggregators.get(index);
			NonConstantInput input = requests.get(i);
			DataServiceNode node = (DataServiceNode)InputTargetExtractor.extractDataSourceNode(input);
			AggregationRequest req = new AggregationRequest(request);
			req.getQueries().setPreparationQueries(request.getMatchingQueries(input));
			req.getQueries().setQuery(null);
			req.getQueries().setIntermediateQuery(null);
			req.getInputs().clearInputs();
			req.getInputs().addInput(input);
			topology.addTargetServiceRequest(aggr, node, req);
			
		}
		
		if(logger.isDebugEnabled()) logger.debug("Computed optimized topology: " + topology);
		
		return topology;
	}
	
	/**
	 * Start the optimization algorithm.
	 */
	public void run() {
		
		for(int i = 0; i < 1000; i++) {
			
		}
		
	}
	
	private void buildTopology(TopologySolution s, int branchFactor, int treeHeight) {
		List<AggregatorNode> aCopy = new LinkedList<AggregatorNode>(aggregators);
		s.clearTopology();
		List<AggregatorNode> queue = new LinkedList<AggregatorNode>();
		
		AggregatorNode master = fixedMasterAggregator;
		if(master == null)
			master = getRandomAggregator(aCopy);
		aCopy.remove(master);
		queue.add(master);
		
		while(!queue.isEmpty()) {

			if(logger.isDebugEnabled()) logger.debug(queue.toString() + " - " + aggregators);
			
			AggregatorNode parent = queue.remove(0);
			
			for(int i = 0; i < branchFactor; i ++) {
				AggregatorNode n = removeRandomAggregator(aCopy);
				if(n == null)
					break;
				queue.add(n);
				int a1 = aggregators.indexOf(parent);
				if(a1 < 0) {
					logger.error("Aggregator " + parent.getEPR() + " not found in list of aggregators: ");
					String list = "";
					for(AggregatorNode a : aggregators) {
						list += a.getEPR() + " , ";
					}
					logger.error(list);
				}
				int a2 = aggregators.indexOf(n);
				for(int j = 0; j < s.aggregatorToAggregators[a1].length; j ++) {
					if(s.aggregatorToAggregators[a1][j] <= 0) {
						s.aggregatorToAggregators[a1][j] = a2 + 1;
						break;
					}
				}
			}
		}
	}
	
	private void assignRequestsToAggregators(TopologySolution s, Map<AbstractNode,List<AggregationRequest>> fixedInputs) {
		for(AbstractNode n : fixedInputs.keySet()) {
			for(AggregationRequest r : fixedInputs.get(n)) {
				AbstractInput i = r.getAllInputs().get(0);
				if(!(i instanceof RequestInput))
					continue;
				RequestInput theInput = (RequestInput)i;
				int input = requests.indexOf(theInput);
				int aggr = aggregators.indexOf(n);
				if(aggr < 0)
					throw new RuntimeException("Expected aggregator node, got: " + n.getClass() + " - " + n);
				s.inputToAggregator[input] = aggr;
				requestToAggregatorFixed[input] = true;
			}
		}
		for(int i = 0; i < s.inputToAggregator.length; i ++) {
			if(!requestToAggregatorFixed[i]) {
				int index = new Random().nextInt(aggregators.size());
				s.inputToAggregator[i] = index;
			}
		}
	}

	private AggregatorNode removeRandomAggregator(List<AggregatorNode> aggrs) {
		AggregatorNode n = getRandomAggregator(aggrs);
		if(n == null)
			return null;
		aggrs.remove(n);
		return n;
	}
	private AggregatorNode getRandomAggregator(List<AggregatorNode> aggrs) {
		if(aggrs.size() <= 0)
			return null;
		int index = (int)(Math.random()*aggrs.size());
		return aggrs.get(index);
	}
}
