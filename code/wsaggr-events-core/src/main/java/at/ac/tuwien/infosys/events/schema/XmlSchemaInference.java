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

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;

import org.apache.log4j.Logger;
import org.w3c.dom.Element;

import at.ac.tuwien.infosys.util.Util;

public abstract class XmlSchemaInference {

	private static Util util = new Util();
	protected static Logger logger = Util.getLogger(XmlSchemaInference.class);
	
	public static class SchemaSet extends HashSet<Element> {
		private static final long serialVersionUID = 1L;
		@Override
		public String toString() {
			Element e = util.xml.toElementSafe("<schemas/>");
			for(Element c : this) {
				try {
					util.xml.appendChild(e, c);
				} catch (Exception e1) {
					throw new RuntimeException(e1);
				}
			}
			return util.xml.toString(e, true);
		}
	}
	
	public SchemaSet inferSchemas(List<Element> instances) {
		return inferSchemas(instances, new LinkedList<Element>());
	}

	public abstract SchemaSet inferSchemas(List<Element> positiveInstances, List<Element> negativeInstances);

	public abstract void addPositiveInstance(Element e);
	
	public abstract void addNegativeInstance(Element e);
	
	public abstract SchemaSet getCurrentSchema();

}
