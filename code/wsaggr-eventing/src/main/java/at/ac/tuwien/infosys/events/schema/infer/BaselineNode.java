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

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import at.ac.tuwien.infosys.events.schema.EventCorrelationSet.EventPropertySelector;

public class BaselineNode {

	static Map<String,List<BaselineNode>> nodes = new HashMap<String,List<BaselineNode>>();
	String name;
	List<BaselineEdge> outgoing = new LinkedList<BaselineEdge>();
	List<BaselineEdge> incoming = new LinkedList<BaselineEdge>();
	List<Node> levels = new LinkedList<Node>();
	Set<EventPropertySelector> matchingProperties = new HashSet<EventPropertySelector>();

	public static class BaselineNodePath extends LinkedList<BaselineNode> {
		private static final long serialVersionUID = 1L;

		public BaselineNodePath() {}
		public BaselineNodePath(BaselineNodePath toCopy) {
			super(toCopy);
		}
		
		@Override
		public String toString() {
			String result = "[P ";
			for(int i = 0; i < this.size() - 1; i ++) {
				result += get(i).name + "->";
			}
			if(!isEmpty()) {
				result += get(size()-1).name;
			}
			result += "]";
			return result;
		}
		public BaselineNodePath copy() {
			return new BaselineNodePath(this);
		}
	}
	
	static BaselineNode getOrCreate(String name,
			Set<EventPropertySelector> props) {
		if(props == null)
			props = new HashSet<EventPropertySelector>();
		if(!nodes.containsKey(name)) 
			nodes.put(name, new LinkedList<BaselineNode>());
		for(BaselineNode n : nodes.get(name)) {
			if(n.matchingProperties.equals(props)) {
				return n;
			}
		}
		BaselineNode n = new BaselineNode();
		n.name = name;
		n.matchingProperties.addAll(props);
		if(!nodes.containsKey(name)) {
			nodes.put(name, new LinkedList<BaselineNode>());
		}
		nodes.get(name).add(n);
		return n;
	}
	
	public List<BaselineEdge> getOutgoing(BaselineNodePath pathSoFar) {
		return getOutgoing(Arrays.asList(pathSoFar));
	}
	public List<BaselineEdge> getOutgoing(List<BaselineNodePath> pathsSoFar) {
		if(pathsSoFar == null) {
			pathsSoFar = new LinkedList<BaselineNodePath>();
		}
		List<BaselineEdge> result = new LinkedList<BaselineEdge>();
		for(BaselineEdge be : outgoing) {
			for(Edge e : be.levels) {
				if(pathsSoFar.isEmpty()) {
					result.add(be);
				} else {
					Node n = e.from.getPredecessor();
					n = e.from;
					if(n != null && !result.contains(be)) {
						for(BaselineNodePath onePath : pathsSoFar) {
							if(n.pathSoFarMatches(onePath)) {
								result.add(be);
								break;
							}
						}
					}
				}
			}
		}
		return result;
	}
	
	public String toString() {
		return toString(30);
	}
	public String toString(int lines) {
		StringBuilder b = new StringBuilder();
		Set<BaselineEdge> visited = new HashSet<BaselineEdge>();
		toString(new AtomicInteger(lines), "", b, null, visited);
		return b.toString();
	}
	private void toString(AtomicInteger maxLines, String indentation, StringBuilder b, BaselineNode parent, Set<BaselineEdge> visited) {
		maxLines.decrementAndGet();
		if(maxLines.get() == 0) {
			b.append(":: output truncated ::\n");
		}
		if(maxLines.get() <= 0) {
			return;
		}
		String phero = parent == null ? "" : BaselineEdge.getOrCreate(parent, this, null).pheromone + " - ";
		List<Integer> l = new LinkedList<Integer>();
		for(int i = 0; i < levels.size(); i ++) {
			//l.add(e.level);
			l.add(i);
		}
		b.append(indentation + "↳ " + phero + name + ", level(s): " + 
				(l.isEmpty() ? "" : l.size() == 1 ? l.get(0) : (l.get(0) + "-" + l.get(l.size() - 1))) + "\n");
		for(BaselineEdge e : new LinkedList<BaselineEdge>(outgoing)) {
			if(!visited.contains(e)) {
				visited.add(e);
				e.to.toString(maxLines, indentation + "  ", b, this, visited);
			}
		}
	}
	public static List<BaselineNode> getAllNodes() {
		List<BaselineNode> list = new LinkedList<BaselineNode>();
		for(List<BaselineNode> l : nodes.values()) {
			for(BaselineNode n : l) {
				list.add(n);
			}
		}
		return list;
	}
	public void remove(boolean recursive) {
		nodes.get(name).remove(this);
		for(BaselineEdge e : new LinkedList<BaselineEdge>(outgoing)) {
			e.remove(recursive);
		}
		outgoing.clear();
		if(recursive) {
			for(Node n : levels) {
				for(Edge e : n.outgoing) {
					e.remove(recursive);
				}
				n.outgoing.clear();
			}
			levels.clear();
		}
		for(Node n : levels) {
			for(Ant a : new LinkedList<Ant>(n.ants)) {
				a.remove();
			}
		}
	}
	
}
