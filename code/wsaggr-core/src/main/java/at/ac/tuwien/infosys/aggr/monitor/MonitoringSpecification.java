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
package at.ac.tuwien.infosys.aggr.monitor;

import io.hummer.util.ws.EndpointReference;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

@XmlRootElement(name="monitor")
public class MonitoringSpecification {

	@XmlElement(name="epr")
	private EndpointReference epr;

	public MonitoringSpecification() { }
	public MonitoringSpecification(EndpointReference epr) { 
		this.setEpr(epr);
	}
	@XmlTransient
	public EndpointReference getEpr() {
		return epr;
	}
	public void setEpr(EndpointReference epr) {
		this.epr = epr;
	}
	
}
