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

package at.ac.tuwien.infosys.monitoring.config;

import io.hummer.util.Util;
import io.hummer.util.ws.EndpointReference;

import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import org.w3c.dom.Element;

import at.ac.tuwien.infosys.aggr.request.AbstractInput;
import at.ac.tuwien.infosys.aggr.request.NonConstantInput;

@XmlRootElement(name=NodeConfigInput.JAXB_ELEMENT_NAME)
@XmlJavaTypeAdapter(AbstractInput.Adapter.class)
public class NodeConfigInput extends NonConstantInput {	
													
	public static final String JAXB_ELEMENT_NAME = "nodeConfigIn";
	
	public NodeConfigInput(){	
	}
	
	public NodeConfigInput(Element element) {
		super(element);
	}

	public NodeConfigInput(NodeConfigInput toClone) {
		copyFrom(toClone);
	}

	@Override
	@SuppressWarnings("all")
	public <T extends AbstractInput> T copy() throws Exception {
		NodeConfigInput c = new NodeConfigInput(this);
		return (T)c;
	}

	public EndpointReference getEndTo() throws Exception {
		Util util = new Util();
		if(getTheContent() == null)
			return null;
		for(Element e : util.xml.getChildElements(getTheContentAsElement())) {
			if(e.getLocalName().equals("endTo")) {
				e = util.xml.changeRootElementName(e, EndpointReference.NS_WS_ADDRESSING,
						"EndpointReference");
				return util.xml.toJaxbObject(EndpointReference.class, e);
			} else if(e.getLocalName().equals("EndpointReference")){
				return util.xml.toJaxbObject(EndpointReference.class, e);
			}
		}
		return null;
	}

	
}
