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
//package at.ac.tuwien.infosys.aggr.eventschema.infer;
//
//import java.util.ArrayList;
//import java.util.Collections;
//import java.util.LinkedList;
//import java.util.List;
//import java.util.Set;
//
//import org.w3c.dom.Element;
//
//import at.ac.tuwien.infosys.aggr.events.EventStoresManager.EventStream;
//import at.ac.tuwien.infosys.aggr.eventschema.EventCorrelationSet.EventPropertySelector;
//import at.ac.tuwien.infosys.aggr.eventschema.XmlSchemaInference.SchemaSet;
//import at.ac.tuwien.infosys.aggr.eventschema.EventCorrelationSet;
//import at.ac.tuwien.infosys.aggr.eventschema.LoggedEvent;
//
//public class EventSchemaInferenceACO extends EventSchemaInference {
//
//	private static final Double MIN_PHEROMONE_LEVEL = 0.51;
//	private static final Double PHEROMONE_DEPOSIT = 1.0;
//	private static final Double PHEROMONE_MIN_EVAPORATE = 0.05;
//	private static final Double PHEROMONE_MAX_EVAPORATE = 2.0;
//	private static final int MAX_NUM_EDGES = 5000;
//
//	protected List<LoggedEvent> events = new ArrayList<LoggedEvent>();
//	private List<BaselineNode> roots = new LinkedList<BaselineNode>();
//	
//	
//	public static class Path extends LinkedList<Node> {
//		private static final long serialVersionUID = 1L;
//		public Path() {}
//		public Path(Path copy) {
//			super(copy);
//		}
//	}
//	
//	public EventSchemaInferenceACO(EventStream stream, SchemaInferenceConfig cfg) {
//		super(stream, cfg);
//	}
//	
//	@Override
//	public void addEvent(Element event) throws Exception {
//		moveAnts(event);
//		updatePheromones();
//		removeUnusedNodes();
//		dump();
//	}
//
//	@Override
//	public SchemaSet getCurrentlyBestEventSchema() {
//		dumpTree();
//		removeLowPheromones();
//		dumpTree();
//		return null;
//	}
//
//	
//	
//	private void removeLowPheromones() {
//		double max = getMaxPheromone() / 2.0;
//		filterByPheromone(max);
//	}
//
//	private double getMaxPheromone() {
//		double max = 0;
//		for(BaselineEdge e : new LinkedList<BaselineEdge>(BaselineEdge.edges)) {
//			if(e.pheromone > max) {
//				max = e.pheromone;
//			}
//		}
//		return max;
//	}
//	
//	private void filterByPheromone(double threshold) {
//		for(BaselineEdge e : new LinkedList<BaselineEdge>(BaselineEdge.edges)) {
//			if(e.pheromone < threshold) {
//				e.remove(false);
//			}
//		}
//	}
//	
//	private void updatePheromones() {
//		for(BaselineEdge e : BaselineEdge.edges) {
//			e.pheromone -= getEvaporationFactor();
//		}
//	}
//	
//	private double getEvaporationFactor() {
//		double diff = PHEROMONE_MAX_EVAPORATE - PHEROMONE_MIN_EVAPORATE;
//		double ratio = 1;
//		if(BaselineEdge.edges.size() < MAX_NUM_EDGES) {
//			ratio = (double)BaselineEdge.edges.size() / (double)MAX_NUM_EDGES;
//		}
//		double phero = PHEROMONE_MIN_EVAPORATE + (ratio*diff);
//		//System.out.println("phero: " + phero);
//		return phero;
//	}
//
////	private boolean haveSameRoot(Node n1, Node n2) {
////		return haveSameRoot(n1.basis, n2.basis);
////	}
////	private boolean haveSameRoot(BaselineNode n1, BaselineNode n2) {
////		BaselineNode r1 = getRoot(n1);
////		BaselineNode r2 = getRoot(n2);
////		return r1 != null && r2 != null && r1.equals(r2);
////	}
////	private boolean hasNoRoot(Node n) {
////		return hasNoRoot(n.basis);
////	}
////	private boolean hasNoRoot(BaselineNode n) {
////		return getRoot(n) == null;
////	}
////	private BaselineNode getRoot(BaselineNode n) {
////		return getRoot(n, new LinkedList<BaselineNode>());
////	}
////	private BaselineNode getRoot(BaselineNode n, List<BaselineNode> visited) {
////		if(visited.contains(n)) {
////			return null;
////		}
////		visited.add(n);
////		if(roots.contains(n)) {
////			return n;
////		}
////		for(BaselineEdge e : n.incoming) {
////			BaselineNode p = e.from;
////			BaselineNode r = getRoot(p, visited);
////			if(r != null) 
////				return r;
////		}
////		return null;
////	}
//
//	private void removeUnusedNodes() {
//		for(BaselineEdge e : new LinkedList<BaselineEdge>(BaselineEdge.edges)) {
//			if(e.pheromone < MIN_PHEROMONE_LEVEL) {
//				//System.out.println("removing edge with pheromone level " + e.pheromone);
//				e.remove(false);
//			}
//		}
//		List<BaselineNode> toDelete = new LinkedList<BaselineNode>();
//		for(BaselineNode n : BaselineNode.getAllNodes()) {
//			if(n.incoming.isEmpty() && !roots.contains(n)) {
//				//System.out.println("deleting node " + n.name);
//				toDelete.add(n);
//			}
//		}
//		for(BaselineNode n : toDelete) {
//			roots.remove(n);
//			n.remove(false);
//		}
//		for(Node n : Node.getAllNodes()) {
//			if(n.incoming.isEmpty() && !roots.contains(n.basis)) {
//				throw new RuntimeException("!! Node should have been removed, but is still here: " + 
//						n + " - " + n.basis.incoming.size() + " - " + roots.size());
//			}
//		}
//	}
//
//	private void dumpTree() {
//		for(BaselineNode n : roots) {
//			System.out.println(n.toString());
//		}
//	}
//
//	private void dump() {
//		//dumpTree();
//		System.out.println("\n-------------------");
//		List<Double> pheros = new LinkedList<Double>();
//		for(BaselineEdge e : BaselineEdge.edges) {
//			pheros.add(e.pheromone);
//		}
//		Collections.sort(pheros);
//		if(!pheros.isEmpty()) {
//			System.out.println("pheromones: " + pheros.size() + " - [" + Collections.min(pheros) + "," + Collections.max(pheros) + "]");
//		}
//	}
//
//	private void moveAnts(Element next) {
//		String name = next.getLocalName();
//		System.out.println(ants.size() + " ants: " + ants);
//
//		Set<EventPropertySelector> props = 
//			EventCorrelationSet.getAllProperties(config.correlationSets);
//		props = EventPropertySelector.match(props, next);
//
//
//		for(Ant a : new LinkedList<Ant>(ants)) {
//
//			if(a.currentNode == null) {
//
//				a.currentNode = Node.getOrCreate(getRoot(name, props), next, config.correlationSets, 0);
//				a.currentNode.ants.add(a);
//
//			} else {
//				
//				BaselineNode.getOrCreate(name, props);
//				boolean moved = false;
//				for(BaselineNode target : BaselineNode.nodes.get(name)) {
//
//					//if(Node.get(target, 0) == null || haveSameRoot(a.currentNode.basis, target)) {
//
//					if(!hasConflictingConstraint(a.currentNode, target, next)) {
//
//						int nextLevel = getMaxLevel(a.currentNode.basis, target) + 1;
//						//System.out.println("adding edge from " + a.currentNode.basis.name + " to " + target.name);
//
//						Node to = Node.getOrCreate(target, next, config.correlationSets, nextLevel);
//						
//						Edge e = Edge.getOrCreate(a.currentNode, to, config.correlationSets);
//						e.basis.pheromone += PHEROMONE_DEPOSIT;
//						if(to.ants.isEmpty()) {
//							a.currentNode = to;
//							if(!moved) {
//								a.currentNode.ants.remove(a);
//								to.ants.add(a);
//								moved = true;
//							} else {
//								Ant a1 = new Ant(to);
//								ants.add(a1);
//								to.ants.add(a1);
//							}
//						} else {
//							a.remove();
//						}
//					}
//					//}
//					
//				}
//			}
//		}
//		
//		if(getRoot(name, props).levels.isEmpty()) {
//			Node n = Node.getOrCreate(getRoot(name, props), next, config.correlationSets, 0);
//			ants.add(new Ant(n));
//			System.out.println("added ant! num roots: " + roots.size());
//		}
//		
//		System.out.println("------------");
//	}
//
//	private boolean hasConflictingConstraint(Node currentNode, BaselineNode target, Element targetContent) {
////		BaselineEdge e = BaselineEdge.get(currentNode.basis, target);
////		if(e == null)
////			return false;
////		for(Edge e1 : e.levels) {
////			// TODO!
////		}
//		return false;
//	}
//
//	private int getMaxLevel(BaselineNode from, BaselineNode to) {
//		List<Path> paths = getPaths(from, to);
//		int max = -1;
//		int max1 = -1;
//		for(Path p : paths) {
//			if(p.get(0).level > max)
//				max = p.get(0).level;
//
//			if(p.get(p.size() - 1).level > max1)
//				max1 = p.get(p.size() - 1).level;
//		}
//		//System.out.println(from.name + " to " + to.name + " , num paths: " + paths.size() + " , max level src: " + max + " , max level dst: " + max1);
//		return max;
//	}
//
//	private List<Path> getPaths(BaselineNode from, BaselineNode to) {
//		List<Path> paths = new LinkedList<Path>();
//		for(Node n : from.levels) {
//			getPaths(n, to, new Path(), paths);
//		}
//		return paths;
//	}
//	private void getPaths(Node from, BaselineNode to, Path pathSoFar, List<Path> paths) {
//		pathSoFar.add(from);
//		if(from.basis.equals(to)) {
//			paths.add(pathSoFar);
//			return;
//		}
//		for(Edge e : from.outgoing) {
//			Node nextTo = e.to;
//			if(!pathSoFar.contains(nextTo)) {
//				getPaths(nextTo, to, new Path(pathSoFar), paths);
//				return;
//			}
//		}
//	}
//	
//	private BaselineNode getRoot(String name, Set<EventPropertySelector> props) {
//		for(BaselineNode n : roots) {
//			if(name.equals(n.name))
//				return n;
//		}
//		BaselineNode b = BaselineNode.getOrCreate(name, props);
//		roots.add(b);
//		return b;
//	}
//}
