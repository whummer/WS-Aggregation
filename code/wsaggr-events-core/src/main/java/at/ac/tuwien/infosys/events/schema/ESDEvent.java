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

import java.util.List;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name="event")
public class ESDEvent extends ESDObject {

	@XmlAttribute
	private String type;

	public ESDEvent() {}
	public ESDEvent(String type) {
		this.type = type;
	}

	public String toQuery(QueryOutputConfig cfg) {
		int id = cfg.getID();
		String s = getTemplate(getElementName());
		s = replaceQueryVariables(s, id);
		String children = "";
		for(Object o : getChildren()) {
			if(o instanceof ESDObject) {
				ESDObject c = (ESDObject)o;
				QueryOutputConfig cfg1 = new QueryOutputConfig(cfg);
				children += c.toQuery(cfg1);
			}
		}
		if(children.isEmpty()) {
			children = "true()";
		}
		s = s.replace("##self.children##", children);
		s = s.replace("\n", "\n" + cfg.indentation);
		return s;
	}

	public String getType() {
		return type;
	}

	protected String getElementName() {
		return "event";
	}

	@Override
	public void toXSD(StringBuilder b) {
		String occurs = "";
		occurs += getMaxOccursAsInt() == 1 ? "" : " maxOccurs=\"" + getMaxOccurs() + "\"";
		occurs += getMinOccursAsInt() == 1 ? "" : " minOccurs=\"" + getMinOccursAsInt() + "\"";
		if(isAnyEvent()) {
			b.append("<any" + occurs + " processContents=\"lax\"/>");
		} else {
			b.append("<element name=\"" + type +"\"" + occurs + " type=\"anyType\"/>");
		}
	}
	public boolean isAnyEvent() {
		return type.trim().equals("*");
	}
	public static ESDEvent merge(List<ESDEvent> eventTypes) {
		if(eventTypes.isEmpty() || !ESDObject.allEqual(eventTypes)) {
			return null;
		}
		ESDEvent e = new ESDEvent();
		e.type = eventTypes.get(0).type;
		e.setMaxOccurs("" + getMaxOccurs(eventTypes));
		e.setMinOccurs(getMinOccurs(eventTypes));
		return e;
	}

	private static int getMinOccurs(List<ESDEvent> eventTypes) {
		int min = Integer.MAX_VALUE;
		for(ESDEvent e : eventTypes) {
			if(e.getMinOccursAsInt() < min)
				min = e.getMinOccursAsInt();
		}
		return min;
	}
	private static int getMaxOccurs(List<ESDEvent> eventTypes) {
		int max = 0;
		for(ESDEvent e : eventTypes) {
			if(e.getMaxOccursAsInt() > max)
				max = e.getMaxOccursAsInt();
		}
		return max;
	}

	@Override
	public String toString() {
		return super.toString().replace("]", " type='" + type + "']");
	}

	@Override
	public boolean isEqualTo(ESDObject obj, boolean considerOccurrences) {
		if(!super.isEqualTo(obj, considerOccurrences))
			return false;
		if(!(obj instanceof ESDEvent))
			return false;
		ESDEvent e = (ESDEvent)obj;
		return this.getType().equals(e.getType());
	}
}
