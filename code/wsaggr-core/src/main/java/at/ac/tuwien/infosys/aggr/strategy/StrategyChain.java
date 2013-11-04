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

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.XmlTransient;

import org.w3c.dom.Element;

import at.ac.tuwien.infosys.ws.AbstractNode;
import at.ac.tuwien.infosys.aggr.node.AggregatorNode;
import at.ac.tuwien.infosys.aggr.request.AbstractInput;
import at.ac.tuwien.infosys.aggr.request.AggregationRequest;
import at.ac.tuwien.infosys.aggr.request.RequestInput;
import at.ac.tuwien.infosys.util.NotImplementedException;
import at.ac.tuwien.infosys.util.Util;
import at.ac.tuwien.infosys.util.xml.XMLUtil;

@XmlSeeAlso({AggregationStrategy.class})
@XmlRootElement(name="StrategyChain")
public class StrategyChain extends AggregationStrategy {

	@XmlElement(name="strategy")
	private List<AggregationStrategy> strategy = new LinkedList<AggregationStrategy>();
	@XmlTransient
	public final List<String> tempTopologyIDs = new LinkedList<String>();
	
	public StrategyChain() { super(null); }
	public StrategyChain(AbstractNode owner) {
		super(owner);
	}
	
	@XmlTransient
	public List<AggregationStrategy> getStrategy() {
		if(strategy == null) {
			strategy = new LinkedList<AggregationStrategy>();
		}
		return strategy;
	}
	public void setStrategy(List<AggregationStrategy> strategy) {
		this.strategy = strategy;
	}

	@Override
	public void generateRequests(String topologyID, List<AbstractInput> inInputs,
			Map<AbstractNode, List<RequestInput>> outRequests, AggregationRequest originalRequest) throws Exception {
		for(AggregationStrategy s : strategy) {
			try {
				s.generateRequests(topologyID, inInputs, outRequests, originalRequest);				
			} catch (NotImplementedException e) {
				/* swallow */
			}
		}
	}
	
	public String createTopology(String type, AggregationRequest request)
			throws Exception {
		for(AggregationStrategy s : strategy) {
			try {
				String id = s.createTopology(type, request);
				if(id != null)
					return id;
			} catch (NotImplementedException e) { }
		}
		throw new Exception("No strategy in the strategy chain (" + strategy + ") was able to create the requested topology.");
	}
	
	public String toString(String elementName) throws Exception {
		XMLUtil util = new XMLUtil();
		Element el = util.toElement(this);
		Element result = util.toElement("<" + elementName + "/>");
		for(Element c : util.getChildElements(el)) {
			util.appendChild(result, c);
		}
		return util.toString(result);
	}
	
	public AggregatorNode selectMasterAggregator(String topologyID, AggregationRequest request) throws Exception {
		List<AggregatorNode> masterSuggestions = new LinkedList<AggregatorNode>();
		for(AggregationStrategy s : strategy) {
			try {
				s.suggestMasterAggregator(topologyID, request, masterSuggestions);
			} catch (NotImplementedException e) { }
		}
		//System.out.println("masterSuggestions.size() " + masterSuggestions.size() + " - " + strategy + " - " + at.ac.tuwien.infosys.aggr.node.Registry.getRegistryProxy().getAggregatorNodes());
		if(masterSuggestions.size() <= 0)
			return null;
		return masterSuggestions.get(0);
	}

	public boolean updateTopology(String topologyID, Object updates) throws Exception {
		int handled = 0;
		for(AggregationStrategy s : strategy) {
			try {
				boolean updated = s.updateTopology(topologyID, updates);
				if(updated)
					handled ++;
			} catch (NotImplementedException e) { }
		}
		if(handled <= 0)
			throw new Exception("'Update Topology' not implemented by any strategy in the strategy chain.");
		return true;
	}
	
	public void handleUnreachableNode(AggregatorNode master) {
		int handled = 0;
		for(AggregationStrategy s : strategy) {
			try {
				s.handleUnreachableNode(master);
				handled ++;
			} catch (NotImplementedException e) { }
		}
		if(handled <= 0)
			logger.warn("'Handle Unreachable Node' not implemented by any strategy in the strategy chain.");
	}
	
	public static synchronized StrategyChain loadDefault(AbstractNode owner) {
		try {
			Util util = new Util();
			String config = util.io.readFile(StrategyChain.class.getResourceAsStream("strategyChain.xml"));
			Element e = util.xml.toElement(config);
			StrategyChain jaxb = util.xml.toJaxbObject(StrategyChain.class, e);
			return loadFromJaxbObject(jaxb, owner);
		} catch (Exception e) { 
			logger.warn("Unexpected error when loading default strategy.", e);
			StrategyChain c = new StrategyChain(owner);
			c.getStrategy().add(new AggregationStrategySimple(owner));
			return c;
		}
	}
	public static synchronized StrategyChain loadFromJaxbObject(StrategyChain jaxb, AbstractNode owner) throws Exception {
		jaxb.setOwner(owner);
		for(AggregationStrategy s : jaxb.getStrategy()) {
			s.setOwner(owner);
		}
		return jaxb;
	}
	public static synchronized StrategyChain loadFromElement(Element element, AbstractNode owner) throws Exception {
		StrategyChain jaxb = XMLUtil.getInstance().toJaxbObject(StrategyChain.class, element);
		return loadFromJaxbObject(jaxb, owner);
	}
	public static synchronized StrategyChain loadFromElement(String element, AggregatorNode owner) throws Exception {
		return loadFromElement(XMLUtil.getInstance().toElement(element), owner);
	}
	public void resetCache() throws Exception {
		for(AggregationStrategy s : strategy) {
			s.resetCache();
		}
	}
	public boolean doDebugQueries() {
		return strategy.get(0).doDebugQueries();
	}
	public void destroyTopology(String topologyID) {
		// TODO add this method to the strategy chain to free all resources!
	}
}
