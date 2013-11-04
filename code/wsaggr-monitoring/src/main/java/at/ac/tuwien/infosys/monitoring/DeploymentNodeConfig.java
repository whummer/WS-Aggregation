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

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.XmlTransient;

import at.ac.tuwien.infosys.aggr.request.AbstractInput;
import at.ac.tuwien.infosys.aggr.request.AbstractInput.RequestInputs;
import at.ac.tuwien.infosys.aggr.request.AbstractOutput;
import at.ac.tuwien.infosys.monitoring.config.Constants;
import at.ac.tuwien.infosys.monitoring.config.MonitoringConfigSet;
import at.ac.tuwien.infosys.monitoring.config.Outputs;
import at.ac.tuwien.infosys.util.Identifiable;
import at.ac.tuwien.infosys.ws.EndpointReference;

@XmlRootElement(name=DeploymentNodeConfig.NAME_ELEMENT, namespace=Constants.NAMESPACE)
@XmlSeeAlso({AbstractInput.class, AbstractOutput.class})
public class DeploymentNodeConfig implements Identifiable{

	public static final String NAME_ELEMENT = "nodeConfig";
	
	@XmlAttribute(name="id")
	private long id;
	
	@XmlElement(name=MonitoringConfigSet.NAME_ELEMENT)
	private List<MonitoringConfigSet> monitoringSets = new ArrayList<MonitoringConfigSet>();
	
	@XmlElement(name="endTo")
	private EndpointReference endTo;
	
	@XmlElement
	private RequestInputs inputs;
	
	@XmlTransient
	private boolean inputsInitialized = false;
	
	@XmlElement
	private Outputs outputs;
	@XmlTransient
	private boolean outputsInitialized = false;

	@Override
	@XmlTransient
	public long getIdentifier() {
		return id;
	}	
	
	public void setIdentifier(long id) {
		this.id = id;
	}
	
	@XmlTransient
	public EndpointReference getEndTo(){
		return endTo;
	}
	
	public void setEndTo(EndpointReference endTo) {
		this.endTo = endTo;
	}


	@XmlTransient
	public List<MonitoringConfigSet> getMonitoringSets() {
		return monitoringSets;
	}

	public void setMonitoringSets(List<MonitoringConfigSet> monitoringSets) {
		this.monitoringSets = monitoringSets;
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
