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

package at.ac.tuwien.infosys.monitoring;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import at.ac.tuwien.infosys.monitoring.config.MonitoringConfig;


@XmlRootElement(name=DeploymentConfig.JAXB_ELEMENT_NAME)
public class DeploymentConfig {

	public static final String JAXB_ELEMENT_NAME = "deplyomentConfig";
	
	@XmlElement(name = MonitoringConfig.NAME_ELEMENT)
	private final MonitoringConfig monitoringConfig;
	
	@XmlElement(name= DeploymentNodeConfig.NAME_ELEMENT)
	private List<DeploymentNodeConfig> nodeConfigs = new ArrayList<DeploymentNodeConfig>();
	
	public DeploymentConfig(MonitoringConfig monitoringConfig){
		this.monitoringConfig = monitoringConfig;
	}
	
	public DeploymentConfig(){
		this.monitoringConfig = null;
	}	

	@XmlTransient
	public MonitoringConfig getMonitoringConfig() {
		return monitoringConfig;
	}

	@XmlTransient
	public List<DeploymentNodeConfig> getDeploymentNodesConfig() {
		return nodeConfigs;
	}

	public void setNodeConfigs(List<DeploymentNodeConfig> nodeConfigs) {
		this.nodeConfigs = nodeConfigs;
	}
	
}
