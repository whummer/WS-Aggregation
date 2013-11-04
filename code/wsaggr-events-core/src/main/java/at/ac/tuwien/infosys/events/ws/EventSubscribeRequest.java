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

package at.ac.tuwien.infosys.events.ws;

import java.util.Date;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.XmlTransient;

import at.ac.tuwien.infosys.ws.EndpointReference;

@XmlRootElement(name = "Subscribe", namespace = WSEvent.NAMESPACE)
@XmlSeeAlso(EventSubscribeResponse.class)
public class EventSubscribeRequest {

	@XmlElement(name = "EndTo", namespace = WSEvent.NAMESPACE)
	private EndpointReference endTo;

	@XmlElement(name = "Delivery", namespace = WSEvent.NAMESPACE)
	private Object delivery;

	@XmlElement(name = "Expires", namespace = WSEvent.NAMESPACE)
	private Date expires;

	@XmlElement(name = "Filter", namespace = WSEvent.NAMESPACE)
	private EventSubscribeFilter filter;
	
	@XmlTransient
	public Object getDelivery() {
		return delivery;
	}
	
	public void setDelivery(Object delivery) {
		this.delivery = delivery;
	}
	
	@XmlTransient
	public EndpointReference getEndTo() {
		return endTo;
	}

	public void setEndTo(EndpointReference endTo) {
		this.endTo = endTo;
	}

	@XmlTransient
	public Date getExpires() {
		return expires;
	}

	public void setExpires(Date expires) {
		this.expires = expires;
	}

	@XmlTransient
	public EventSubscribeFilter getFilter() {
		return filter;
	}

	public void setFilter(EventSubscribeFilter filter) {
		this.filter = filter;
	}
	
}
