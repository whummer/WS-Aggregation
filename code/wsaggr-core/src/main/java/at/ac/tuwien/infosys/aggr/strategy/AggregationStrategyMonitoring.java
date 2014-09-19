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

import io.hummer.util.Util;
import io.hummer.util.ws.AbstractNode;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.xml.bind.annotation.XmlRootElement;

import org.apache.log4j.Logger;

import at.ac.tuwien.infosys.aggr.monitor.DataServiceMonitor;
import at.ac.tuwien.infosys.aggr.node.AggregatorNode;
import at.ac.tuwien.infosys.aggr.request.AbstractInput;
import at.ac.tuwien.infosys.aggr.request.AggregationRequest;
import at.ac.tuwien.infosys.aggr.request.RequestInput;

@XmlRootElement(name="strategy")
public class AggregationStrategyMonitoring extends AggregationStrategy {

	private static final Logger logger = Util.getLogger(AggregationStrategyMonitoring.class);
	
	private AggregationStrategyFixed helper;
	private DataServiceMonitor monitor;
	
	public AggregationStrategyMonitoring(AbstractNode owner) {
		super(owner);
	}
	
	@Deprecated
	public AggregationStrategyMonitoring() { }
	
	@Override
	public void suggestMasterAggregator(String topologyID, AggregationRequest request, 
			List<AggregatorNode> masterSuggestions) throws Exception {
		masterSuggestions.add(getRandomAggregatorNode());
	}
	
	@Override
	public void generateRequests(String topologyID, List<AbstractInput> inInputs, Map<AbstractNode, List<RequestInput>> outRequests, AggregationRequest originalRequest) throws Exception {
		if(topologyID != null) {
			AggregatorNode a = (AggregatorNode)owner;
			List<String> tempTopologyIDs = a.getStrategyChain().tempTopologyIDs;
			synchronized (tempTopologyIDs) {
				boolean exists = tempTopologyIDs.remove(topologyID);
				if(exists) {
					inInputs.clear();
					logger.debug("Monitoring: generateRequests.. removing temporary inputs list.");
				}
			}
		}
	}

	@Override
	public String createTopology(String type, AggregationRequest request) throws Exception {

		getHelper();
		getMonitor();

		if(request.getRequestID() == null)
			request.setRequestID(UUID.randomUUID().toString());

		if(request.getTopologyID() == null)
			request.setTopologyID(UUID.randomUUID().toString());

		if(!(owner instanceof AggregatorNode)) 
			return null;

		//final String topologyID = request.getRequestID();
		final String topologyID = request.getTopologyID();

		if(logger.isDebugEnabled()) logger.debug("Initializing Event Coordinator.");

		((AggregatorNode)owner).initializeEventCoordinator(request, type, topologyID, true);

		return topologyID;
	}
	
	
	private AggregationStrategyFixed getHelper() {
		if(helper == null)
			helper = new AggregationStrategyFixed(owner);
		return helper;
	}

	private DataServiceMonitor getMonitor() {
		if(monitor == null) {
			monitor = ((AggregatorNode)owner).getMonitor();
		}
		return monitor;
	}

	@Override
	public boolean updateTopology(String topologyID, Object updates) throws Exception {
		return true;
	}

	@Override
	public boolean doDebugQueries() {
		// we need to gather debug information during query execution 
		// in order to be able to build the monitoring model in this 
		// strategy implementation...
		return true;
	}
	
}
