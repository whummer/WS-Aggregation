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

import at.ac.tuwien.infosys.util.Identifiable;

@XmlRootElement(name=MonitoringConfigSet.NAME_ELEMENT, namespace=Constants.NAMESPACE)
public class MonitoringConfigSet implements Identifiable{

	public static final String NAME_ELEMENT = "monitoringSet";
	
	@XmlAttribute
	private Long id;
	@XmlAttribute
	private boolean active = false;
	@XmlElement
	private MonitoringConfigSubscriptions subscriptions = new MonitoringConfigSubscriptions();
	@XmlElement
	private MonitoringConfigPublications publications = new MonitoringConfigPublications();
	@XmlElement
	private List<String> query = new ArrayList<String>();	
	@XmlElement
	private List<String> activateQuery = new ArrayList<String>();	
	@XmlElement
	private List<String> deactivateQuery = new ArrayList<String>();
	
	@Override
	@XmlTransient
	public long getIdentifier() {
		return id;
	}
	
	public void setIdentifier(long id){
		this.id = id;
	}
	
	public void setActive(boolean active){
		this.active = active; 
	}
	
	@XmlTransient
	public boolean isActive(){
		return active;
	}
	
	@XmlTransient
	public List<String> getQuery(){
		return query;
	}
	
	public void setQuery(List<String> query){
		this.query = query;
	}	
	
	@XmlTransient
	public List<String> getActivateQuery(){
		return activateQuery;
	}
	
	public void setActivateQuery(List<String> query){
		this.activateQuery = query;
	}
	
	@XmlTransient
	public List<String> getDeactivateQuery(){
		return deactivateQuery;
	}
	
	public void setDeactivateQuery(List<String> query){
		this.deactivateQuery = query;
	}
	
	
	@XmlTransient
	public MonitoringConfigSubscriptions getSubscriptions(){
		return subscriptions;
	}
	
	public void setSubscriptions(MonitoringConfigSubscriptions subscriptions){
		this.subscriptions = subscriptions;
	}
	
	@XmlTransient
	public MonitoringConfigPublications getPublications(){
		return publications;
	}
	
	public void setPublications(MonitoringConfigPublications publications){
		this.publications = publications;
	}
		
}

