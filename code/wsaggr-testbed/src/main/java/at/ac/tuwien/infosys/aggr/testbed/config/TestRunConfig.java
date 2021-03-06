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

/**
 * 
 */
package at.ac.tuwien.infosys.aggr.testbed.config;

import io.hummer.util.Configuration;
import io.hummer.util.xml.XMLUtil;

import javax.xml.bind.annotation.XmlAnyElement;
import javax.xml.bind.annotation.XmlAttribute;

import org.w3c.dom.Element;

import at.ac.tuwien.infosys.aggr.request.AggregationRequest;

/**
 * @author Daniel Domberger
 *
 */
public class TestRunConfig {
	@XmlAttribute
	public int parallelInstances;
	
	@XmlAnyElement
	public Object request;
	
	public AggregationRequest getAggregationRequest() throws Exception {
		return XMLUtil.getInstance().toJaxbObject(AggregationRequest.class, (Element) request);
	}
	
	public AggregationRequest createAggregationRequest() throws Exception {
		XMLUtil xmlUtil = new XMLUtil();
		if(request instanceof Element) {
			Element e = (Element)request;
			if(e.getNodeName().endsWith("createTopology")) {
				e = xmlUtil.getChildElements(e, "request").get(0);
				e = xmlUtil.changeRootElementName(e, "tns:aggregate xmlns:tns=\"" + Configuration.NAMESPACE + "\"");
			}
			if(e.getNodeName().endsWith("aggregate")) {
				return xmlUtil.toJaxbObject(AggregationRequest.class, e);
			}
		}
		throw new RuntimeException("Unexpected request type: " + request);
	}
}
