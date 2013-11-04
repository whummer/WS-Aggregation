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

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import at.ac.tuwien.infosys.ws.EndpointReference;

@XmlRootElement(name=EventTypeEndpointReferenceMapping.NAME_ELEMENT, namespace=Constants.NAMESPACE)
public class EventTypeEndpointReferenceMapping {

	public static final String NAME_ELEMENT = "evTypeEpr";
	
	@XmlAttribute(name="eventType")
	private String eventType; 
	
	@XmlElement(name="endTo")
	private List<EndpointReference> endPoints = new ArrayList<EndpointReference>();

	@XmlTransient
	public List<EndpointReference> getEndPoints() {
		return endPoints;
	}

	public void setEndPoints(List<EndpointReference> endPoints) {
		this.endPoints = endPoints;
	}

	@XmlTransient
	public String getEventType() {
		return eventType;
	}

	public void setEventType(String eventType) {
		this.eventType = eventType;
	}
	
}
