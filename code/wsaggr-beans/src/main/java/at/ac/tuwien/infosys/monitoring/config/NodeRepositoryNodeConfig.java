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

import io.hummer.util.persist.Identifiable;
import io.hummer.util.ws.EndpointReference;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

@XmlRootElement(name=NodeRepositoryNodeConfig.NAME_ELEMENT, namespace=Constants.NAMESPACE)
public class NodeRepositoryNodeConfig implements Identifiable {
	
	public static final String NAME_ELEMENT = "nodeConfig";

	@XmlAttribute(name="id")
	private long id;
	
	@XmlElement(name="endTo")
	private EndpointReference endTo;

	@XmlTransient
	public EndpointReference getEndTo() {
		return endTo;
	}

	public void setEndTo(EndpointReference endTo) {
		this.endTo = endTo;
	}

	@Override
	@XmlTransient
	public long getIdentifier() {
		return id;
	}	
	
	public void setIdentifier(long id){
		this.id = id;
	}
	
}
