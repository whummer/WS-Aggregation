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

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.adapters.XmlAdapter;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import org.apache.log4j.Logger;
import org.w3c.dom.Element;

import at.ac.tuwien.infosys.aggr.node.AggregatorNode;
import at.ac.tuwien.infosys.aggr.node.DataServiceNode;
import at.ac.tuwien.infosys.aggr.request.AbstractInput;
import at.ac.tuwien.infosys.aggr.request.AggregationRequest;
import at.ac.tuwien.infosys.aggr.request.InputTargetExtractor;
import at.ac.tuwien.infosys.aggr.request.NonConstantInput;
import at.ac.tuwien.infosys.aggr.request.WAQLQuery.PreparationQuery;
import at.ac.tuwien.infosys.aggr.waql.DataDependency;

@XmlRootElement(name="Topology")
public class Topology {

	public static final Logger logger = Util.getLogger(Topology.class);

	private static final boolean CHECK_INPUT_ID_UNIQUENESS = true;

	@XmlElement
	private String topologyID;
	@XmlElement
	@XmlJavaTypeAdapter(CollectionXmlAdapter.class)
	private HashMap<AggregatorNode, LinkedList<AggregatorNode>> partners = new HashMap<AggregatorNode,LinkedList<AggregatorNode>>();
	/** targetServices is used to map feature names to concrete data service nodes */
	@XmlElement
	@XmlJavaTypeAdapter(CollectionXmlAdapter.class)
	private HashMap<String,LinkedList<DataServiceNode>> targetServices = new HashMap<String, LinkedList<DataServiceNode>>();
	/** targetServicesByRequest is used to store the complete topology for data monitoring */
	@XmlElement
	@XmlJavaTypeAdapter(CollectionXmlAdapter.class)
	private HashMap<AggregatorNode, HashMap<DataServiceNode,LinkedList<AggregationRequest>>> targetServiceRequests = new HashMap<AggregatorNode, HashMap<DataServiceNode,LinkedList<AggregationRequest>>>();
	/** timestamp of last usage, required for garbage collection of unused topologies. */
	@XmlTransient
	private long lastUsed;
	/** specifies whether in this topology notifications between aggregators should be sent directly or along the specified topology path. */
	@XmlElement
	private boolean sendNotificationsDirectly = false;
	
	
	public static class CollectionXmlAdapter extends XmlAdapter<Object, Object> {
		private Util util = new Util();
		private static final String NAMESPACE = "http://jaxbAdapter";
	    
		@Override
		@SuppressWarnings("all")
		public Object marshal(Object o) throws Exception {
			StringBuilder b = new StringBuilder();
			if(o instanceof Map) {
				Map map = (Map)o;
				b.append("<j:map xmlns:j=\"" + NAMESPACE + "\">");
				for(Object e : map.entrySet()) {
					Entry entry = (Entry)e;
					b.append("<j:e>");
					b.append("<k>");
					b.append(util.xml.toString(marshal(entry.getKey())));
					b.append("</k>");
					b.append("<v>");
					b.append(util.xml.toString(marshal(entry.getValue())));
					b.append("</v>");
					b.append("</j:e>");
				}
				b.append("</j:map>");
			} else if(o instanceof List) {
				List list = (List)o;
				b.append("<j:list xmlns:j=\"" + NAMESPACE + "\">");
				for(Object i : list) {
					b.append("<j:i>");
					b.append(util.xml.toString(marshal(i)));
					b.append("</j:i>");
				}
				b.append("</j:list>");
			} else if(o instanceof Element) {
				return (Element)o;
			} else {
				return util.xml.toElement(o);
			}
			return util.xml.toElement(b.toString());
		}
		@Override
		@SuppressWarnings("all")
		public Object unmarshal(Object v) throws Exception {
			Element e = (Element)v;
			List<Element> children = util.xml.getChildElements(e);
			if(NAMESPACE.equals(e.getNamespaceURI()) || (children.size() > 0 && 
					NAMESPACE.equals(children.get(0).getNamespaceURI()))) {
				if(e.getLocalName().equals("map") || (children.size() > 0 &&
						children.get(0).getLocalName().equals("e"))) { // e = map entry
					Map map = new HashMap();
					for(Element entry : children) {
						List<Element> keyAndValue = util.xml.getChildElements(entry);
						Object key = unmarshal(util.xml.getChildElements(keyAndValue.get(0)).get(0));
						Object value = unmarshal(util.xml.getChildElements(keyAndValue.get(1)).get(0));
						map.put(key, value);
					}
					return map;
				} else if(e.getLocalName().equals("list") || (children.size() > 0 &&
						children.get(0).getLocalName().equals("i"))) { // i = list item
					List list = new LinkedList();
					for(Element item : children) {
						Object itemObj = unmarshal(util.xml.getChildElements(item).get(0));
						list.add(itemObj);
					}
					return list;
				} 
			}
			Object returnObj = util.xml.toJaxbObject(e);
			if(returnObj instanceof AggregationRequest) {
				AggregationRequest r = (AggregationRequest)returnObj;
				for(AbstractInput i : r.getAllInputs()) {
					i.request = r;
				}
			}
			return returnObj;
		}
	}
	
	public void clearTargetServices(String feature) {
		if(!targetServices.containsKey(feature))
			targetServices.put(feature, new LinkedList<DataServiceNode>());
		targetServices.get(feature).clear();
	}
	
	public Map<DataServiceNode,LinkedList<AggregationRequest>> getTargetServiceRequests(AggregatorNode n) {
		if(!targetServiceRequests.containsKey(n))
			targetServiceRequests.put(n, new HashMap<DataServiceNode,LinkedList<AggregationRequest>>());
		return Collections.unmodifiableMap(targetServiceRequests.get(n));
	}
	public List<AggregationRequest> getTargetServiceRequests(AggregatorNode aggr, DataServiceNode data) {
		getTargetServiceRequests(aggr);
		Map<DataServiceNode,LinkedList<AggregationRequest>> inputs = targetServiceRequests.get(aggr);
		if(!inputs.containsKey(data))
			inputs.put(data, new LinkedList<AggregationRequest>());
		return Collections.unmodifiableList(inputs.get(data));
	}
	public List<AggregatorNode> getPartners(AggregatorNode node) {
		if(!partners.containsKey(node))
			return new LinkedList<AggregatorNode>();
		return partners.get(node);
	}
	
	public AggregatorNode getResponsibleAggregator(AbstractInput input) {
		for(AggregatorNode a : targetServiceRequests.keySet()) {
			Map<DataServiceNode,LinkedList<AggregationRequest>> map = targetServiceRequests.get(a);
			for(DataServiceNode n : map.keySet()) {
				for(AggregationRequest r : map.get(n)) {
					if(input.searchInListByID(r.getAllInputs()))
						return a;
				}
			}
		}
		return null;
	}
	
	
	
	/*public AggregatorNode getResponsibleAggregator(String requestID) {
		for(AggregatorNode a : targetServiceRequests.keySet()) {
			Map<DataServiceNode,LinkedList<AggregationRequest>> map = targetServiceRequests.get(a);
			for(DataServiceNode n : map.keySet()) {
				for(AggregationRequest r : map.get(n)) {
					if(requestID.equals(r.requestID))
						return a;
				}
			}
		}
		return null;
	}*/
	
	/*public List<AggregatorNode> getDependingAggregators(String requestIDofReceivedResult) {
		List<AggregatorNode> result = new LinkedList<AggregatorNode>();
		for(AggregationRequest req : dataFlows.keySet()) {
			if(requestIDofReceivedResult.equals(req.requestID)) {
				for(AggregationRequest dep : dataFlows.get(req)) {
					AggregatorNode n = getResponsibleAggregator(dep.requestID);
					if(!result.contains(n))
						result.add(n);
				}
			}
		}
		return result;
	}*/

	public void addPartner(AggregatorNode from, AggregatorNode to) {
		List<AggregatorNode> partnerAggrs = partners.get(from);
		if(partnerAggrs == null) {
			partners.put(from, new LinkedList<AggregatorNode>());
			partnerAggrs = partners.get(from);
		}
		if(from.equals(to)) {
			throw new RuntimeException("Cannot add topology link because parent node equals child node.");
		}
		if(!partnerAggrs.contains(to))
			partnerAggrs.add(to);
	}

	public AggregatorNode getParent(AggregatorNode aggr) {
		for(AggregatorNode a1 : partners.keySet()) {
			for(AggregatorNode a2 : partners.get(a1)) {
				if(a2.equals(aggr))
					return a1;
			}
		}
		return null;
	}
	
	public AggregatorNode getMasterAggregator() {
		AggregatorNode m = null;
		for(AggregatorNode a : partners.keySet()) {
			if(getParent(a) == null) {
				if(m != null)
					throw new RuntimeException("Could not unambiguously determine topology master aggregator.");
				m = a;
			}
		}
		if(m == null) {
			for(AggregatorNode a : targetServiceRequests.keySet()) {
				List<AggregationRequest> allReq = getAllRequests(a);
				if(allReq.size() > 0) {
					if(m != null)
						throw new RuntimeException("Could not unambiguously determine topology master aggregator.");
					m = a;
				}
			}
		}
		if(m == null)
			logger.warn("Could not determine master aggregator of topology: " + targetServiceRequests);
		return m;
	}

	public List<AggregationRequest> getAllRequests(AggregatorNode n) {
		List<AggregationRequest> result = new LinkedList<AggregationRequest>();
		for(LinkedList<AggregationRequest> list : getTargetServiceRequests(n).values()) {
			result.addAll(list);
		}
		return result;
	}
	
	public List<AggregatorNode> getAllAggregators() {
		List<AggregatorNode> result = new LinkedList<AggregatorNode>();
		result.addAll(partners.keySet());
		for(List<AggregatorNode> list : partners.values()) {
			for(AggregatorNode a : list)
				if(!result.contains(a))
					result.add(a);
		}
		for(AggregatorNode a : targetServiceRequests.keySet()) {
			if(!result.contains(a))
				result.add(a);
		}
		return result;
	}
	
	public List<AbstractInput> getAllInputs() {
		List<AbstractInput> result = new LinkedList<AbstractInput>();
		for(AggregatorNode n : targetServiceRequests.keySet()) {
			HashMap<DataServiceNode, LinkedList<AggregationRequest>> l = targetServiceRequests.get(n);
			for(DataServiceNode d : l.keySet()) {
				for(AggregationRequest r : l.get(d)) {
					for(AbstractInput i : r.getAllInputs()) {
						result.add(i);
					}
				}
			}
		}
		return result;		
	}

	public AbstractInput getInputByUID(String inputID) {
		AbstractInput i = null;
		List<AbstractInput> allInputs = getAllInputs();
		for(AbstractInput in : allInputs) {
			if(inputID.equals(in.getUniqueID())) {
				if(CHECK_INPUT_ID_UNIQUENESS) {
					if(i != null) {
						logger.warn("UIDs of inputs in topology are not unique, duplicate: " + i.getUniqueID() + ", inputs: " + allInputs);
					}
				}
				i = in;
			}
		}
		return i;
	}
	public List<AbstractInput> getInputsByID(String inputID) {
		List<AbstractInput> result = new LinkedList<AbstractInput>();
		List<AbstractInput> allInputs = getAllInputs();
		for(AbstractInput in : allInputs) {
			if(inputID.equals(in.getExternalID())) {
				result.add(in);
			}
		}
		return result;
	}

	public List<AbstractInput> getRequiredInputs(AbstractInput in) {
		List<AbstractInput> result = new LinkedList<AbstractInput>();
		List<DataDependency> deps = null;
		if(in.request instanceof AggregationRequest) {
			deps = ((AggregationRequest)in.request).getAllDataDependencies(in);
		} else {
			deps = in.getDataDependencies();
		}
		for(DataDependency d : deps) {
			if(d.getIdentifier() != null) {
				for(AbstractInput i : getAllInputs()) {
					if(d.getIdentifier().toString().equals(i.getExternalID())) {
						if(!result.contains(i))
							result.add(i);
					}
				}
			}
		}
		return result;
	}

	public AggregationRequest constructTotalRequest() {
		AggregationRequest r = null;
		for(HashMap<DataServiceNode,LinkedList<AggregationRequest>> map : targetServiceRequests.values()) {
			for(LinkedList<AggregationRequest> reqs : map.values()) {
				for(AggregationRequest req : reqs) {
					if(r == null) {
						r = new AggregationRequest(req);
					} else {
						r.getAllInputs().addAll(req.getAllInputs());
					}
					for(PreparationQuery q : req.getQueries().getPreparationQueries()) {
						if(!r.getQueries().getPreparationQueries().contains(q))
							r.getQueries().getPreparationQueries().add(q);
					}
				}
			}
		}
		return r;
	}
	
	public void moveTargetServiceRequest(AggregationRequest request,
			AggregatorNode fromNode, AggregatorNode toNode) throws Exception {
		AggregatorNode oldMasterAggr = getMasterAggregator();
		Map<DataServiceNode,LinkedList<AggregationRequest>> inputs = getTargetServiceRequests(fromNode);
		boolean removed = false;
		AbstractInput input = request.getAllInputs().get(0);
		for(DataServiceNode d : inputs.keySet()) {
			LinkedList<AggregationRequest> list = inputs.get(d);
			for(int i = 0; i < list.size(); i ++) {
				AggregationRequest r = list.get(i);
				if(r.equals(request)) {
					for(AbstractInput i1 : r.getAllInputs()) {
						if(input.equals(i1)) {
							list.remove(i--);
							if(removed)
								logger.info("Unexpected: more than one matching input when changing topology.");
							removed = true;
						}
					}
				}
			}
		}
		if(!removed) {
			logger.info("Could not find matching input (" + request.getRequestID() + "," + input.getExternalID() + ") when moving from " + fromNode + " to " + toNode + " in topology: " + this + " existing:\n");
			for(AggregationRequest r : getAllRequests(fromNode)) {
				for(AbstractInput i : r.getAllInputs()) {

					if(i.request instanceof AggregationRequest) {
						AggregationRequest ar = (AggregationRequest)i.request;
						System.out.println((i.request != null ? ar.getRequestID() : "null") + " - " + i.getExternalID() + " ----- " +
								(input.request != null ? ar.getRequestID() : "null") + " - " + input.getExternalID() );
					}
				}
			}
		}
		NonConstantInput inputTmp = ((NonConstantInput)request.getAllInputs().get(0));
		InputTargetExtractor.extractDataSourceNode(inputTmp);
		DataServiceNode d = (DataServiceNode)InputTargetExtractor.extractDataSourceNode(inputTmp);
		LinkedList<AggregationRequest> reqList = getTargetServiceRequests(toNode).get(d);
		if(reqList == null) {
			reqList = new LinkedList<AggregationRequest>();
			targetServiceRequests.get(toNode).put(d, reqList);
		}
		reqList.add(request);
		// set parent node
		if(getParent(toNode) == null) {
			AggregatorNode parent = getParent(fromNode);
			if(parent == null) {
				if(!fromNode.equals(oldMasterAggr))
					logger.info("parent node of inheriting partner is null, defaulting to master aggregator.");
				parent = oldMasterAggr;
			}
			if(!parent.equals(toNode))
				addPartner(parent, toNode);
		}
	}
	
	public Map<String, LinkedList<DataServiceNode>> getTargetServices() {
		return targetServices;
	}
	public void setLastUsedNow() {
		this.lastUsed = System.currentTimeMillis();
	}
	public long getLastUsed() {
		return lastUsed;
	}
	@XmlTransient
	public boolean isSendNotificationsDirectly() {
		return sendNotificationsDirectly;
	}
	public void setSendNotificationsDirectly(boolean sendNotificationsDirectly) {
		this.sendNotificationsDirectly = sendNotificationsDirectly;
	}

	@XmlTransient
	public HashMap<AggregatorNode,LinkedList<AggregatorNode>> getPartners() {
		return partners;
	}
	public void setPartners(HashMap<AggregatorNode,LinkedList<AggregatorNode>> p) {
		partners = p;
	}

	@XmlTransient
	public Map<AggregatorNode, HashMap<DataServiceNode, LinkedList<AggregationRequest>>> getTargetServiceRequests() {
		return Collections.unmodifiableMap(targetServiceRequests);
	}

	public String toString() {
		//System.out.println("A:toString: " + partners);
		//System.out.println("A:toString1: " + targetServiceRequests);
		StringBuilder b = new StringBuilder();
		b.append(super.toString() + " : \n");
		AggregatorNode m = getMasterAggregator();
		toString(m, b, " ");
		return b.toString();
	}
	private void toString(AggregatorNode parent, StringBuilder b, String indentation) {
		b.append(indentation + " |_ " + parent + " -> " + targetServices.get(parent) + " - " + targetServiceRequests.get(parent)+  "\n");
		//System.out.println(partners + " - contains key : " + parent + " - " + partners.containsKey(parent));
		if(parent != null) {
			if(partners.containsKey(parent)) {
				for(AggregatorNode child : partners.get(parent)) {
					if(!child.equals(parent))
						toString(child, b, indentation + "  ");
				}
			}
		}
	}

	public void addTargetServiceRequest(AggregatorNode aggr, 
			DataServiceNode node, AggregationRequest req) {
		addTargetServiceRequest(aggr, node, req, false);
	}
	public void addTargetServiceRequest(AggregatorNode aggr, 
			DataServiceNode node, AggregationRequest req, Boolean failOnDuplicateInputId) {
		/*List<AggregationRequest> existing = getTargetServiceRequests(aggr, node);
		if(CHECK_INPUT_ID_UNIQUENESS && (failOnDuplicateInputId == null || failOnDuplicateInputId)) {
			for(AggregationRequest r : existing) {
				for(AbstractInput i : r.getAllInputs()) {
					for(AbstractInput j : req.getAllInputs()) {
						if(j.id != null && j.id.equals(i.id)) {
							throw new RuntimeException("Cannot add input with same id to request:\n" + i + "\nexisting:\n" + existing);
						}
					}
				}
			}
		}*/
		for(AbstractInput i : req.getAllInputs()) {
			if(i.request == null)
				i.request = req;
		}
		getTargetServiceRequests(aggr, node);
		targetServiceRequests.get(aggr).get(node).add(req);
	}

	public List<AggregatorNode> getPath(AggregatorNode source, AggregatorNode target) {
		List<AggregatorNode> path = new LinkedList<AggregatorNode>();
		path = doGetPath(source, target, path);
		if(path == null)
			throw new RuntimeException("Could not find path from " + source + " to " + target + " in topology:\n" + this);
		return path;
	}
	private List<AggregatorNode> doGetPath(AggregatorNode source, AggregatorNode target, List<AggregatorNode> pathSoFar) {
		if(source.equals(target)) 
			return pathSoFar;
		AggregatorNode parent = getParent(source);
		if(parent != null && !pathSoFar.contains(parent)) {
			pathSoFar.add(parent);
			List<AggregatorNode> tempPath = doGetPath(parent, target, pathSoFar);
			if(tempPath != null)
				return tempPath;
		}
		for(AggregatorNode child : getPartners(source)) {
			if(child != null && !pathSoFar.contains(child)) {
				pathSoFar.add(child);
				List<AggregatorNode> tempPath = doGetPath(child, target, pathSoFar);
				if(tempPath != null)
					return tempPath;
			}
		}
		return null;
	}

	@XmlTransient
	public String getTopologyID() {
		return topologyID;
	}
	public void setTopologyID(String topologyID) {
		this.topologyID = topologyID;
	}
	
}
