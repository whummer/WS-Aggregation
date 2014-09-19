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
package at.ac.tuwien.infosys.aggr.request;

import io.hummer.util.Util;
import io.hummer.util.ws.EndpointReference;

import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import org.apache.log4j.Logger;
import org.w3c.dom.Element;

import at.ac.tuwien.infosys.events.ws.EventSubscribeFilter;

@XmlRootElement(name=EventingInput.JAXB_ELEMENT_NAME, namespace="")
@XmlJavaTypeAdapter(AbstractInput.Adapter.class)
public class EventingInput extends NonConstantInput {
	
	public static final String JAXB_ELEMENT_NAME = "subscribe";
	
	protected static final Logger logger = Util.getLogger(EventingInput.class);
	
	private boolean dontSubscribe;
	
	public EventingInput() { }
	public EventingInput(Element message) {
		super(message);
	}
	public EventingInput(EventingInput toClone) {
		copyFrom(toClone);
	}
	public void copyFrom(EventingInput toClone) {
		super.copyFrom(toClone);
		this.dontSubscribe = toClone.dontSubscribe;
	}
	@Override
	@SuppressWarnings("all")
	public <T extends AbstractInput> T copy() throws Exception {
		EventingInput c = new EventingInput(this);
		return (T)c;
	}

	public EventSubscribeFilter getFilter() throws Exception {
		Util util = new Util();
		if(getTheContent() == null)
			return null;
		for(Element e : util.xml.getChildElements(getTheContentAsElement())) {
			if(e.getLocalName().equals("Filter")) {
				return util.xml.toJaxbObject(EventSubscribeFilter.class, e);
			}
		}
		return null;
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
			}
		}
		return null;
	}

	public String getFilterToString() throws Exception {
		Util util = new Util();
		EventSubscribeFilter f = getFilter();
		if(f == null)
			return "";
		return util.xml.toString(f);
	}
	
	@Override
	public int hashCode() {
		int hc = 0;
		if(getServiceEPR() != null) hc += getServiceEPR().hashCode();
		if(getServiceURL() != null) hc += getServiceURL().hashCode();
		EventSubscribeFilter f;
		try {
			f = getFilter();
			if(f != null) hc += f.hashCode();
		} catch (Exception e) { }
		return hc;
	}
	
	@Override
	public boolean equals(Object o) {
		boolean eq = true;
		if(!(o instanceof EventingInput))
			return false;
		EventingInput e = (EventingInput)o;
		if(e.getServiceEPR() != null) eq &= e.getServiceEPR().equals(getServiceEPR());
		if(e.getServiceURL() != null) eq &= e.getServiceURL().equals(getServiceURL());
		EventSubscribeFilter f;
		try {
			f = getFilter();
			if(f != null) eq &= f.equals(e.getFilter());
		} catch (Exception e1) { }
		return eq;
	}
	/**
	 * @return the dontSubscribe
	 */
	public boolean isDontSubscribe() {
		return dontSubscribe;
	}
	/**
	 * @param dontSubscribe the dontSubscribe to set
	 */
	public void setDontSubscribe(boolean dontSubscribe) {
		this.dontSubscribe = dontSubscribe;
	}
	
}
