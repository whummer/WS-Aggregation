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

import io.hummer.util.NotImplementedException;
import io.hummer.util.Util;
import io.hummer.util.ws.EndpointReference;
import io.hummer.util.ws.WebServiceClient;
import io.hummer.util.ws.request.InvocationRequest;
import io.hummer.util.ws.request.InvocationResult;
import io.hummer.util.ws.request.RequestType;

import java.net.URL;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import org.w3c.dom.Element;

public class XmlSchemaInferenceDotNet extends XmlSchemaInference {

	private static Util util = new Util();
	private final String uuid = UUID.randomUUID().toString();
	private final String serverURL = "http://localhost:8876/" + uuid;

	public SchemaSet inferSchemas(List<Element> positiveInstances, List<Element> negativeInstances) {
		try {
			for(Element e : positiveInstances) {
				addPositiveInstance(e);
			}
			for(Element e : negativeInstances) {
				try {
					addNegativeInstance(e);
				} catch (Exception e2) {
					logger.warn("", e2);
				}
			}
			SchemaSet schemas = getCurrentSchema();
			return schemas;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public void addPositiveInstance(Element e) {
		try {
			System.out.println("Adding " + util.xml.toString(e));
			WebServiceClient c = WebServiceClient.getClient(
					new EndpointReference(new URL(serverURL)));
			InvocationRequest request = new InvocationRequest(
					RequestType.HTTP_POST, e);
			c.invoke(request);
		} catch (Exception e1) {
			throw new RuntimeException(e1);
		}
	}

	public void addNegativeInstance(Element e) {
		throw new NotImplementedException();
	}

	public SchemaSet getCurrentSchema() {
		try {
			WebServiceClient c = WebServiceClient.getClient(
					new EndpointReference(new URL(serverURL)));
			InvocationRequest request = new InvocationRequest(
					RequestType.HTTP_GET, null);
			InvocationResult result = c.invoke(request);
			return getSchemas(result.getResultAsElement());
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private SchemaSet getSchemas(Element schemasRoot) throws Exception {
		SchemaSet result = new SchemaSet();
		result.addAll(util.xml.getChildElements(schemasRoot));
        return result;
    }

	public static void main(String[] args) throws Exception {
		List<Element> instances = new LinkedList<Element>();
		instances.add(util.xml.toElement("<foo><bar/></foo>"));
		instances.add(util.xml.toElement("<foo><bar/><bar/></foo>"));
		instances.add(util.xml.toElement("<n:foo xmlns:n=\"foobar\"><bar/><bar/></n:foo>"));
		XmlSchemaInferenceDotNet infer = new XmlSchemaInferenceDotNet();
		SchemaSet schemas = infer.inferSchemas(instances);
		for(Element s : schemas) {
			util.xml.print(s);
		}
	}
}
