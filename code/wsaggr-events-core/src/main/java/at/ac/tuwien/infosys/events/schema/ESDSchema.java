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

import javax.xml.bind.annotation.XmlRootElement;

import org.w3c.dom.Element;

import at.ac.tuwien.infosys.events.schema.XmlSchemaInference.SchemaSet;

@XmlRootElement(name="schema")
public class ESDSchema extends ESDObject {

	public static final String NAMESPACE_URI_ESD = "http://infosys.tuwien.ac.at/ESD";
	
	public String toQuery(QueryOutputConfig cfg) {
		String s = getTemplate("preamble");
		s += "\n" + getTemplate("root");
		s = replaceQueryVariables(s, 1);
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
		s = s.replace("$nextIndex0","$spos");
		return s;
	}
	
	public static ESDSchema toESDSchema(SchemaSet s) {
		Element schema = s.iterator().next();
		try {
			return util.xml.toJaxbObject(ESDSchema.class, schema);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	protected String getElementName() {
		return "schema";
	}
	
	public Element toElement() {
		try {
			return util.xml.toElement(this);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public String toXSD() {
		StringBuilder b = new StringBuilder();
		toXSD(b);
		return b.toString();
	}
	@Override
	public void toXSD(StringBuilder b) {
		String namespace = NAMESPACE_URI_ESD;
		b.append("<schema xmlns=\"http://www.w3.org/2001/XMLSchema\" " +
				"targetNamespace=\"" + namespace + "\" " +
				"xmlns:esd=\"" + namespace + "\"" +
				">");
		for(ESDObject o : getChildren()) {
			String name = getChildren().size() == 1 ? "events" : "esd" + children.indexOf(o);
			b.append("<complexType name=\"" + name + "Type\">");
			o.toXSD(b);
			b.append("</complexType>");
			b.append("<element name=\"" + name + "\" type=\"esd:" + name + "Type\"/>");
		}
		b.append("</schema>");
	}

}
