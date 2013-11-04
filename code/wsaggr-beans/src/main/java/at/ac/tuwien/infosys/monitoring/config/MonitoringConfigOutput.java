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

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import at.ac.tuwien.infosys.aggr.request.AbstractOutput;

@XmlRootElement(name=MonitoringConfigOutput.JAXB_ELEMENT_NAME)
@XmlJavaTypeAdapter(AbstractOutput.Adapter.class)
public class MonitoringConfigOutput extends AbstractOutput {

	public static final String JAXB_ELEMENT_NAME = "monitoringConfig";	
	
	@XmlAttribute()
	private int monitoringSet;

	@XmlTransient
	public int getMonitoringSet() {
		return monitoringSet;
	}

	public void setMonitoringSet(int monitoringSet) {
		this.monitoringSet = monitoringSet;
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T extends AbstractOutput> T copy() throws Exception {
		MonitoringConfigOutput retVal = new MonitoringConfigOutput();
		retVal.setMonitoringSet(getMonitoringSet());
		return (T) retVal;
	} 
		
}
