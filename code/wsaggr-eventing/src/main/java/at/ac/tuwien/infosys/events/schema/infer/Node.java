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

package at.ac.tuwien.infosys.events.schema.infer;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.w3c.dom.Element;

import at.ac.tuwien.infosys.events.schema.ESDSchema;
import at.ac.tuwien.infosys.events.schema.EventCorrelationSet;
import at.ac.tuwien.infosys.events.schema.EventCorrelationSet.EventPropertySelector;
import at.ac.tuwien.infosys.events.schema.EventCorrelationSet.EventPropertyValue;
import at.ac.tuwien.infosys.events.schema.infer.BaselineNode.BaselineNodePath;
import at.ac.tuwien.infosys.util.Util;
import at.ac.tuwien.infosys.util.xml.XMLUtil;

public class Node {
	BaselineNode basis;
	List<Edge> outgoing = new LinkedList<Edge>();
	List<Edge> incoming = new LinkedList<Edge>();
	List<Ant> ants = new LinkedList<Ant>();
	Set<EventPropertyValue> values = new HashSet<EventPropertyValue>();
	Element underlyingEvent;
	List<EventCorrelationSet> correlationSets;
	
	
	public static class NodeAny extends Node {
		public int multiplicity = 1;
		
		public NodeAny(List<EventCorrelationSet> correlationSets) {
			super(BaselineNode.getOrCreate("*", null), null, correlationSets);
		}
	}
	
	public static class NodeSet extends HashSet<Node> {
		private static final long serialVersionUID = 1L;

		public Collection<NodeSet> getOutgoingNodeSets() {
			return getOutgoingNodeSetsByBasis().values();
		}
		public Map<BaselineNode,NodeSet> getOutgoingNodeSetsByBasis() {
			Map<BaselineNode,NodeSet> outgoingByBaselineNode = new HashMap<BaselineNode,NodeSet>();
			for(Node n : this) {
				Node s = n.getSuccessor();
				if(s != null) {
					if(!outgoingByBaselineNode.containsKey(s.basis)) {
						outgoingByBaselineNode.put(s.basis, new NodeSet());
					}
					outgoingByBaselineNode.get(s.basis).add(s);
				}
			}
			return outgoingByBaselineNode;
		}
		public BaselineNode getBasis() {
			if(isEmpty())
				return null;
			return iterator().next().basis;
		}
		public String getName() {
			return getBasis().name;
		}
		public int getMaxMultiplicity() {
			int max = 1;
			for(Node n : this) {
				if(n instanceof NodeAny) {
					int mult = ((NodeAny)n).multiplicity;
					if(mult > max)
						max = mult;
				}
			}
			return max;
		}
		public int getMinMultiplicity() {
			for(Node n : this) {
				if(n instanceof NodeAny)
					return 0;
			}
			return 1;
		}
		@Override
		public String toString() {
			return "[NS " + getBasis().name + "]";
		}
	}
	
	public static class NodeSetPath extends LinkedList<NodeSet> {
		private static final long serialVersionUID = 1L;

		public NodeSetPath() {}
		public NodeSetPath(NodeSetPath toCopy) {
			super(toCopy);
		}
		public NodeSetPath copy() {
			return new NodeSetPath(this);
		}
	}
	
	private Node(BaselineNode basis, Element underlyingEvent, List<EventCorrelationSet> correlationSets) {
		this.basis = basis;
		//this.level = basis.levels.size();
		this.correlationSets = correlationSets;
		this.underlyingEvent = underlyingEvent;
		basis.levels.add(this);
		if(correlationSets != null) {
			for(EventCorrelationSet s : correlationSets) {
				values.addAll(getMatches(s));
			}
		}
	}
	public void setSuccessor(Node n, List<EventCorrelationSet> correlationSets) {
		if(outgoing.size() > 1)
			throw new IllegalStateException("Expected none or a single successor for node, but found " + outgoing.size() + "!");
		if(outgoing.size() == 1) {
			Edge e = outgoing.remove(0);
			e.remove(false);
		}
		Edge.getOrCreate(this, n, correlationSets);
	}
	public Node getFirstNode() {
		Node p = getPredecessor();
		if(p == null)
			return this;
		return p.getFirstNode();
	}
	public Node getLastNode() {
		Node p = getSuccessor();
		if(p == null)
			return this;
		return p.getLastNode();
	}
	public Node getPredecessor() {
		if(incoming.size() <= 0) {
			return null;
		}
		if(incoming.size() > 1) {
			throw new IllegalStateException("Expected a single predecessor for node, but found " + incoming.size() + "!");
		}
		return incoming.get(0).from;
	}
	public Node getSuccessor() {
		if(outgoing.size() <= 0) {
			return null;
		}
		if(outgoing.size() > 1) {
			throw new IllegalStateException("Expected a single successor for node, but found " + outgoing.size() + "!");
		}
		return outgoing.get(0).to;
	}
	public static Node create(BaselineNode target, Element underlyingEvent, List<EventCorrelationSet> correlationSets) {
		return new Node(target, underlyingEvent, correlationSets);
	}
	public static List<Node> getAllNodes() {
		List<Node> list = new LinkedList<Node>();
		for(BaselineNode n : BaselineNode.getAllNodes()) {
			for(Node n1 : n.levels) {
				list.add(n1);
			}
		}
		return list;
	}
	public BaselineNodePath getPathFromTwineStart() {
		return getPathFromTwineStart(true);
	}
	private BaselineNodePath getPathFromTwineStart(boolean removeSubsequentDuplicates) {
		BaselineNodePath result = new BaselineNodePath();
		getPathFromTwineStart(result);
		if(removeSubsequentDuplicates) {
			removeSubsequentDuplicates(result);
		}
		return result;
	}
	private static void removeSubsequentDuplicates(List<BaselineNode> nodePath) {
		for(int i = 0; i < nodePath.size() - 1; i ++) {
			if(nodePath.get(i).equals(nodePath.get(i + 1))) {
				nodePath.remove(i--);
			}
		}
	}
	private void getPathFromTwineStart(List<BaselineNode> path) {
		path.add(0, basis);
		Node n = getPredecessor();
		if(n != null) {
			n.getPathFromTwineStart(path);
		}
	}
	public boolean pathSoFarMatches(BaselineNodePath path) {
		BaselineNodePath pathSoFar = getPathFromTwineStart();
		path = new BaselineNodePath(path);
		removeSubsequentDuplicates(path);
		//System.out.println("comparing paths:\n" + path + "\n" + pathSoFar + "\n----");
		if(path.size() > pathSoFar.size()) {
			return false;
		}
		for(int i = 1; i <= path.size() && i <= pathSoFar.size(); i ++) {
			BaselineNode n1 = path.get(path.size() - i);
			BaselineNode n2 = pathSoFar.get(pathSoFar.size() - i);
			if(!n1.equals(n2))
				return false;
		}
		//System.out.println("path match:\n" + path + "\n" + pathSoFar + "\n----");
		return true;
	}
	public void remove(boolean recursive) {
		for(Edge e : new LinkedList<Edge>(outgoing)) {
			e.remove(recursive);
		}
	}
	public Set<EventPropertyValue> getMatches(EventCorrelationSet corr) {
		return getMatches(underlyingEvent, corr);
	}
	private static Set<EventPropertyValue> getMatches(Element event, EventCorrelationSet corr) {
		return getMatches(event, Collections.singletonList(corr));
	}
	protected static Set<EventPropertyValue> getMatches(Element event, List<EventCorrelationSet> corrs) {
		Set<EventPropertyValue> result = new HashSet<EventPropertyValue>();
		for(EventCorrelationSet corr : corrs) {
			for(EventPropertySelector s : corr.getCorrelatedProperties()) {
				if(event != null) {
					if(s.matches(event)) {
						Object v = s.apply(event);
						result.add(new EventPropertyValue(s, v));
					}
				}
			}
		}
		return result;
	}
	@Override
	public String toString() {
		return "[Node basis=" + basis + "]";
	}
	public boolean isAnyType() {
		return basis.name != null && basis.name.trim().equals("*");
	}
	public void dumpTwine() {
		if(underlyingEvent != null) {
			System.out.println(XMLUtil.getInstance().toString(underlyingEvent));
		} else {
			System.out.println(basis.name);
		}
		Node s = getSuccessor();
		if(s != null)
			s.dumpTwine();
	}

	public Node copy() {
		Node copy = new Node(basis, underlyingEvent, correlationSets);
		Node s = getSuccessor();
		if(s != null) {
			s = s.copy();
			copy.setSuccessor(s, s.correlationSets);
		}
		return copy;
	}
	public Element constructSequenceAsXML() {
		List<Element> l = new LinkedList<Element>();
		getElementSequence(l);
		Util util = new Util();
		Element result = util.xml.toElementSafe("<esd:events xmlns:esd=\"" + ESDSchema.NAMESPACE_URI_ESD + "\"/>");
		for(Element e : l) {
			try {
				util.xml.appendChild(result, e);
			} catch (Exception e1) {
				throw new RuntimeException(e1);
			}
		}
		return result;
	}
	private void getElementSequence(List<Element> listToFill) {
		if(underlyingEvent != null) {
			listToFill.add(underlyingEvent);
		}
		Node n = getSuccessor();
		if(n != null) {
			n.getElementSequence(listToFill);
		}
	}
}
