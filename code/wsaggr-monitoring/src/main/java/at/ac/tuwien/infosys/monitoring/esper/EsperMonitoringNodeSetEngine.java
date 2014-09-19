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

package at.ac.tuwien.infosys.monitoring.esper;

import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;

import at.ac.tuwien.infosys.monitoring.config.MonitoringConfigSet;
import io.hummer.util.Util;

import com.espertech.esper.client.EPServiceProvider;
import com.espertech.esper.client.EPStatement;

public class EsperMonitoringNodeSetEngine {

	private static final Logger LOGGER = Util.getLogger(EsperMonitoringEngine.class);
	
	private EsperMonitoringEngine engine;
	private MonitoringConfigSet configSet;
	
	private List<EPStatement> configQueries = new ArrayList<EPStatement>();
	
	private List<EPStatement> queries = new ArrayList<EPStatement>();
	
	
	public EsperMonitoringNodeSetEngine(EsperMonitoringEngine engine){
		this.engine = engine;
	}
	
	public MonitoringConfigSet getMonitoringConfigSet(){
		return configSet;
	}
	
	public void configure(MonitoringConfigSet config) throws Exception{
		this.configSet = config;
		LOGGER.info(String.format("ConfigSet %s is %s.", configSet.getIdentifier(), 
				configSet.isActive() ? "active" : "not active"));
		EPServiceProvider epServiceProvider = engine.getEPServiceProvider();
		for (String query : configSet.getQuery()) {
			EPStatement epStatement = epServiceProvider.getEPAdministrator().createEPL(query);
			//could be used to generate the publish event types
			//epStatement.getEventType();
			this.queries.add(epStatement);
		}
		
		for (String query : configSet.getActivateQuery()) {
			String q = String.format(
					"insert into ChangeConfig select %s as monitoringSet, 'activate' as action %s",
					config.getIdentifier(), query);
			EPStatement epStatement = epServiceProvider.getEPAdministrator().createEPL(q);
			this.configQueries.add(epStatement);
		}
		for (String query : configSet.getDeactivateQuery()) {
			String q = String.format(
					"insert into ChangeConfig select %s as monitoringSet, 'deactivate' as action %s",
					config.getIdentifier(), query);
			EPStatement epStatement = epServiceProvider.getEPAdministrator().createEPL(q);
			this.configQueries.add(epStatement);
		}
			
		if(configSet.isActive() == false){
			for (EPStatement query : queries) {
				if(query.isStopped() == false){
					query.stop();
				}
			}
		}
	}
	
	public void deactivate() {
		if(configSet.isActive()){
			this.configSet.setActive(false);
			for (EPStatement query : queries) {
				if(query.isStopped() == false){
					query.stop();
				}
			}
			LOGGER.info(String.format("ConfigSet %s deactivated.", this.getMonitoringConfigSet().getIdentifier()));
		}
	}	
	
	public void activate() {
		if(configSet.isActive() == false){
			this.configSet.setActive(true);
			for (EPStatement query : queries) {
				if(query.isStarted() == false){
					query.start();
				}
			}
			LOGGER.info(String.format("ConfigSet %s activated.", this.getMonitoringConfigSet().getIdentifier()));
		}
	}	
	
	public void start() {
		if(configSet.isActive()){
			LOGGER.info(String.format("Starting config with id '%s'.", configSet.getIdentifier()));
			for (EPStatement query : configQueries) {
				if(query.isStarted() == false){
					query.start();
				}
			}
			for (EPStatement query : queries) {
				if(query.isStarted() == false){
					query.start();
				}
			}
		}
	}

	public void stop() {
		LOGGER.info(String.format("Stopping config with id '%s'.", configSet.getIdentifier()));
		for (EPStatement query : configQueries) {
			if(query.isStopped() == false){
				query.stop();
			}
		}
		for (EPStatement query : queries) {
			if(query.isStopped() == false){
				query.stop();
			}
		}
	}
	
	public void destroy() {
		LOGGER.info(String.format("Destroying config with id '%s'.", configSet.getIdentifier()));		
		for (EPStatement query : queries) {
			if(query.isDestroyed() == false){
				query.destroy();
			}
		}
		for (EPStatement query : configQueries) {
			if(query.isDestroyed() == false){
				query.destroy();
			}
		}

	}	
	
}
