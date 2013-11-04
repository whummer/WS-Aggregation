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

import java.util.HashMap;
import java.util.Map;

import org.w3c.dom.Element;

import at.ac.tuwien.infosys.aggr.xml.XPathProcessor;

public interface Constraint {
	boolean satisfied(Element e1, Element e2);
	
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
}
