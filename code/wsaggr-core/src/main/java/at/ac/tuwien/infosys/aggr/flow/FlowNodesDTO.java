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

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

@XmlRootElement(name="dependencies")
public class FlowNodesDTO {

	@XmlElement(name="node")
	private List<FlowNode> flowNodes = new LinkedList<FlowNode>();
	@XmlElement(name="edge")
	private List<FlowEdge> flowEdges = new LinkedList<FlowEdge>();

	public static class FlowNode {
		private static AtomicInteger idCounter = new AtomicInteger();
		@XmlAttribute(name="ID")
		private String ID;
		@XmlAttribute
		private String name;
		@XmlElement
		private List<Object> invocationResult;
		@XmlElement
		private Object tempFinalResult;
		@Override
		public boolean equals(Object o) {
			if(!(o instanceof FlowNode)) {
				return false;
			}
			return ((FlowNode)o).ID != null && ((FlowNode)o).ID.equals(ID);
		}
		
		@XmlTransient
		public String getID() {
			return ID;
		}
		public void setID(String iD) {
			ID = iD;
		}
		@XmlTransient
		public String getName() {
			return name;
		}
		public void setName(String name) {
			this.name = name;
		}
		@XmlTransient
		public List<Object> getInvocationResult() {
			return invocationResult;
		}
		public void setInvocationResult(List<Object> invocationResult) {
			this.invocationResult = invocationResult;
		}
		@XmlTransient
		public Object getTempFinalResult() {
			return tempFinalResult;
		}
		public void setTempFinalResult(Object tempFinalResult) {
			this.tempFinalResult = tempFinalResult;
		}
	}

	public static class FlowEdge {
		@XmlAttribute
		private String from;
		@XmlAttribute
		private String to;
		@XmlElement
		private String name;
		@XmlElement
		private Object extractedData;
		@Override
		public boolean equals(Object o) {
			if(!(o instanceof FlowEdge)) 
				return false;
			FlowEdge e = (FlowEdge)o;
			return e.from != null && e.to != null && e.from.equals(from) && e.to.equals(to);
		}
		
		@XmlTransient
		public String getFrom() {
			return from;
		}
		public void setFrom(String from) {
			this.from = from;
		}
		@XmlTransient
		public String getTo() {
			return to;
		}
		public void setTo(String to) {
			this.to = to;
		}
		@XmlTransient
		public String getName() {
			return name;
		}
		public void setName(String name) {
			this.name = name;
		}
		@XmlTransient
		public Object getExtractedData() {
			return extractedData;
		}
		public void setExtractedData(Object extractedData) {
			this.extractedData = extractedData;
		}
	}

	public String addNode(String name) {
		FlowNode n = new FlowNode();
		n.name = name;
		n.ID = "" + FlowNode.idCounter.getAndIncrement();
		flowNodes.add(n);
		return n.ID;
	}

	public void addEdge(FlowNode n1, FlowNode n2) {
		addEdge(n1.ID, n2.ID);
	}
	public void addEdge(String n1, String n2) {
		FlowEdge e = new FlowEdge();
		e.from = n1;
		e.to = n2;
		flowEdges.add(e);
	}
	
	@XmlTransient
	public List<FlowEdge> getFlowEdges() {
		return flowEdges;
	}
	@XmlTransient
	public List<FlowNode> getFlowNodes() {
		return flowNodes;
	}
}
