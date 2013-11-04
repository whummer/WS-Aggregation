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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.w3c.dom.Element;

import at.ac.tuwien.infosys.aggr.events.query.EventStream;
import at.ac.tuwien.infosys.aggr.xml.XPathProcessor;
import at.ac.tuwien.infosys.events.schema.ESDAll;
import at.ac.tuwien.infosys.events.schema.ESDChoice;
import at.ac.tuwien.infosys.events.schema.ESDEvent;
import at.ac.tuwien.infosys.events.schema.ESDObject;
import at.ac.tuwien.infosys.events.schema.ESDSchema;
import at.ac.tuwien.infosys.events.schema.ESDSequence;
import at.ac.tuwien.infosys.events.schema.EventCorrelationSet;
import at.ac.tuwien.infosys.events.schema.LoggedEvent;
import at.ac.tuwien.infosys.events.schema.EventCorrelationSet.EventPropertySelector;
import at.ac.tuwien.infosys.events.schema.EventCorrelationSet.EventPropertyValue;
import at.ac.tuwien.infosys.events.schema.XmlSchemaInference.SchemaSet;
import at.ac.tuwien.infosys.events.schema.infer.Node.NodeSet;

public class EventSchemaInferenceTwining extends EventSchemaInference {

	private static final boolean EXTRACT_COMMON_START_EVENT_OF_CHOICE = true;
	private static final boolean VALIDATE_SCHEMA_SIMPLIFICATIONS = false;
	private static final boolean ADD_WILDCARD_NODES = false;

	protected List<LoggedEvent> events = new ArrayList<LoggedEvent>();
	//private List<BaselineNode> roots = new LinkedList<BaselineNode>();
	private List<Node> twineStarts = new LinkedList<Node>();
	private List<Node> twineEnds = new LinkedList<Node>();

	public static interface Constraint {
		boolean satisfied(Element e1, Element e2);
	}
	public static class XPathConstraint implements Constraint {
		String xpath, varName1, varName2;
		public XPathConstraint(String xpath) {
			this(xpath, "e1", "e2");
		}
		public XPathConstraint(String xpath, String varName1, String varName2) {
			this.xpath = xpath;
			this.varName1 = varName1;
			this.varName2 = varName2;
		}
		public boolean satisfied(Element e1, Element e2) {
			Map<String,Object> vars = new HashMap<String,Object>();
			vars.put(varName1, e1);
			vars.put(varName2, e2);
			try {
				return (boolean)(Boolean)XPathProcessor.evaluate(xpath, vars);
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}
	}
	public static class EqualityConstraint extends XPathConstraint {
		public EqualityConstraint(String xpath) {
			this(xpath, xpath);
		}
		public EqualityConstraint(String xpath1, String xpath2) {
			super(
					"boolean(($e1/" + xpath1 + " = $e2/" + xpath2 + ") or ($e1/" + xpath2 + " = $e2/" + xpath1 + "))",
					"e1", "e2"
				);
		}
		public boolean satisfied(Element e1, Element e2) {
			Map<String,Object> vars = new HashMap<String,Object>();
			vars.put(varName1, e1);
			vars.put(varName2, e2);
			try {
				return (boolean)(Boolean)XPathProcessor.evaluate(xpath, vars);
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}
	}
	
	
	public static class Path extends LinkedList<Node> {
		private static final long serialVersionUID = 1L;
		public Path() {}
		public Path(Path copy) {
			super(copy);
		}
	}
	
	public EventSchemaInferenceTwining(EventStream stream, SchemaInferenceConfig cfg) {
		super(stream, cfg);
	}
	
	@Override
	public void addEvent(Element event) throws Exception {
		nextElement(event);
		//splitTwinesIfNecessary();
		//dump();
	}

	@Override
	public SchemaSet getCurrentlyBestEventSchema() {
		//dumpSolution();
		//removeLowPheromones();
		//dumpSolution();
		SchemaSet s = new SchemaSet();
		ESDSchema es = inferSchema();
//		try {
//			s.add(util.toElement(es.toXSD()));
//		} catch (Exception e) {
//			throw new RuntimeException(e);
//		}
		s.add(es.toElement());
		return s;
	}
	

	public ESDSchema inferSchema() {
		ESDSchema s = new ESDSchema();
		ESDChoice c = new ESDChoice();
		s.addChild(c);
		Map<BaselineNode,NodeSet> roots = new HashMap<BaselineNode,NodeSet>();
		for(Node r : twineStarts) {
			if(!roots.containsKey(r.basis)) {
				roots.put(r.basis, new NodeSet());
			}
			roots.get(r.basis).add(r);
		}
		for(NodeSet root : roots.values()) {
			c.addChild(getSequence(/*new NodeSetPath(), */root, new LinkedList<NodeSet>()));
		}
		try {
			System.out.println("Before simplification: " + 
					util.xml.toString(util.xml.toElement(s.toXSD()), true));
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		simplify(s);
		return s;
	}

	private ESDSequence getSequence(/*NodeSetPath pathSoFar, */
			NodeSet thisNodeSet, List<NodeSet> stopAt) {
		
		ESDSequence s = new ESDSequence();
		ESDEvent e = new ESDEvent(thisNodeSet.getBasis().name);
		e.setMaxOccurs("" + thisNodeSet.getMaxMultiplicity());
		e.setMinOccurs(thisNodeSet.getMinMultiplicity());
		s.addChild(e);
		/*pathSoFar.add(thisNodeSet);*/
		
		if(stopAt.contains(thisNodeSet)) {
			return s;
		}
		
		Collection<NodeSet> outgoingNodeSets = thisNodeSet.getOutgoingNodeSets();
		
		//System.out.println(pathSoFar + " out size: " + out.size());
		while(outgoingNodeSets.size() == 1) {
			thisNodeSet = outgoingNodeSets.iterator().next();
			BaselineNode nextNode = thisNodeSet.getBasis();
			if(stopAt.contains(thisNodeSet)) {
				return s;
			}
			ESDEvent e1 = new ESDEvent(nextNode.name);
			e1.setMaxOccurs("" + thisNodeSet.getMaxMultiplicity());
			e1.setMinOccurs(thisNodeSet.getMinMultiplicity());
			s.addChild(e1);
			outgoingNodeSets = thisNodeSet.getOutgoingNodeSets();
		}
		if(outgoingNodeSets.size() > 1) {
			List<List<NodeSet>> accessibles = getAllAccessibleSuccessors(thisNodeSet);
			NodeSet mergeNode = getNextCommonMergeNode(accessibles);
			boolean deadEnd = false;
			
			if(mergeNode != null) {
				//System.out.println("merge node: " + mergeNode.getName());
				if(!stopAt.contains(mergeNode)) {
					stopAt.add(mergeNode);
				}
				deadEnd = hasDeadEndBeforeMergeNode(mergeNode, thisNodeSet);
				//System.out.println("has dead end: " + deadEnd);
			} else if(mergeNode == null) {
				System.out.println("NULL merge node for split node: " + thisNodeSet);
			}
			ESDChoice c = getChoice(/*pathSoFar, */thisNodeSet, stopAt);
			s.addChild(c);
			
			if(mergeNode != null) {
				stopAt.remove(mergeNode);
				ESDChoice choiceAfterMerge = new ESDChoice();
				Collection<NodeSet> nextOutgoing = mergeNode.getOutgoingNodeSets();
				if(nextOutgoing.size() <= 1) {
					ESDSequence seq = getSequence(/*pathSoFar, */mergeNode, stopAt);
					choiceAfterMerge.addChild(seq);
				} else if(nextOutgoing.size() > 1) {
					ESDChoice ch = getChoice(/*pathSoFar, */mergeNode, stopAt);
					choiceAfterMerge.addChild(ch);
				}
				if(deadEnd) {
					choiceAfterMerge.setMinOccurs(0);
				}
				s.addChild(choiceAfterMerge);
			}
		}
		return s;
	}
	
	private ESDChoice getChoice(/*NodeSetPath pathSoFar, */NodeSet thisNodeSet, List<NodeSet> stopAt) {
		ESDChoice c = new ESDChoice();
		Collection<NodeSet> outgoingNodeSets = thisNodeSet.getOutgoingNodeSets();
		
		//List<NodeSetPath> pathsToStopNode = new LinkedList<NodeSetPath>();
		System.out.println("stop at: " + stopAt);
		for(NodeSet s : outgoingNodeSets) {
			//if(!stopAt.contains(s)) {
				System.out.println("branch: " + s);
				c.addChild(getSequence(/*pathSoFar.copy(), */s, stopAt));
			//} else {
				/*NodeSetPath thisPath = new NodeSetPath(pathSoFar);
				thisPath.add(s);
				pathsToStopNode.add(thisPath);*/
				/* add an empty sequence for this choice option! */
			//	System.out.println("stopping: " + s);
			//	c.addChild(new ESDSequence());
			//}
		}
		return c;
	}
	
	boolean hasDeadEndBeforeMergeNode(NodeSet startNode, NodeSet mergeNode) {
		if(startNode == mergeNode)
			return false;
		if(startNode.getOutgoingNodeSets().isEmpty())
			return true;
		for(NodeSet n : startNode.getOutgoingNodeSets()) {
			boolean b = hasDeadEndBeforeMergeNode(n, mergeNode);
			if(b)
				return true;
		}
		return false;
	}

	private NodeSet getNextCommonMergeNode(
			List<List<NodeSet>> accessibleNodes) {
		
		while(!accessibleNodes.isEmpty()) {
			for(List<NodeSet> p1 : new LinkedList<List<NodeSet>>(accessibleNodes)) {
				if(p1.isEmpty()) {
					accessibleNodes.remove(p1);
				} else {
					NodeSet n = p1.get(0);
					boolean containedInAll = true;
					for(List<NodeSet> p2 : accessibleNodes) {
						if(!p2.contains(n)) {
							containedInAll = false;
						}
					}
					if(containedInAll) {
						return n;
					} else {
						for(List<NodeSet> p2 : accessibleNodes) {
							p2.remove(n);
						}
					}
				}
			}
		}
		return null;
	}

	private List<List<NodeSet>> getAllAccessibleSuccessors(NodeSet node) {
		List<List<NodeSet>> accessibles = new LinkedList<List<NodeSet>>();
		for(NodeSet e : node.getOutgoingNodeSets()) {
			// TODO: do not add *all* successors to the list here (potential performance issues)
			accessibles.add(getAccessibleSuccessors(e));
		}
		return accessibles;
	}
	private List<NodeSet> getAccessibleSuccessors(NodeSet node) {
		List<NodeSet> result = new LinkedList<NodeSet>();
		getAccessibleSuccessors(node, result);
		return result;
	}
	private void getAccessibleSuccessors(NodeSet node, List<NodeSet> result) {
		for(NodeSet n : node.getOutgoingNodeSets()) {
			if(!result.contains(n)) {
				result.add(n);
				getAccessibleSuccessors(n, result);
			}
		}
	}
	

	private boolean simplify(ESDObject o) {
		boolean someChanges;
		do {
			someChanges = false;
			for(ESDObject c : o.getChildren()) {
				try {
					String before = o.toXSD();
					someChanges = simplify(c);
					if(someChanges) {
						if(o instanceof ESDSchema) {
							if(VALIDATE_SCHEMA_SIMPLIFICATIONS) {
								System.out.println("Applied simplification. Re-validating.");
								boolean valid = EventSchemaValidator.validate((ESDSchema)o, twineStarts);
								String after = o.toXSD();
								if(!valid) {
									System.out.println("!!! BEFORE !!!");
									util.xml.print(util.xml.toElement(before));
									System.out.println("!!! AFTER !!!");
									util.xml.print(util.xml.toElement(after));
									throw new RuntimeException();
								}
							}
							break;
						} else {
							return true;
						}
					}
				} catch (Throwable e) {
					throw new RuntimeException(e);
				}
			}
		} while(someChanges);
		
		/* single-occurrence sequences within sequences can be concatenated */
		if(o instanceof ESDSequence) {
			for(int i = 0; i < o.getChildren().size() - 1; i ++) {
				if(o.getChildren().get(i) instanceof ESDSequence) {
					if(o.getChildren().get(i + 1) instanceof ESDSequence) {
						ESDObject s1 = o.getChildren().get(i);
						ESDObject s2 = o.getChildren().get(i + 1);
						if(s1.isSingleOccurrence() && s2.isSingleOccurrence()) {
							for(ESDObject o1 : s2.getChildren()) {
								s1.addChild(o1);
							}
							o.removeChild(s2);
							System.out.println("Applied simplification: single-occurrence sequences within sequences can be concatenated");
							return true;
						}
					}
				}
			}
		}

		/* successive ESDObjects in a sequence can be merged by increasing maxOccurs and minOccurs */
		if(o instanceof ESDSequence) {
			for(int i = 0; i < o.getChildren().size() - 1; i ++) {
				ESDObject o1 = o.getChildren().get(i);
				ESDObject o2 = o.getChildren().get(i + 1);
				if(o1.isEqualTo(o2, false)) {
					o1.increaseMinOccurs(o2.getMinOccursAsInt());
					o1.increaseMaxOccurs(o2.getMaxOccursAsInt());
					o.removeChild(o2);
					System.out.println("Applied simplification: ESDObjects in a sequence can be merged by increasing maxOccurs and minOccurs");
					return true;
				}
			}
		}

		/* merge choice options that differ only in the number of occurrences. */
		if(o instanceof ESDChoice) {
			for(int i = 0; i < o.getChildren().size() - 1; i ++) {
				ESDObject o1 = o.getChildren().get(i);
				ESDObject o2 = o.getChildren().get(i + 1);
				if(o1.isEqualTo(o2, false) && !o1.hasEqualOccurrences(o2)) {
					o1.setMinOccurs(Math.min(o1.getMinOccursAsInt(), o2.getMinOccursAsInt()));
					if(o1.getMaxOccursAsInt() < 0 || o2.getMaxOccursAsInt() < 0) {
						o1.setMaxOccurs("unbounded");
					} else {
						o1.setMaxOccurs("" + Math.max(o1.getMaxOccursAsInt(), o2.getMaxOccursAsInt()));
					}
					o.removeChild(o2);
					System.out.println("Applied simplification: merge choice options that differ only in the number of occurrences");
					return true;
				}
			}
		}
		
		/* get rid of single-occurrence sequences within sequences */
		if(o instanceof ESDSequence) {
			for(int i = 0; i < o.getChildren().size(); i ++) {
				ESDObject s = o.getChildren().get(i);
				if(s instanceof ESDSequence && s.isSingleOccurrence()) {
					o.replaceChild(s, s.getChildren());
					System.out.println("Applied simplification: get rid of single-occurrence sequences within sequences");
					return true;
				}
			}
		}

		/* get rid of choice/sequence/all event types with 0 or 1 children... */
		for(ESDObject c : new LinkedList<ESDObject>(o.getChildren())) {
			if((c instanceof ESDChoice || c instanceof ESDSequence || c instanceof ESDAll)) {
				if(c.getChildren().size() == 1) {
					ESDObject repl = c.getChildren().get(0);
					repl.multiplyMaxOccurs(c.getMaxOccursAsInt());
					repl.multiplyMinOccurs(c.getMinOccursAsInt());
					o.replaceChild(c, repl);
					System.out.println("Applied simplification: get rid of choice/sequence/all event types with 1 child...");
					return true;
				} else if(c.getChildren().size() <= 0) {
					o.removeChild(c);
					/* important: if we remove an empty option from a choice, this means
					 * that the whole choice does not need to exist (minOccurs="0"). */
					if(o instanceof ESDChoice) {
						o.setMinOccurs(0);
					}

//					ESDEvent e = new ESDEvent("*");
//					e.setMinOccurs(0);
//					o.replaceChild(c, e);

					System.out.println("Applied simplification: get rid of choice/sequence/all event types with 0 children...");
					return true;
				}
			}
		}
		
		/* if all options of a <choice> start with the same <event> (including leading <any>'s),
		 * this <event> can be put before the choice (within a sequence). */
		if(EXTRACT_COMMON_START_EVENT_OF_CHOICE) {
			for(ESDObject c : new LinkedList<ESDObject>(o.getChildren())) {
				if(c instanceof ESDChoice) {
					List<ESDEvent> firstEvents = new LinkedList<ESDEvent>();
					List<ESDEvent> anyBeforeFirstEvents = new LinkedList<ESDEvent>();
					for(ESDObject o1 : c.getChildren()) {
						ESDEvent firstEvent = ESDObject.getFirstNonAnyEvent(o1);
						if(firstEvent != null) {
							firstEvents.add(firstEvent);
						}
						ESDEvent any = ESDObject.getAnyBeforeFirstEvent(o1);
						if(any != null) {
							anyBeforeFirstEvents.add(any);
						}
					}
					//System.out.println("firstevents: " + firstEvents);
					//System.out.println("anybeforefirstevents: " + anyBeforeFirstEvents);
					
					if(firstEvents.size() == c.getChildren().size()) {
						ESDEvent e = ESDEvent.merge(firstEvents);
						if(e != null) {
							//System.out.println("Merged common choice event: " + e);
							ESDSequence s = new ESDSequence();
							if(!anyBeforeFirstEvents.isEmpty()) {
								s.addChild(ESDEvent.merge(anyBeforeFirstEvents));
							}
							s.addChild(e);
							s.addChild(c);
							for(ESDObject o1 : c.getChildren()) {
								ESDObject.removeAnyPlusFirstEvent(o1);
							}
							o.replaceChild(c, s);
							System.out.println("Applied simplification: EXTRACT_COMMON_START_EVENT_OF_CHOICE: " + e);
							return true;
						}
					}
				}
			}
		}
		

		/* if all options of a <choice> are a <sequence> that starts 
		 * with <any>, put the <any> before the <choice> */
		if(EXTRACT_COMMON_START_EVENT_OF_CHOICE) {
			for(ESDObject c : new LinkedList<ESDObject>(o.getChildren())) {
				if(c instanceof ESDChoice) {
					List<ESDEvent> firstObjects = new LinkedList<ESDEvent>();
					boolean allChildrenSequences = !c.getChildren().isEmpty();
					boolean allGrandChildrenAny = true;
					int maxOccurs = 0;
					int minOccurs = Integer.MAX_VALUE;
					for(ESDObject o1 : c.getChildren()) {
						if(!(o1 instanceof ESDSequence)) {
							allChildrenSequences = false;
							break;
						} else {
							int sizeBefore = firstObjects.size();
							if(!o1.getChildren().isEmpty()) {
								ESDObject c1 = o1.getChildren().get(0);
								if(c1 instanceof ESDEvent && ((ESDEvent)c1).isAnyEvent()) {
									firstObjects.add((ESDEvent)c1);
									if(maxOccurs >= 0 && (c1.getMaxOccursAsInt() < 0 || c1.getMaxOccursAsInt() > maxOccurs))
										maxOccurs = c1.getMaxOccursAsInt();
									if(c1.getMinOccursAsInt() < minOccurs)
										minOccurs = c1.getMinOccursAsInt();
								}
							}
							int sizeAfter = firstObjects.size();
							if(sizeBefore == sizeAfter) {
								allGrandChildrenAny = false;
								break;
							}
						}
					}
					if(allChildrenSequences && allGrandChildrenAny) {
						// remove grandchildren
						ESDObject any = null;
						for(ESDObject o1 : c.getChildren()) {
							any = o1.getChildren().get(0);
							o1.removeChild(any);
						}
						any.setMaxOccurs(maxOccurs < 0 ? "unbounded" : ("" + maxOccurs));
						any.setMinOccurs(minOccurs);
						o.replaceChild(c, Arrays.asList(any, c));
					}
				}
			}
		}
		
		
		return false;
	}

	private boolean twineExists(Set<EventPropertyValue> correlationPropertyValues) {
		for(Node s : twineStarts) {
			if(!twineHasConflict(s, correlationPropertyValues))
				return true;
		}
		return false;
	}

	private boolean hasNonConflictingCorrelationAlongTwine(Node start, 
			Set<EventPropertyValue> correlationPropertyValues) {
		List<EventCorrelationSet> list = new LinkedList<EventCorrelationSet>();
		getNonConflictingCorrelationAlongTwine(start, correlationPropertyValues, list);
		System.out.println("non-conflicting correlations for node " + start.underlyingEvent + " and props " + correlationPropertyValues + " - " + list);
		return !list.isEmpty();
	}
	private void getNonConflictingCorrelationAlongTwine(Node node, 
			Set<EventPropertyValue> correlationPropertyValues,
			List<EventCorrelationSet> resultList) {
		if(node == null)
			return;
		for(EventPropertyValue v1 : node.values) {
			for(EventPropertyValue v2 : correlationPropertyValues) {
				if(!resultList.contains(v1.selector.correlationSet)) {
					if(v1.selector.correlationSet.equals(v2.selector.correlationSet)) {
						System.out.println("equal corr set! values: " + v1.value + " - " + v2.value);
						if(v1.value.equals(v2.value)) {
							resultList.add(v1.selector.correlationSet);
						}
					}
				}
			}
		}
		getNonConflictingCorrelationAlongTwine(node.getSuccessor(), 
				correlationPropertyValues, resultList);
	}
	
	private boolean twineHasConflict(Node start, Set<EventPropertyValue> correlationPropertyValues) {
		if(start == null)
			return false;
		if(haveConflictingCorrelationProps(start, correlationPropertyValues))
			return true;
		return twineHasConflict(start.getSuccessor(), correlationPropertyValues);
	}

	protected boolean haveConflictingCorrelationProps(Node node1, Node node2) {
		return haveConflictingCorrelationProps(node1, node2.values);
	}
	private boolean haveConflictingCorrelationProps(Node n, 
			Set<EventPropertyValue> correlationPropertyValues) {
		for(EventPropertyValue v1 : n.values) {
			for(EventPropertyValue v2 : correlationPropertyValues) {
				if(v1.selector.correlationSet.equals(v2.selector.correlationSet)) {
					if(!v1.value.equals(v2.value)) {
						System.out.println("values are in conflict: " + v1.value + " - " + v2.value);
						return true;
					}
				}
			}
		}
		return false;
	}
	
	private void nextElement(Element next) {
		try {
			next = util.xml.clone(next);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		
		String name = "";
		try {
			name = (String)XPathProcessor.evaluate(config.eventTypeXPath, next);
			//System.out.println("Event type: " + name);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}

		Set<EventPropertySelector> props = 
			EventCorrelationSet.getAllProperties(config.correlationSets);
		props = EventPropertySelector.match(props, next);

		BaselineNode target = BaselineNode.getOrCreate(name, props);

		List<Node> newTwineEnds = new LinkedList<Node>();
		for(Node end : new LinkedList<Node>(twineEnds)) {

			
			Set<EventPropertyValue> propValues = Node.getMatches(next, config.correlationSets);
			System.out.println("prop values: " + propValues);
			
			if(!twineHasConflict(end.getFirstNode(), propValues)) {

				Node to = Node.create(target, next, config.correlationSets);
				
				Edge.getOrCreate(end, to, config.correlationSets);

				newTwineEnds.add(to);
				
			} else {
				
				/* Here we need to distinguish the number of non-conflicting correlations so far.
				 * If there is no non-conflicting correlation so far (case 1), we know that we have a 
				 * completely new instance and we simply add a new twine to the list. Otherwise,
				 * if there are already some non-conflicting correlations (case 2), we know that we 
				 * have reached a branch, and hence we copy the whole existing twine that we have 
				 * travelled along so far and add the new node to the end of this copy. */

				if(hasNonConflictingCorrelationAlongTwine(end.getFirstNode(), propValues)) {
					
					Node startCopy = end.getFirstNode().copy();
					Node endCopy = startCopy.getLastNode();
					twineStarts.add(startCopy);
					newTwineEnds.add(endCopy);
					
				} else {
					
					if(!twineExists(propValues)) {
						System.out.println("twine for element " + next + " does not exist: " + propValues);
						
						Node to = Node.create(target, next, config.correlationSets);
						twineStarts.add(to);
						newTwineEnds.add(to);
					}
				}
				
				/* Finally, add an <any> node to the previous end node (the one whose correlation
				 * properties were conflicting with the new node that was just added). */

				if(ADD_WILDCARD_NODES) {
					if(end instanceof Node.NodeAny) {
						((Node.NodeAny)end).multiplicity++;
						newTwineEnds.add(end);
					} else {
						Node.NodeAny any = new Node.NodeAny(config.correlationSets);
						Edge.getOrCreate(end, any, config.correlationSets);
						newTwineEnds.add(any);
					}
				} else {
					newTwineEnds.add(end);
				}
				
			}
			
		}
		twineEnds.clear();
		twineEnds.addAll(newTwineEnds);
		
		if(twineStarts.isEmpty()) {
			Node n = Node.create(getRoot(name, props), next, config.correlationSets);
			twineStarts.add(n);
			twineEnds.add(n);
		}
		
		//dumpTwines();
		
	}

	
	private BaselineNode getRoot(String name, Set<EventPropertySelector> props) {
		for(Node n : twineEnds) {
			if(name.equals(n.basis.name))
				return n.basis;
		}
		BaselineNode b = BaselineNode.getOrCreate(name, props);
		return b;
	}
	
	@Override
	public List<Node> getTwineStarts() {
		return twineStarts;
	}
	
	

//	private void dumpSolution() {
//		for(Node n : twineStarts) {
//			System.out.println(n.toString());
//		}
//	}
//	
//	protected void dumpTwines() {
//		System.out.println("----");
//		for(Node n : twineStarts) {
//			System.out.println("-- TWINE:");
//			n.dumpTwine();
//		}
//		System.out.println("******");
//	}
//
//	protected void dump() {
//		//dumpTree();
//		dumpSolution();
//		System.out.println("\n-------------------");
//	}


//	private List<BaselineNode> getTwineRoots() {
//		List<BaselineNode> roots = new LinkedList<BaselineNode>();
//		for(Node n : twineStarts) {
//			if(!roots.contains(n.basis))
//				roots.add(n.basis);
//		}
//		return roots;
//	}


//	private void splitTwinesIfNecessary() {
//		for(Node n : new LinkedList<Node>(twineEnds)) {
		
//			Node p = n.getPredecessor();
//			Node immediatePredecessor = p;
//			while(p != null && p.isAnyType()) {
//				p = p.getPredecessor();
//			}
//			if(p != null) {
			
			
//				if(twineHasConflict(n.getFirstNode(), n.values)) {
//					System.out.println("Removing conflicting node from twine: " + n);
//					if(!twineExists(n.values) && !twineStarts.contains(n)) {
//						twineStarts.add(n);
//					}
//					Node any = new Node.NodeAny();
//					Node p = n.getPredecessor();
//					if(p != null) {
//						p.setSuccessor(any, config.correlationSets);
//					}
//					twineEnds.add(any);
//				}
				
				
//			}
		
//		}
//	}

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
//	private boolean haveSameRoot(Node n1, Node n2) {
//		return haveSameRoot(n1.basis, n2.basis);
//	}
//	private boolean haveSameRoot(BaselineNode n1, BaselineNode n2) {
//		BaselineNode r1 = getRoot(n1);
//		BaselineNode r2 = getRoot(n2);
//		return r1 != null && r2 != null && r1.equals(r2);
//	}
//	private boolean hasNoRoot(Node n) {
//		return hasNoRoot(n.basis);
//	}
//	private boolean hasNoRoot(BaselineNode n) {
//		return getRoot(n) == null;
//	}
//	private BaselineNode getRoot(BaselineNode n) {
//		return getRoot(n, new LinkedList<BaselineNode>());
//	}
//	private BaselineNode getRoot(BaselineNode n, List<BaselineNode> visited) {
//		if(visited.contains(n)) {
//			return null;
//		}
//		visited.add(n);
//		if(roots.contains(n)) {
//			return n;
//		}
//		for(BaselineEdge e : n.incoming) {
//			BaselineNode p = e.from;
//			BaselineNode r = getRoot(p, visited);
//			if(r != null) 
//				return r;
//		}
//		return null;
//	}

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
}
