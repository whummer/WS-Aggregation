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

import java.util.LinkedList;
import java.util.List;

import org.apache.xmlbeans.XmlException;
import org.apache.xmlbeans.XmlObject;
import org.w3c.dom.Element;

import io.hummer.util.NotImplementedException;
import io.hummer.util.Util;

import com.eviware.soapui.impl.wadl.inference.InferredSchema;
import com.eviware.soapui.impl.wadl.inference.support.InferredSchemaImpl;

public class XmlSchemaInferenceSOAPUI extends XmlSchemaInference {

	private static Util util = new Util();
	private InferredSchema schema = new InferredSchemaImpl(); 
	
	
	public SchemaSet inferSchemas(List<Element> positiveInstances, List<Element> negativeInstances) {
		InferredSchema s = new InferredSchemaImpl(); 
		try {
			for(Element e : positiveInstances) {
				s.processValidXml(XmlObject.Factory.parse(e));
			}
			SchemaSet schemas = getSchemas(s);
			return schemas;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public void addPositiveInstance(Element e) {
		try {
			System.out.println("Adding " + util.xml.toString(e));
			schema.processValidXml(XmlObject.Factory.parse(e));
		} catch (XmlException e1) {
			throw new RuntimeException(e1);
		}
	}

	public void addNegativeInstance(Element e) {
		throw new NotImplementedException();
	}

	public SchemaSet getCurrentSchema() {
		try {
			return getSchemas(schema);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public SchemaSet getSchemas(InferredSchema s) throws Exception {
		SchemaSet result = new SchemaSet();
        for(String namespace : s.getNamespaces()) {
        	String src = s.getXsdForNamespace(namespace).toString();
        	result.add(util.xml.toElement(src));
        }
        return result;
    }

	public static void main(String[] args) throws Exception {
		List<Element> instances = new LinkedList<Element>();
		instances.add(util.xml.toElement("<foo><bar/></foo>"));
		instances.add(util.xml.toElement("<foo><bar/><bar/></foo>"));
		instances.add(util.xml.toElement("<n:foo xmlns:n=\"foobar\"><bar/><bar/></n:foo>"));
		XmlSchemaInferenceSOAPUI infer = new XmlSchemaInferenceSOAPUI();
		SchemaSet schemas = infer.inferSchemas(instances);
		for(Element s : schemas) {
			util.xml.print(s);
		}
	}
}
