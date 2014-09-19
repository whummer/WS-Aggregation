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

import io.hummer.util.NotImplementedException;
import io.hummer.util.Util;
import io.hummer.util.test.TestUtil;
import io.hummer.util.ws.AbstractNode;
import io.hummer.util.xml.XMLUtil;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.adapters.XmlAdapter;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import org.apache.log4j.Logger;
import org.w3c.dom.Element;

import at.ac.tuwien.infosys.aggr.node.AggregatorNode;
import at.ac.tuwien.infosys.aggr.node.DataServiceNode;
import at.ac.tuwien.infosys.aggr.node.Registry;
import at.ac.tuwien.infosys.aggr.proxy.RegistryProxy;
import at.ac.tuwien.infosys.aggr.request.AbstractInput;
import at.ac.tuwien.infosys.aggr.request.AggregationRequest;
import at.ac.tuwien.infosys.aggr.request.InputTargetExtractor;
import at.ac.tuwien.infosys.aggr.request.RequestInput;
import at.ac.tuwien.infosys.aggr.request.RequestInput.TargetType;

@XmlJavaTypeAdapter(AggregationStrategy.AggregationStrategyAdapter.class)
public class AggregationStrategy {

	public static Logger logger = Util.getLogger();

	private static Util util = new Util();
	protected static TestUtil testUtil = new TestUtil();
	
	@XmlTransient
	protected AbstractNode owner;
	@XmlElement(name="className", nillable=false)
	private String className;
	
	
	public static class AggregationStrategyAdapter extends XmlAdapter<Object, AggregationStrategy> {

		@Override
		public Object marshal(AggregationStrategy v) throws Exception {
			return XMLUtil.getInstance().toElementWithDynamicContext(v);
		}

		@SuppressWarnings("all")
		@Override
		public AggregationStrategy unmarshal(Object v) throws Exception {
			try {
				Class<? extends AggregationStrategy> clazz = null;
				for(Element e : util.xml.getChildElements((Element)v)) {
					if(e.getTagName().equalsIgnoreCase("className"))
						clazz = (Class<? extends AggregationStrategy>)Class.forName(e.getTextContent());
				}
				AggregationStrategy s = util.xml.toJaxbObjectWithDynamicContext(clazz, (Element)v);
				return s;
			} catch (Exception e) {
				logger.warn("Unable to unmarshal JAXB object.", e);
			}
			return null;
		}
	}

	public AggregationStrategy() { }

	public AggregationStrategy(AbstractNode owner) {
		this.owner = owner;
	}
	
	public boolean doDebugQueries() {
		// should be overwritten by strategy implementor responsible for indicating a debugging request
		return false;
	}

	public void generateRequests(String topologyID, List<AbstractInput> inInputs, Map<AbstractNode,List<RequestInput>> outRequests, AggregationRequest originalRequest) throws Exception {
		// should be overwritten by concrete subclasses
		throw new NotImplementedException();
	}
	public void suggestMasterAggregator(String topologyID, AggregationRequest request, List<AggregatorNode> masterSuggestions) throws Exception {
		// should be overwritten by concrete subclasses
		throw new NotImplementedException();
	}
	public String createTopology(String type, List<String> feature) throws Exception {
		// should be overwritten by concrete subclasses
		throw new NotImplementedException();
	}
	public String createTopology(String type, AggregationRequest request) throws Exception {
		// should be overwritten by concrete subclasses
		throw new NotImplementedException();
	}
	public boolean updateTopology(String topologyID, Object updates) throws Exception {
		// should be overwritten by concrete subclasses
		throw new NotImplementedException();
	}
	public void handleUnreachableNode(AggregatorNode node) {
		// should be overwritten by concrete subclasses
		throw new NotImplementedException();
	}
	public void resetCache() throws Exception {
		// should be overwritten by concrete subclasses
		throw new NotImplementedException();
	}
	
	protected AggregatorNode getRandomAggregatorNode() throws Exception {
		return Registry.getRegistryProxy().getRandomAggregatorNode();
	}
	

	protected int getTotalNumberOfTargetServiceRequests(List<AbstractInput> inputs) throws Exception {
		int totalSize = 0;
		RegistryProxy registry = Registry.getRegistryProxy();
		for(AbstractInput in : inputs) {
			if(!(in instanceof RequestInput))
				continue;
			RequestInput input = (RequestInput)in;
			if(input.getTo() == TargetType.ALL) {
				List<DataServiceNode> services = registry.getDataServiceNodes(input.getFeature());
				if(testUtil.isNullOrNegative(input.getFeatureServiceLast()))
					input.setFeatureServiceLast(services.size() - 1);
				if(testUtil.isNullOrNegative(input.getFeatureServiceFirst()))
					input.setFeatureServiceFirst(0);
				totalSize += input.getFeatureServiceLast() - input.getFeatureServiceFirst() + 1;
			} else if(input.getTo() == TargetType.ONE) {
				totalSize += 1;
			} else {
				throw new Exception("Unexpected input 'to' attribute: " + input.getTo());
			}
		}
		return totalSize;
	}
	
	protected void extractRequestsThatTargetAllServices(List<AbstractInput> inputs) throws Exception {
		int size = inputs.size();
		RegistryProxy registry = Registry.getRegistryProxy();
		for(int i = 0; i < size && i < inputs.size(); i ++) {
			AbstractInput in = inputs.get(i);
			if(!(in instanceof RequestInput))
				continue;
			RequestInput input = (RequestInput)in;
			if(input.getTo() == TargetType.ALL) {
				inputs.remove(i);
				i --;
				List<DataServiceNode> services = registry.getDataServiceNodes(input.getFeature());
				int start = testUtil.isNullOrNegative(input.getFeatureServiceFirst()) ? 0 : input.getFeatureServiceFirst();
				int end = testUtil.isNullOrNegative(input.getFeatureServiceLast()) ? services.size() - 1 : input.getFeatureServiceLast();
				if(end > services.size() - 1) {
					System.out.println("!! AggregationStrategy::extractRequestsThatTargetAllServices: input.featureServiceLast > services.size() - 1 : "+ end + " > " + (services.size() - 1));
					end = services.size() - 1;
				}
				if(end >= start) {
					for(int j = start; j <= end; j ++) {
						DataServiceNode service = services.get(j);
						RequestInput newInput = new RequestInput(input);
						newInput.setFeature(null);
						newInput.setTo(TargetType.ONE);
						newInput.setServiceEPR(service.getEPR());
						inputs.add(newInput);
					}
					i += (end - start) - 1;
				}
				if(i < -1) 
					i = -1;
			}
		}
	}
	
	protected void assignAllInputsToDataServices(List<AbstractInput> inInputs, 
			Map<AbstractNode,List<RequestInput>> outRequests) throws Exception {
		
		while(inInputs.size() > 0) {
			AbstractInput input = inInputs.remove(0);
			if(!(input instanceof RequestInput))
				continue;
			RequestInput in = (RequestInput)input;
			AbstractNode node = InputTargetExtractor.extractDataSourceNode(in, owner);
			
			List<RequestInput> resultList = outRequests.get(node);
			if(resultList == null) {
				resultList = new LinkedList<RequestInput>();
				outRequests.put(node, resultList);
			}
			resultList.add(in);
		}
	}
	
	@XmlTransient
	public String getClassName() {
		return className;
	}
	public void setClassName(String className) {
		this.className = className;
	}
	
	public void setOwner(AbstractNode owner) {
		this.owner = owner;
	}
}
