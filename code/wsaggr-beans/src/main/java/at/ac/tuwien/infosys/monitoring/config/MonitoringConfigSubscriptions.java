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

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

@XmlRootElement(name=MonitoringConfigSubscriptions.NAME_ELEMENT, namespace=Constants.NAMESPACE)
public class MonitoringConfigSubscriptions {
	
	public static final String NAME_ELEMENT = "subscriptions";
	
	@XmlElement(name=MonitoringConfigSubscription.NAME_ELEMENT)
	private List<MonitoringConfigSubscription> subscriptions = new ArrayList<MonitoringConfigSubscription>();
	
	@XmlTransient
	public List<MonitoringConfigSubscription> getSubscriptions() {
		return subscriptions;
	}

	public void setSubscriptions(List<MonitoringConfigSubscription> subscriptions) {
		this.subscriptions = subscriptions;
	}
}
