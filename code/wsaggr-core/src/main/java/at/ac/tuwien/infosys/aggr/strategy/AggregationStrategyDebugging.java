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

import java.util.List;
import java.util.Map;

import javax.xml.bind.annotation.XmlRootElement;

import at.ac.tuwien.infosys.ws.AbstractNode;
import at.ac.tuwien.infosys.aggr.node.AggregatorNode;
import at.ac.tuwien.infosys.aggr.request.AbstractInput;
import at.ac.tuwien.infosys.aggr.request.AggregationRequest;
import at.ac.tuwien.infosys.aggr.request.RequestInput;

@XmlRootElement(name="strategy")
public class AggregationStrategyDebugging extends AggregationStrategy {

	public AggregationStrategyDebugging(AbstractNode owner) {
		super(owner);
	}

	/**
	 * default constructor needed by JAXB, should not be used by the programmer
	 */
	@Deprecated
	public AggregationStrategyDebugging() { }
	
	@Override
	public boolean doDebugQueries() {
		return true;
	}
	
	@Override
	public void suggestMasterAggregator(String topologyID, AggregationRequest request, 
			List<AggregatorNode> masterSuggestions) throws Exception {
		masterSuggestions.add(getRandomAggregatorNode());
	}
	
	@Override
	public void generateRequests(String topologyID, List<AbstractInput> inInputs,
			Map<AbstractNode, List<RequestInput>> outRequests, AggregationRequest originalRequest) throws Exception {
		// do nothing (but avoid NotImplementedException)...
	}
	
	@Override
	public boolean updateTopology(String topologyID, Object updates)
			throws Exception {
		return false;
	}
}
