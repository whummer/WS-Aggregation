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

import at.ac.tuwien.infosys.aggr.request.AbstractInput;
import at.ac.tuwien.infosys.aggr.request.AbstractInput.RequestInputs;
import at.ac.tuwien.infosys.aggr.request.AbstractOutput;


@XmlRootElement(name=MonitoringConfig.NAME_ELEMENT, namespace=Constants.NAMESPACE)
public class MonitoringConfig {

	public static final String NAME_ELEMENT = "monitoring";
	
	@XmlAttribute(name="nodeCluster")
	private String nodeCluster;
	
	@XmlElement(name=MonitoringConfigSet.NAME_ELEMENT)
	private List<MonitoringConfigSet> monitoringSets = new ArrayList<MonitoringConfigSet>();	
	
	@XmlElement
	private RequestInputs inputs;
	
	@XmlTransient
	private boolean inputsInitialized = false;
	
	@XmlElement
	private Outputs outputs;
	@XmlTransient
	private boolean outputsInitialized = false;
	
	@XmlTransient
	public String getNodeCluster(){
		return nodeCluster;
	}

	@XmlTransient
	public List<MonitoringConfigSet> getMonitoringSets(){
		return monitoringSets;
	}
	
	@XmlTransient
	public RequestInputs getInputs() {
		if(!inputsInitialized) {
			inputsInitialized = true;
			if(inputs == null)
				inputs = new RequestInputs();
		}
		return inputs;
	}
	public void setInputs(RequestInputs inputs) {
		this.inputs = inputs;
	}

	public List<AbstractInput> getAllInputs() {
		if(inputs == null) {
			inputs = new RequestInputs();
		}
		return inputs.getInputsCopy();
	}
	
	@XmlTransient
	public Outputs getOutputs() {
		if(!outputsInitialized) {
			outputsInitialized = true;
			if(outputs == null){
				outputs = new Outputs();
			}
		}
		return outputs;
	}
	public void setOutputs(Outputs outputs) {
		this.outputs = outputs;
	}

	public List<AbstractOutput> getAllOutputs() {
		if(outputs == null) {
			outputs = new Outputs();
		}
		return outputs.getOutputsCopy();
	}
	
}
