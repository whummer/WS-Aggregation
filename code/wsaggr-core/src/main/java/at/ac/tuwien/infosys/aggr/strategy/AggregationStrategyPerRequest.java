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

import java.net.URL;
import java.util.List;

import javax.xml.bind.annotation.XmlRootElement;

import org.w3c.dom.Element;

import at.ac.tuwien.infosys.aggr.node.AggregatorNode;
import at.ac.tuwien.infosys.aggr.request.AggregationRequest;
import at.ac.tuwien.infosys.aggr.xml.XPathProcessor;
import at.ac.tuwien.infosys.util.NotImplementedException;
import at.ac.tuwien.infosys.util.Util;
import at.ac.tuwien.infosys.ws.AbstractNode;
import at.ac.tuwien.infosys.ws.EndpointReference;

@XmlRootElement(name="strategy")
public class AggregationStrategyPerRequest extends AggregationStrategy {

	public AggregationStrategyPerRequest(AbstractNode owner) {
		super(owner);
	}

	/**
	 * default constructor needed by JAXB, should not be used by the programmer
	 */
	@Deprecated
	public AggregationStrategyPerRequest() { }
	
	@Override
	public void suggestMasterAggregator(String topologyID, AggregationRequest request, List<AggregatorNode> masterSuggestions) throws Exception {
		Util util = new Util();
		
		/** we assume that the target aggregator is specified in some <assignedAggregator..>.. 
		 * element directly in the XML representation of the aggregation request. */
		List<?> assignments = XPathProcessor.evaluateAsList("//*:assignedAggregator", util.xml.clone(util.xml.toElement(request)));
		
		//System.out.println("assignedAggregator elements in request: " + assignments + " - " + util.toString(request));
		if(assignments == null || assignments.isEmpty())
			return;
		
		AggregatorNode master = null;
		Element e = (Element)assignments.get(0);
		String content = e.getTextContent();
		if(!util.str.isEmpty(content)) {
			content = content.trim();
			if(e.hasAttribute("type")) {
				String type = e.getAttribute("type");
				if(type.equalsIgnoreCase("regex")) {
					// TODO
				}
				throw new NotImplementedException();
			} else {
				try {
					master = new AggregatorNode(new EndpointReference(new URL(content)), false);					
				} catch (Exception e2) {
					logger.warn("Illegal URL format of aggregator assigned in request: " + content);
				}
			}
		}
		
		if(master != null) {
			logger.info("Using fixed master aggregator specified directly in the request: " + master);
			masterSuggestions.add(0, master);
		}
	}
	
}
