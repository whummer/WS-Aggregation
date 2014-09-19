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
package at.ac.tuwien.infosys.aggr.flow;

import io.hummer.util.Util;
import io.hummer.util.ws.request.InvocationResult;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import org.apache.log4j.Logger;
import org.w3c.dom.Element;

import at.ac.tuwien.infosys.aggr.flow.FlowNode.DependencyUpdatedInfo;
import at.ac.tuwien.infosys.aggr.request.AbstractInput;
import at.ac.tuwien.infosys.aggr.request.AggregationRequest;
import at.ac.tuwien.infosys.aggr.request.ConstantInput;
import at.ac.tuwien.infosys.aggr.request.EventingInput;
import at.ac.tuwien.infosys.aggr.request.RequestInput;
import at.ac.tuwien.infosys.aggr.request.WAQLQuery;
import at.ac.tuwien.infosys.aggr.request.WAQLQuery.PreparationQuery;
import at.ac.tuwien.infosys.aggr.xml.XPathProcessor;

public class FlowManager {

	private static Logger logger = Util.getLogger(FlowManager.class);
	
	private static final int MAX_CACHE_SIZE = 10; // TODO: make configurable!?
	
	private Util util = new Util();
	private List<FlowNode> nodes;
	private int maxResultCacheSizePerFlowNode = MAX_CACHE_SIZE;
	
	public static class InputDataDependency {
		public AbstractInput from;
		public AbstractInput to;
		public String xpath;

		public static List<InputDataDependency> filterByProvider(List<InputDataDependency> list, AbstractInput provider) {
			List<InputDataDependency> result = new LinkedList<InputDataDependency>();
			for(InputDataDependency d : list)
				if(d.from.equals(provider))
					result.add(d);
			return result;
		}
	}
	public FlowManager(AggregationRequest request) {
		this(request, MAX_CACHE_SIZE);
	}

	public FlowManager(AggregationRequest request, int maxResultCacheSizePerFlowNode) {
		if(maxResultCacheSizePerFlowNode > 0) {
			this.maxResultCacheSizePerFlowNode = maxResultCacheSizePerFlowNode;
		} else if(maxResultCacheSizePerFlowNode < 0) {
			this.maxResultCacheSizePerFlowNode = Integer.MAX_VALUE;
		}
		this.nodes = buildNodeList(request);
		for(FlowNode n : nodes) {
			try {
				if(n.getOriginalInput() instanceof ConstantInput) {
					Object content = n.getOriginalInput().getTheContent();
					if(content == null) {
						throw new RuntimeException("Constant query input (ID " + n.getOriginalInput().getExternalID() + ") may not be NULL.");
					}
					if(content instanceof String) {
						try {
							content = util.xml.toElement((String)content);
						} catch (Exception e) { }
					}
					InvocationResult data = new InvocationResult(content);
					update(data, n.getOriginalInput());
				}
			} catch (Exception e) {
				logger.info("Unable to initialize FlowManager for request: " + request, e);
				throw new RuntimeException(e);
			}
		}
	}
	
	public List<AbstractInput> filterInputsWithoutDependencies(List<AbstractInput> someInputs, 
			boolean insertOnlyNewResults, Class<?> ... instanceOf) throws Exception {
		List<FlowNode> someNodes = new LinkedList<FlowNode>();
		if(someInputs == null) {
			return filterInputsWithoutDependencies(nodes, insertOnlyNewResults);
		} else {
			for(AbstractInput i : someInputs) {
				FlowNode n = getFlowNodeByInput(i);
				boolean doAdd = true;
				for(Class<?> clazz : instanceOf) {
					if(!clazz.isAssignableFrom(i.getClass()))
						doAdd = false;
				}
				if(doAdd) {
					someNodes.add(n);
				}
			}
		}
		return filterInputsWithoutDependencies(someNodes, insertOnlyNewResults);
	}
	public List<AbstractInput> filterInputsWithoutDependencies(boolean insertOnlyNewResults) throws Exception {
		return filterInputsWithoutDependencies(nodes, insertOnlyNewResults);
	}
	private List<AbstractInput> filterInputsWithoutDependencies(List<FlowNode> someNodes, boolean insertOnlyNewResults) throws Exception {
		List<AbstractInput> result = new LinkedList<AbstractInput>();
		for(FlowNode n : nodes) {
			if(!n.hasDependencies()) {
				result.addAll(n.getInputsWithInjectedValues(insertOnlyNewResults));
			}
		}
		return result;
	}
	

	public boolean isIndependent(AbstractInput in) {
		return !getFlowNodeByInput(in).hasDependencies();
	}

	private FlowNode getFlowNodeByInput(AbstractInput i) {
		for(FlowNode n : nodes) {
			if(n.getOriginalInput().getExternalID() != null && n.getOriginalInput().getExternalID().equals(i.getExternalID()))
				return n;
		}
		return null;
	}

	public List<AbstractInput> getAllInputsWithInjectedValues(AbstractInput in) throws Exception {
		return getInputsWithInjectedValues(in, false);
	}
	public List<AbstractInput> getNewInputsWithInjectedValues(AbstractInput in) throws Exception {
		return getInputsWithInjectedValues(in, true);
	}
	private List<AbstractInput> getInputsWithInjectedValues(AbstractInput in, boolean onlyNew) throws Exception {
		FlowNode n = getFlowNodeByInput(in);
		if(n == null) return Arrays.asList(in);
		List<AbstractInput> list = n.getInputsWithInjectedValues(onlyNew);
		return list;
	}


	public List<DependencyUpdatedInfo> update(InvocationResult data, AbstractInput fromInput) throws Exception {
		List<DependencyUpdatedInfo> result = new LinkedList<DependencyUpdatedInfo>();

		//System.out.println("Update dependencies: " + Util.toString(data.getResultAsElement()) + " - from input: " + fromInput);
		if(logger.isDebugEnabled()) logger.debug("Update dependencies: " + util.xml.toString(data.getResultAsElement()) + " - from input: " + fromInput);

		if(fromInput == null) {
			logger.warn("Trying to update dependencies, but fromInput variable is null. Update data: " + util.xml.toString(data.getResultAsElement()));
			return result;
		}

		FlowNode n = getFlowNodeByInput(fromInput);
		if(n == null)
			throw new Exception("No request found with ID '" + fromInput.getExternalID() + "'");
		
		n.addReceivedResult(data);
		
		List<DataFlow> matchingDeps = getMatchingDataDependencies(nodes, n, data);
		//System.out.println("Data flows matching " + Util.toString(Util.cloneCanonical(data.getResultAsElement())) + ": " + matchingDeps);
		if(logger.isDebugEnabled()) logger.debug("Data flows matching " + util.xml.toString(util.xml.cloneCanonical(data.getResultAsElement())) + ": " + matchingDeps);
		for(DataFlow d : matchingDeps) {
			if(!d.getProvidedBy().contains(n))
				d.getProvidedBy().add(n);
		}
		for(FlowNode node : nodes) {
			try {
				List<DependencyUpdatedInfo> temp = node.insertDataIfMatchesDependency(data, n);
				result.addAll(temp);
			} catch (Throwable t) {
				logger.error("Error updating dependencies: ", t);
			}
		}
		
		return result;
	}

	private List<DataFlow> getMatchingDataDependencies(List<FlowNode> nodes, 
			FlowNode nodeWithData, InvocationResult theData) throws Exception {
		List<DataFlow> result = new LinkedList<DataFlow>();
		for(FlowNode n1 : nodes) {
			for(DataFlow d : n1.getDependencies()) {
				/** actually, I believe we should allow nodes to provide data to themselves,
				 * so we do NOT check whether (n1 != nodeWithData) .. */
				try {
				InvocationResult invResult = theData;
					if(d.isHeader()) {
						String key = d.getHeaderName();
						List<String> headerValues = invResult.getHeader(key);
						if(headerValues != null) {
							result.add(d);
						}
					} else {
						if(!d.getProvidedBy().contains(nodeWithData)) {
							Element data = invResult.getResultAsElement();
							data = util.ws.getFirstChildIfSOAPBody(data);
							List<?> matches = null;
							synchronized (XPathProcessor.class) {
								matches = XPathProcessor.evaluateAsList(d.getXPathQuery(), data);								
							}
							
							if(matches.size() > 0) {
								result.add(d);
							}
						}
					}
				} catch (Exception e) {
					logger.warn("Unable to determine data dependencies.", e);
				}
			}
		}
		return result;
	}
	
	private List<FlowNode> buildNodeList(AggregationRequest request) {
		return buildNodeList(request.getAllInputs(), request.getQueries());
	}
	private List<FlowNode> buildNodeList(List<AbstractInput> inputs, WAQLQuery queries) {
		List<FlowNode> nodes = new LinkedList<FlowNode>();
		for(AbstractInput input : inputs) {
			FlowNode n = null;
			if(input instanceof RequestInput)
				n = new FlowNode((RequestInput)input, maxResultCacheSizePerFlowNode);
			else if(input instanceof ConstantInput)
				n = new FlowNode((ConstantInput)input, maxResultCacheSizePerFlowNode);
			else if(input instanceof EventingInput)
				n = new FlowNode((EventingInput)input, maxResultCacheSizePerFlowNode);
			else 
				throw new RuntimeException("Unexpected input type: " + input);
			
			if(queries != null) {
				List<PreparationQuery> prepQueries = new LinkedList<PreparationQuery>(queries.getPreparationQueries());
				for(int i = 0; i < prepQueries.size(); i ++) {
					PreparationQuery q = prepQueries.get(i);
					if(q.isForInput(input.getExternalID())) {
						n.addPreparationQuery(q);
						prepQueries.remove(i--);
					}
				}
			}
			nodes.add(n);
		}
		logger.debug("nodes: " + nodes);
		return nodes;
	}

	
}
