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

package at.ac.tuwien.infosys.events.schema;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import javax.xml.bind.annotation.XmlAnyElement;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlMixed;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.XmlType;

import org.w3c.dom.Element;

import at.ac.tuwien.infosys.aggr.xml.XPathProcessor;
import io.hummer.util.Util;

@XmlSeeAlso({
	ESDAll.class, 
	ESDSequence.class, 
	ESDChoice.class, 
	ESDEvent.class,
	ESDObject.ESDObjectWithUnsortedChildren.class})
public abstract class ESDObject {
	
	protected static final Util util = new Util();
	
	@XmlAnyElement
	@XmlMixed
	protected final List<ESDObject> children = new LinkedList<ESDObject>();
	@XmlAttribute
	private Integer minOccurs;
	@XmlAttribute
	private Integer min;
	@XmlAttribute
	private String maxOccurs;
	@XmlAttribute
	private String max;
	
	public static enum QueryType {
		XQUERY
	}

	public static class QueryOutputConfig {
		private AtomicInteger idCounter = new AtomicInteger(0);
		private int fixedID = -1;
		public QueryType type = QueryType.XQUERY;
		public String indentation = "\t";
		public QueryOutputConfig() {}
		public QueryOutputConfig(QueryOutputConfig toCopy) {
			this(toCopy, -1);
		}
		public QueryOutputConfig(QueryOutputConfig toCopy, int counterValue) {
			if(counterValue > 0) {
				this.idCounter.set(counterValue);
			} else {
				this.idCounter = toCopy.idCounter;
			}
			this.type = toCopy.type;
			this.indentation = toCopy.indentation;
		}
		public int getID() {
			fixedID = fixedID > 0 ? fixedID : idCounter.incrementAndGet();
			return fixedID;
		}
	}
	

	@XmlType
	public abstract static class ESDObjectWithUnsortedChildren extends ESDObject {

		@Override
		public boolean isEqualTo(ESDObject obj, boolean considerOccurrences) {
			if(!super.isEqualTo(obj, considerOccurrences))
				return false;
			if(obj.getClass() != getClass())
				return false;
			if(obj.getChildren().size() != getChildren().size())
				return false;
			List<ESDObject> childrenCopy = new LinkedList<ESDObject>(obj.getChildren());
			for(int i = 0; i < getChildren().size(); i ++) {
				boolean found = false;
				for(int j = 0; j < childrenCopy.size(); j ++) {
					if(getChildren().get(i).isEqualTo(childrenCopy.get(j), true)) {
						childrenCopy.remove(j--);
						found = true;
						break;
					}
				}
				if(!found) {
					return false;
				}
			}
			return true;
		}
	}
	

	public String toQuery() {
		return toQuery(new QueryOutputConfig());
	}
	
	public abstract String toQuery(QueryOutputConfig type);
	
	protected abstract String getElementName();
	
	public String toXSD() {
		StringBuilder b = new StringBuilder();
		toXSD(b);
		return b.toString();
	}
	public void toXSD(StringBuilder b) {
		String occurs = "";
		occurs += getMaxOccursAsInt() == 1 ? "" : (" maxOccurs=\"" + getMaxOccurs() + "\"");
		occurs += getMinOccursAsInt() == 1 ? "" : (" minOccurs=\"" + getMinOccursAsInt() + "\"");
		b.append("<" + getElementName() + occurs + ">");
		for(ESDObject o : getChildren()) {
			o.toXSD(b);
		}
		b.append("</" + getElementName() + ">");
	}

	public void addChild(ESDObject o) {
		children.add(o);
	}
	public void removeChild(ESDObject o) {
		children.remove(o);
	}
	public void replaceChild(ESDObject oldObj, ESDObject newObj) {
		replaceChild(oldObj, Arrays.asList(newObj));
	}
	public void replaceChild(ESDObject oldObj, List<ESDObject> newObjs) {
		//System.out.println("replacing " + oldObj + " with " + newObjs + " in parent " + this);
		int index = children.indexOf(oldObj);
		children.remove(index);
		for(int i = newObjs.size() - 1; i >= 0; i --) {
			children.add(index, newObjs.get(i));
		}
	}
	
	
	protected String replaceQueryVariables(String query, int id) {
		query = query.replace("##self.maxOccurs##", "" + getMaxOccursAsInt());
		query = query.replace("##self.minOccurs##", "" + getMinOccursAsInt());
		query = query.replace("##self.name##", (this instanceof ESDEvent) ? ((ESDEvent)this).getType() : "");
		ESDEvent next = getNextChildEvent();
		query = query.replace("##next.name##", next != null ? next.getType() : ".*");
		query = query.replace("##ID##", ""+id);
		query = query.replace("##ID-1##", ""+(id-1));
		return query;
	}
	
	protected String getTemplate(String type) {
		try {
			Element e = util.xml.toElement(util.io.readFile(getClass()
					.getResourceAsStream("esdQueryParticles.xquery.xml")));
			List<?> list = XPathProcessor.evaluateAsList("//particle", e);
			for(Object o : list) {
				Element el = (Element)o;
				if(type.equals(el.getAttribute("type"))) {
					return el.getTextContent();
				}
			}
			return null;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
	
	public ESDEvent getNextChildEvent() {
		if(getChildren().isEmpty())
			return null;
		ESDObject next = getChildren().get(0);
		if(next instanceof ESDEvent)
			return (ESDEvent)next;
		return next.getNextChildEvent();
	}
	
	public List<ESDObject> getChildren() {
		for(Object o : new LinkedList<ESDObject>(children)) {
			if(!(o instanceof ESDObject)) {
				children.remove(o);
				if(o instanceof Element) {
					try {
						children.add(util.xml.toJaxbObject(ESDObject.class, (Element)o));
					} catch (Exception e) {
						throw new RuntimeException(e);
					}
				}
			}
		}
		return children;
	}

	public int getMinOccursAsInt() {
		return minOccurs != null ? minOccurs : min != null ? min : 1;
	}
	public int getMaxOccursAsInt() {
		return 	maxOccurs != null ? 
					(maxOccurs.equals("unbounded") ? -1 : Integer.parseInt(maxOccurs)) : 
				max != null ? 
					(max.equals("unbounded") ? -1 : Integer.parseInt(max)) : 
				1;
	}
	public void setMinOccurs(Integer minOccurs) {
		this.minOccurs = minOccurs;
	}
	public void setMaxOccurs(String maxOccurs) {
		this.maxOccurs = maxOccurs;
	}
	@XmlTransient
	public String getMaxOccurs() {
		return maxOccurs;
	}

	@Override
	public String toString() {
		return "[ESD name=" + getElementName() + "]";
	}

	public static ESDEvent getFirstNonAnyEvent(ESDObject o) {
		if(o instanceof ESDEvent && ((ESDEvent)o).isAnyEvent()) {
			return null;
		}
		if(o instanceof ESDEvent && !((ESDEvent)o).isAnyEvent()) {
			return (ESDEvent)o;
		}
		for(ESDObject c : o.getChildren()) {
			ESDEvent e = getFirstNonAnyEvent(c);
			if(e != null) {
				return e;
			}
		}
		return null;
	}

	public boolean isSingleOccurrence() {
		return getMaxOccursAsInt() == 1 && getMinOccursAsInt() == 1;
	}

	public void multiplyMaxOccurs(int factor) {
		if(getMaxOccursAsInt() < 0) // already "unbounded"
			return;
		setMaxOccurs("" + (getMaxOccursAsInt() * factor));
	}
	public void multiplyMinOccurs(int factor) {
		if(getMinOccursAsInt() < 0) // should not happen
			return;
		setMinOccurs((getMinOccursAsInt() * factor));
	}
	public void increaseMaxOccurs() {
		increaseMaxOccurs(1);
	}
	public void increaseMaxOccurs(int addendum) {
		if(getMaxOccursAsInt() < 0) // already "unbounded"
			return;
		if(addendum < 0)
			setMaxOccurs("unbounded");
		setMaxOccurs("" + (getMaxOccursAsInt() + addendum));
	}
	public void increaseMinOccurs(int addendum) {
		if(getMinOccursAsInt() < 0 || addendum < 0) // should not happen
			return;
		setMinOccurs(getMinOccursAsInt() + addendum);
	}


	public ESDObject getChildAfter(ESDObject o) {
		int index = getChildren().indexOf(o);
		if(index < 0 || getChildren().size() <= index + 1)
			return null;
		return getChildren().get(index + 1);
	}

	public static ESDEvent getAnyBeforeFirstEvent(ESDObject o) {
		return getAnyBeforeFirstEvent(o, false);
	}
	public static ESDEvent removeAnyPlusFirstEvent(ESDObject o) {
		return getAnyBeforeFirstEvent(o, true);
	}
	private static ESDEvent getAnyBeforeFirstEvent(ESDObject o, boolean remove) {
		if(o instanceof ESDEvent && ((ESDEvent)o).isAnyEvent()) {
			return null;
		}
		if(o instanceof ESDEvent && !((ESDEvent)o).isAnyEvent()) {
			return (ESDEvent)o;
		}
		int index = 0;
		for(ESDObject c : new LinkedList<ESDObject>(o.getChildren())) {
			if(c instanceof ESDEvent && !((ESDEvent)c).isAnyEvent()) {
				ESDEvent toReturn = null;
				if(index > 0) {
					ESDObject before = o.getChildren().get(index - 1);
					if(before instanceof ESDEvent && ((ESDEvent)before).isAnyEvent()) {
						toReturn = (ESDEvent)before;
					}
				}
				if(remove) {
					for(int i = 0; i <= index; i ++) {
						o.removeChild(o.getChildren().get(0));
					}
				}
				return toReturn;
			}
			ESDEvent e = getAnyBeforeFirstEvent(c, remove);
			if(e != null) {
				return e;
			}
			index ++;
		}
		return null;
	}

	public boolean isEqualTo(ESDObject obj) {
		return isEqualTo(obj, true);
	}
	public boolean isEqualTo(ESDObject obj, boolean considerOwnOccurrences) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		ESDObject other = (ESDObject) obj;
		if (children == null) {
			if (other.children != null)
				return false;
		}
		if(considerOwnOccurrences) {
			if(!hasEqualOccurrences(other))
				return false;
		}
		return true;
	}
	
	public boolean hasEqualOccurrences(ESDObject other) {

		if (max == null) {
			if (other.max != null)
				return false;
		} else if (!max.equals(other.max))
			return false;
		if (maxOccurs == null) {
			if (other.maxOccurs != null)
				return false;
		} else if (!maxOccurs.equals(other.maxOccurs))
			return false;
		if (min == null) {
			if (other.min != null)
				return false;
		} else if (!min.equals(other.min))
			return false;
		if (minOccurs == null) {
			if (other.minOccurs != null)
				return false;
		} else if (!minOccurs.equals(other.minOccurs))
			return false;

		return true;
	}

	public static boolean allEqual(List<? extends ESDObject> objects) {
		for(int i = 0; i < objects.size(); i ++) {
			for(int j = i + 1; j < objects.size(); j ++) {
				if(!objects.get(i).isEqualTo(objects.get(j)))
					return false;
			}
		}
		return true;
	}
	

	
	@Override
	public final boolean equals(Object obj) {
		// do *not* override equals(..), because we might need identity equality 
		// in some parts of the schema inferrence logic (e.g., if certain 
		// ESDObjects are to be removed from Lists).
		return super.equals(obj);
	}
}
