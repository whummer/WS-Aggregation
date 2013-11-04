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

import java.util.Comparator;

import org.apache.log4j.Logger;
import org.w3c.dom.Element;

import at.ac.tuwien.infosys.aggr.xml.XPathProcessor;
import at.ac.tuwien.infosys.events.schema.XmlSchemaInference.SchemaSet;
import at.ac.tuwien.infosys.util.Util;

public class SchemaQualityComparator implements Comparator<SchemaSet> {

	private static Logger logger = Util.getLogger(SchemaQualityComparator.class);

	@Override
	public int compare(SchemaSet o1, SchemaSet o2) {
		Long c1 = getNumAnyElements(o1) + getNumElements(o1);
		Long c2 = getNumAnyElements(o2) + getNumElements(o2);
		return c1.compareTo(c2);
	}

	protected long getNumAnyElements(SchemaSet schemas) {
		return selectCount(schemas, "count(//*:any)");
	}
	protected long getNumAnyElements(Element schema) {
		return selectCount(schema, "count(//*:any)");
	}
	protected long getNumElements(SchemaSet schemas) {
		return selectCount(schemas, "count(//*:element)");
	}
	protected long getNumElements(Element schema) {
		return selectCount(schema, "count(//*:element)");
	}

	private long selectCount(SchemaSet schemas, String xpath) {
		long c = 0;
		for(Element element : schemas) {
			c += selectCount(element, xpath);
		}
		return c;
	}
	private long selectCount(Element element, String xpath) {
		try {
			return (long)(double)(Double)XPathProcessor.evaluate(xpath, element);
		} catch (Exception e) {
			logger.warn("Unable to select element from schema.", e);
			return 0;
		}
	}
}
