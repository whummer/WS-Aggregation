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
package at.ac.tuwien.infosys.aggr.request;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.log4j.Logger;
import org.w3c.dom.Element;

import at.ac.tuwien.infosys.aggr.flow.FlowNodesDTO;
import at.ac.tuwien.infosys.aggr.flow.FlowNode.DependencyUpdatedInfo;
import at.ac.tuwien.infosys.aggr.flow.FlowNodesDTO.FlowEdge;
import at.ac.tuwien.infosys.aggr.flow.FlowNodesDTO.FlowNode;
import at.ac.tuwien.infosys.ws.AbstractNode;
import at.ac.tuwien.infosys.util.Configuration;
import at.ac.tuwien.infosys.util.Util;
import at.ac.tuwien.infosys.aggr.util.DebugAssertion.AssertionResult;

@XmlRootElement(name="result",namespace=Configuration.NAMESPACE)
public class AggregationResponse {
	
	private static final Logger logger = Util.getLogger(AggregationResponse.class);

	@XmlTransient
	private final Object inResponseTo;
	@XmlElement(name="result")
	private Object result;
	@XmlElement(name="debug")
	private List<DebugInfo> debug = new LinkedList<DebugInfo>();
	@XmlElement(name="graph")
	private FlowNodesDTO graph;
	@XmlElement
	private String topologyID;

	@XmlRootElement(name="debug")
	public static class DebugInfo {
		@XmlAttribute
		private String inResponseTo = "";
		@XmlElement
		private Object resultBeforeQuery;
		@XmlElement
		private Object resultAfterQuery;
		@XmlElement(name="providedBy", type=RequestInput.class)
		private List<AbstractInput> providedBy = new LinkedList<AbstractInput>();
		@XmlElement(name="assertResult")
		private List<AssertionResult> assertionResults = new LinkedList<AssertionResult>();
		@XmlElement(name="dataSourceError")
		private String dataSourceError;
		@XmlTransient
		public RequestInput inResponseToInput;
		@XmlTransient
		public RequestInput inResponseToInputOriginal;
		@XmlTransient
		public AbstractNode toNode;
		@XmlTransient
		public boolean hasDependency = false;
		@XmlTransient
		public List<DependencyUpdatedInfo> updated = new LinkedList<DependencyUpdatedInfo>();

		@XmlTransient
		public String getInResponseTo() {
			return inResponseTo;
		}
		public void setInResponseTo(String inResponseTo) {
			this.inResponseTo = inResponseTo;
		}
		@XmlTransient
		public Object getResultBeforeQuery() {
			return resultBeforeQuery;
		}
		public void setResultBeforeQuery(Object resultBeforeQuery) {
			this.resultBeforeQuery = resultBeforeQuery;
		}
		@XmlTransient
		public Object getResultAfterQuery() {
			return resultAfterQuery;
		}
		public void setResultAfterQuery(Object resultAfterQuery) {
			this.resultAfterQuery = resultAfterQuery;
		}
		@XmlTransient
		public List<AbstractInput> getProvidedBy() {
			return providedBy;
		}
		public void setProvidedBy(List<AbstractInput> providedBy) {
			this.providedBy = providedBy;
		}
		@XmlTransient
		public List<AssertionResult> getAssertionResults() {
			return assertionResults;
		}
		public void setAssertionResults(List<AssertionResult> assertionResults) {
			this.assertionResults = assertionResults;
		}
		@XmlTransient
		public String getDataSourceError() {
			return dataSourceError;
		}
		public void setDataSourceError(String dataSourceError) {
			this.dataSourceError = dataSourceError;
		}
	}
	
	/** no-arg constructor required by JAXB, should not be used by the programmer. */
	@Deprecated
	public AggregationResponse() { this.inResponseTo = null; }
	
	public AggregationResponse(Object inResponseTo) {
		this.inResponseTo = inResponseTo;
	}
		
	public AggregationResponse(Object inResponseTo, Object result) {
		this.inResponseTo = inResponseTo;
		this.result = result;
	}
	
	public AggregationResponse(AggregationResponse toCopy, Object newInResponseTo) {
		this.inResponseTo = newInResponseTo;
		this.result = toCopy.result;
		this.debug.addAll(toCopy.getDebug());
	}

	public boolean isException() {
		return (result != null) && (result instanceof Exception);
	}

	public boolean isError() {
		return (result == null) || isException();
	}
	
	@Override
	public int hashCode() {
		int code = 0;
		if(inResponseTo != null) code += inResponseTo.hashCode();
		if(result != null) code += result.hashCode();
		return code;
	}
	
	@Override
	public boolean equals(Object o) {
		if(!(o instanceof AggregationResponse))
			return false;
		AggregationResponse r = (AggregationResponse)o;
		boolean eq = true;
		if(inResponseTo != null) eq &= inResponseTo.equals(r.inResponseTo);
		if(result != null) eq &= result.equals(r.result);
		return eq;
	}
	
	@SuppressWarnings("all")
	public <T> T getResultAs(Class<T> clazz) throws Exception {
		Object r = getResult();
		if(clazz.isAssignableFrom(r.getClass()))
			return (T)r;
		if(!(r instanceof Element))
			return null;
		Element e = (Element)r;
		return Util.getInstance().toJaxbObject(clazz, e);
	}
	
	public Element getResultAsElement() throws Exception {
		Object r = getResult();
		if(r instanceof Element)
			return (Element)r;
		if(r instanceof String)
			return Util.getInstance().xml.toElement("<r>" + r + "</r>");
		throw new IllegalStateException("Result is neither an Element nor a String: " + r);
	}
	
	public void buildGraphFromDebugInfo() {
		try {
			graph = new FlowNodesDTO();
			logger.debug("Existing debug messages: " + debug);
			for(DebugInfo d : debug) {
				FlowNode node = new FlowNode();
				node.setID(d.inResponseTo);
				node.setName("s" + node.getID());
				if(node.getID() != null && !node.getID().isEmpty() && !graph.getFlowNodes().contains(node)) {
					graph.getFlowNodes().add(node);
				}
			}
			for(DebugInfo d : debug) {
				if(d.providedBy != null) {
					for(AbstractInput i : d.providedBy) {
						FlowNode temp = new FlowNode();
						temp.setID(i.getExternalID());
						if(!graph.getFlowNodes().contains(temp)) {
							logger.warn("Could not find flow node belonging to request input " + i.getExternalID() + " in model.");
						} else {
							FlowEdge e = new FlowEdge();
							e.setFrom(i.getExternalID());
							e.setTo(d.inResponseTo);
							if(!graph.getFlowEdges().contains(e))
								graph.getFlowEdges().add(e);
						}
					}
				}
			}
			// sort nodes
			Collections.sort(graph.getFlowNodes(), new Comparator<FlowNode>() {
				public int compare(FlowNode n1, FlowNode n2) {
					try {
						return new Integer(Integer.parseInt(n1.getID()))
							.compareTo(Integer.parseInt(n2.getID()));
					} catch (Exception e) {
						try {
							return n1.getID().compareTo(n2.getID());
						} catch (Exception e2) {
							logger.error("Unable to sort nodes in dependency graph.", e2);
							return 0;
						}
					}
				}
			});
		} catch (Exception e) {
			logger.error("Unable to build dependency graph", e);
		}
	}
	
	@XmlTransient
	public FlowNodesDTO getGraph() {
		return graph;
	}
	public void setGraph(FlowNodesDTO graph) {
		this.graph = graph;
	}
	@XmlTransient
	public Object getResult() {
		return result;
	}
	public void setResult(Object result) {
		this.result = result;
	}
	@XmlTransient
	public List<DebugInfo> getDebug() {
		return debug;
	}
	public void setDebug(List<DebugInfo> debug) {
		this.debug = debug;
	}
	@XmlTransient
	public String getTopologyID() {
		return topologyID;
	}
	public void setTopologyID(String topologyID) {
		this.topologyID = topologyID;
	}
	@XmlTransient
	public Object getInResponseTo() {
		return inResponseTo;
	}
}
