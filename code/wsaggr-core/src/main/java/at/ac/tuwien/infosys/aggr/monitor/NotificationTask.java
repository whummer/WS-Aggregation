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

@XmlRootElement(name="listener")
public class NotificationTask {

	@XmlElement(name="subscriptionID")
	public String subscriptionID;
	@XmlElement(name="epr")
	public EndpointReference listener;
	@XmlElement(name="filter")
	public String filterXPath;
	
	public NotificationTask(String subscriptionID) {
		this.subscriptionID = subscriptionID;
	}
	
	/** required by JAXB, should not be used by the programmer */
	@Deprecated
	public NotificationTask() { }

	
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result
				+ ((filterXPath == null) ? 0 : filterXPath.hashCode());
		result = prime * result
				+ ((listener == null) ? 0 : listener.hashCode());
		result = prime * result
				+ ((subscriptionID == null) ? 0 : subscriptionID.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		NotificationTask other = (NotificationTask) obj;
		if (filterXPath == null) {
			if (other.filterXPath != null)
				return false;
		} else if (!filterXPath.equals(other.filterXPath))
			return false;
		if (listener == null) {
			if (other.listener != null)
				return false;
		} else if (!listener.equals(other.listener))
			return false;
		if (subscriptionID == null) {
			if (other.subscriptionID != null)
				return false;
		} else if (!subscriptionID.equals(other.subscriptionID))
			return false;
		return true;
	}
	
}
