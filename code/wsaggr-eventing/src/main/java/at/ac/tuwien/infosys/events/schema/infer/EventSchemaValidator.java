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

import java.util.LinkedList;
import java.util.List;

import javax.xml.transform.Source;
import javax.xml.transform.dom.DOMSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;

import org.w3c.dom.Element;

import at.ac.tuwien.infosys.events.schema.ESDAll;
import at.ac.tuwien.infosys.events.schema.ESDChoice;
import at.ac.tuwien.infosys.events.schema.ESDEvent;
import at.ac.tuwien.infosys.events.schema.ESDObject;
import at.ac.tuwien.infosys.events.schema.ESDSchema;
import at.ac.tuwien.infosys.events.schema.ESDSequence;
import at.ac.tuwien.infosys.events.schema.XmlSchemaInference.SchemaSet;
import at.ac.tuwien.infosys.util.Util;

public class EventSchemaValidator {

	private static Util util = new Util();
	private static final boolean VALIDATE_USING_XSD = true;

	private static class NodePosition {
		NodePosition parent;
		ESDObject schemaEl;
		int position = 1;
		NodePosition() {}
		NodePosition(NodePosition parent, ESDObject schemaElement) {
			this(parent, schemaElement, 1);
		}
		NodePosition(NodePosition parent, ESDObject schemaElement, int position) {
			this.schemaEl = schemaElement;
			this.parent = parent;
			this.position = position;
		}
		
		NodePosition nextPosition() {
			NodePosition p = new NodePosition();
			p.schemaEl = schemaEl;
			p.position = position + 1;
			p.parent = parent;
			return p;
		}
		@Override
		public String toString() {
			return "[P " + schemaEl + ", pos=" + position + "]";
		}
	}
	
	public static boolean validate(SchemaSet schemas, List<Node> twineStarts) {
		if(schemas.size() != 1)
			throw new IllegalArgumentException("Expected a single schema, got: " + schemas.size());
		try {
			ESDSchema schema = ESDSchema.toESDSchema(schemas);
			if(VALIDATE_USING_XSD) {
				return validateUsingXSD(schema, twineStarts);
			} else {
				return validate(schema, twineStarts);
			}
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	protected static boolean validate(ESDSchema schema, List<Node> twineStarts) {
		EventSchemaValidator v = new EventSchemaValidator();
		for(Node twineStart : twineStarts) {
			List<NodePosition> currentPositions = new LinkedList<NodePosition>();
			currentPositions.add(new NodePosition(null, schema));
			Node currentNode = twineStart;
			while(currentNode != null) {
//				System.out.println("Looking for nodes reachable from here: " + 
//						currentPositions + " -> " + currentNode.basis.name);
				currentPositions = v.getAllMatchingNextPositions(currentPositions, currentNode);
				if(currentPositions.isEmpty()) {
					System.out.println("Could not validate path against schema. current node: " + currentNode.basis.name);
					System.out.println("Path so far: " + currentNode.getPathFromTwineStart());
					System.out.println("There are " + twineStarts.size() + " twines");
					for(Node n1 : twineStarts) {
						System.out.println(util.xml.toString(n1.underlyingEvent));
					}
					
					System.out.println("problem was starting here:");
					if(currentNode.getFirstNode().underlyingEvent != null) {
						System.out.println(util.xml.toString(currentNode.getFirstNode().underlyingEvent));
					}
					return false;
				}
				currentNode = currentNode.getSuccessor();
			}
			//System.out.println("!!! twine " + (twineStarts.indexOf(twineStart) + 1) + 
			//		" of " + twineStarts.size() + " was validated successfully!");
		}
		System.out.println("There are " + twineStarts.size() + " twines");
		for(Node n1 : twineStarts) {
			System.out.println(util.xml.toString(n1.underlyingEvent));
		}
		return true;
	}
	
	private static boolean validateUsingXSD(ESDSchema schema, List<Node> twineStarts) {
		SchemaFactory factory = 
			SchemaFactory.newInstance("http://www.w3.org/2001/XMLSchema");
		try {
			factory.setFeature("http://apache.org/xml/features/validation/schema-full-checking", false);
			
			Element el = util.xml.toElement(schema.toXSD());
			System.out.println("Validating schema:");
			util.xml.print(el);
			Schema xsd = factory.newSchema(new DOMSource(el));
			Validator validator = xsd.newValidator();
			
			for(Node n : twineStarts) {
				Element e = n.constructSequenceAsXML();
				Source source = new DOMSource(e);
				try {
				    validator.validate(source);
				} catch (Exception ex) {
				    System.out.println("Element is not valid:");
				    util.xml.print(e);
				    return false;
				}  
			}
			return true;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private List<NodePosition> getAllMatchingNextPositions(List<NodePosition> pos, Node nextNode) {
		List<NodePosition> result = new LinkedList<NodePosition>();
		for(NodePosition p : pos) {
			result.addAll(getMatchingNextPositions(p, nextNode));
		}
		return result;
	}
	
	private List<NodePosition> getMatchingNextPositions(NodePosition pos, Node nextNode) {
		List<NodePosition> result = new LinkedList<NodePosition>();
		for(NodePosition next : getNextPositionsFromHere(pos)) {
			//System.out.println("reachable node from here: " + next.schemaEl + " - matches " + 
			//		nextNode.basis.name + ": " + nodeMatches(nextNode, (ESDEvent)next.schemaEl));
			if(nodeMatches(nextNode, (ESDEvent)next.schemaEl)) {
				result.add(next);
			}
		}
		return result;
	}
	
	private boolean nodeMatches(Node node, ESDEvent eventType) {
		if(eventType.isAnyEvent())
			return true;
		if(node.basis.name.equals(eventType.getType()) || 
				node.basis.name.matches(eventType.getType()))
			return true;
		return false;
	}

	private List<NodePosition> getNextPositionsFromHere(NodePosition pos) {
		return getNextPositionsFromHere(pos, false);
	}
	private List<NodePosition> getNextPositionsFromHere(NodePosition pos, boolean isBacktrackDirection) {
		List<NodePosition> result = new LinkedList<NodePosition>();
		
		if(isBacktrackDirection) {
			if(pos == null || pos.parent == null)
				return result;

			//System.out.println("parent: " + pos.parent.schemaEl);
			if(pos.schemaEl.getMaxOccursAsInt() > pos.position) {
				result.addAll(getNextPositionsFromHere(pos.nextPosition(), false));
			}
			
			if(pos.parent.schemaEl instanceof ESDSequence) {
				ESDObject c = pos.parent.schemaEl.getChildAfter(pos.schemaEl);
				if(c != null) {
					//System.out.println("!! jumping to next sibling after backtrack: " + c);
					result.addAll(getNextPositionsFromHere(new NodePosition(pos.parent, c), false));
				}
			}

			//System.out.println("Backtracking to parent 0 " + pos.parent);
			result.addAll(getNextPositionsFromHere(pos.parent, true));
			return result;
		}
		
		if(pos.parent != null) {
			if(pos.parent.schemaEl instanceof ESDSequence) {
				ESDObject next = pos.parent.schemaEl.getChildAfter(pos.schemaEl);
				while(next != null && next.getMinOccursAsInt() == 0) {
					result.addAll(getNextPositionsFromHere(
						new NodePosition(pos.parent, next), false));
					next = pos.parent.schemaEl.getChildAfter(next);
				}
			}
		}
		if(pos.schemaEl.getMaxOccursAsInt() > pos.position) {
			result.add(pos.nextPosition());
		}
		//if(pos.schemaEl instanceof ESDEvent) {
			
			if(pos.position >= pos.schemaEl.getMinOccursAsInt() && pos.parent != null) {
				if(pos.parent.schemaEl instanceof ESDSequence) {
					boolean added = false;
					ESDObject c = pos.parent.schemaEl.getChildAfter(pos.schemaEl);
					//System.out.println("next object after '" + pos.schemaEl + "': " + c);
					while(c != null) {
						added = true;
						if(c instanceof ESDEvent) {
							result.add(new NodePosition(pos.parent, c));
						} else {
							result.addAll(getNextPositionsFromHere(
								new NodePosition(pos.parent, c), false));
						}
						if(c.getMinOccursAsInt() == 0) {
							c = pos.parent.schemaEl.getChildAfter(c);
						} else {
							c = null;
						}
					} 
					
					if(!added) {
						/* backtrack! */
						//System.out.println("Backtracking to parent 1 " + pos.parent);
						result.addAll(getNextPositionsFromHere(pos.parent, true));
					}
				} else {
					/* backtrack! */
					//System.out.println("Backtracking to parent 2 " + pos.parent);
					result.addAll(getNextPositionsFromHere(pos.parent, true));
				}
			}
			
			
		if(pos.schemaEl instanceof ESDEvent) {
			return result;
		}
		
		if(pos.schemaEl instanceof ESDChoice || pos.schemaEl instanceof ESDSchema) {
			//System.out.println("type: " + pos.schemaEl);
			for(ESDObject c : pos.schemaEl.getChildren()) {
				if(c instanceof ESDEvent) {
					result.add(new NodePosition(pos, c));
				} else {
					result.addAll(getNextPositionsFromHere(
						new NodePosition(pos, c)));
				}
			}
		} else if(pos.schemaEl instanceof ESDSequence) {
			if(!pos.schemaEl.getChildren().isEmpty()) {
				ESDObject c = pos.schemaEl.getChildren().get(0);
				if(c instanceof ESDEvent) {
					result.add(new NodePosition(pos, c));
				} else {
					result.addAll(getNextPositionsFromHere(
						new NodePosition(pos, c)));
				}
			}
		} else if(pos.schemaEl instanceof ESDAll) {
			// TODO!
		}

		if(pos.parent != null) {
			ESDObject finalNext = pos.parent.schemaEl.getChildAfter(pos.schemaEl);
			if(finalNext == null) {
				/* backtrack! */
				result.addAll(getNextPositionsFromHere(pos.parent, true));
			}
		}
		
		return result;
	}

}
