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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.w3c.dom.Element;

import at.ac.tuwien.infosys.aggr.request.AbstractInput;
import at.ac.tuwien.infosys.aggr.request.AbstractOutput;
import at.ac.tuwien.infosys.events.Event;
import at.ac.tuwien.infosys.monitoring.config.MonitoringConfig;
import at.ac.tuwien.infosys.monitoring.config.MonitoringConfigPublication;
import at.ac.tuwien.infosys.monitoring.config.MonitoringConfigPublications;
import at.ac.tuwien.infosys.monitoring.config.MonitoringConfigSet;
import at.ac.tuwien.infosys.monitoring.config.MonitoringConfigSubscription;
import at.ac.tuwien.infosys.monitoring.config.MonitoringConfigSubscriptions;
import at.ac.tuwien.infosys.monitoring.config.NodeConfigChangeOutput;
import at.ac.tuwien.infosys.monitoring.config.NodeConfigInput;
import at.ac.tuwien.infosys.monitoring.config.NodeConfigOutput;
import at.ac.tuwien.infosys.monitoring.config.NodeRepositoryNodeConfig;
import at.ac.tuwien.infosys.monitoring.esper.ChangeConfigEvent;
import at.ac.tuwien.infosys.util.Util;
import at.ac.tuwien.infosys.ws.EndpointReference;

public interface DeploymentConfigFactory {
	
	public DeploymentConfig createConfig(MonitoringConfig config, NodeRepository nodeRepository);
	
	public static class SimpleDeploymentConfigFactory implements DeploymentConfigFactory {
		
		private static Util UTIL = new Util();
		private static String INTERNAL_ID_PREFIX = "_NC";
		
		@Override
		public DeploymentConfig createConfig(MonitoringConfig config, NodeRepository nodeRepository) {
			DeploymentConfig retVal = new DeploymentConfig(config);
			
			//TODO this must be optimized with an algorithm!!!
			long id = 1L;
			
			final Map<String, List<EndpointReference>> eventTypeMap = new HashMap<String, List<EndpointReference>>();
			
			final DeploymentNodeConfig ioNodeConfig = createIONodeConfig(config,
					nodeRepository, id, eventTypeMap);
			retVal.getDeploymentNodesConfig().add(ioNodeConfig);			
			id++;
					
			for (MonitoringConfigSet set : config.getMonitoringSets()) {			
				final DeploymentNodeConfig nodeConfig = createNodeConfig(config,
						nodeRepository, id, eventTypeMap, set);		
				
				retVal.getDeploymentNodesConfig().add(nodeConfig);
				id++;
			}
			
			addInternalConfig(retVal, eventTypeMap);
			
			return retVal;
		}

		private DeploymentNodeConfig createNodeConfig(MonitoringConfig config,
				NodeRepository nodeRepository, long id,
				final Map<String, List<EndpointReference>> eventTypeMap,
				MonitoringConfigSet set) {
			
			final DeploymentNodeConfig retVal = createDeploymentNodeConfig(id, 
					config.getNodeCluster(), nodeRepository);	
			retVal.getMonitoringSets().add(set);
						
			for (MonitoringConfigSubscription subscribe : set.getSubscriptions().getSubscriptions()) {
				String eventType = subscribe.getEventType();
				if(eventTypeMap.containsKey(eventType) == false){
					eventTypeMap.put(eventType, new ArrayList<EndpointReference>());
				}
				
				eventTypeMap.get(eventType).add(retVal.getEndTo());
			}
			return retVal;
		}

		private DeploymentNodeConfig createIONodeConfig(
				MonitoringConfig config, NodeRepository nodeRepository,
				long id, Map<String, List<EndpointReference>> eventTypeMap) {
			
			final DeploymentNodeConfig retVal = createDeploymentNodeConfig(id,
					config.getNodeCluster(), nodeRepository);
			retVal.getInputs().addAllInputs(config.getAllInputs());
			MonitoringConfigSet mSet = new MonitoringConfigSet();
			mSet.setIdentifier(Long.MAX_VALUE);
			mSet.setActive(true);
			retVal.getMonitoringSets().add(mSet);
			for (AbstractOutput out : config.getAllOutputs()) {
				retVal.getOutputs().addOutput(out);
				for (MonitoringConfigSubscription sub : out.getSubscriptions().getSubscriptions()) {
					final String eventType = sub.getEventType();					
					if(checkContains(mSet.getSubscriptions(), eventType) == false){
						//mSet.getQuery().add(String.format("insert into %s select * from %s", eventType, eventType));
						
						final MonitoringConfigSubscription mcs = new MonitoringConfigSubscription();
						mcs.setEventType(eventType);					
						mSet.getSubscriptions().getSubscriptions().add(mcs);
						
						if(eventTypeMap.containsKey(eventType) == false){
							eventTypeMap.put(eventType, new ArrayList<EndpointReference>());
						}
						
						final List<EndpointReference> evTypeEpTo = eventTypeMap.get(eventType);
						if(evTypeEpTo.contains(retVal.getEndTo()) == false) {
							evTypeEpTo.add(retVal.getEndTo());
						}
					}
				}
			}
			
			for (AbstractInput in : config.getAllInputs()) {
				for (MonitoringConfigPublication pub : in.getPublications().getPublications()) {
					final String eventType = pub.getEventType();
					if(checkContains(mSet.getPublications(), eventType) == false){
						final MonitoringConfigPublication mcp = new MonitoringConfigPublication();
						mcp.setEventType(eventType);					
						mSet.getPublications().getPublications().add(mcp);
					}
				}
			}
			
			return retVal;
		}
		
		
		

		private static boolean checkContains(MonitoringConfigSubscriptions subscriptions, String eventType) {
			for (MonitoringConfigSubscription mcs : subscriptions.getSubscriptions()) {
				if(mcs.getEventType().equals(eventType))
					return true;
			}
			return false;
		}

		private static boolean checkContains(MonitoringConfigPublications publications, String eventType) {
			for (MonitoringConfigPublication mcp : publications.getPublications()) {
				if(mcp.getEventType().equals(eventType))
					return true;
			}
			return false;
		}

		private static void addInternalConfig(DeploymentConfig retVal,	 
				Map<String, List<EndpointReference>> eventTypeMap) {
			//Node Configuration
			for (DeploymentNodeConfig nodeConfig : retVal.getDeploymentNodesConfig()) {
														
				//Create Monitoring Node Input
				try {					
					final Element e = UTIL.createElement("endTo");					
					final EndpointReference endTo = getEventEpr(nodeConfig.getEndTo());		
					UTIL.appendChild(e, endTo.toElement());
					NodeConfigInput nodeConfigInput = new NodeConfigInput(e);		
					nodeConfigInput.setExternalID(INTERNAL_ID_PREFIX + nodeConfig.getIdentifier());
					nodeConfig.getInputs().addInput(nodeConfigInput);
				} catch (Exception e) {
					//can not happen
				}
				
				//Create Monitoring Node Output
				NodeConfigOutput nodeConfigOutput = new NodeConfigOutput();	
				nodeConfigOutput.setExternalID(INTERNAL_ID_PREFIX + nodeConfig.getIdentifier());
				nodeConfigOutput.setSubscriptions(new MonitoringConfigSubscriptions());
				nodeConfig.getOutputs().addOutput(nodeConfigOutput);
				for (String eventType : eventTypeMap.keySet()) {
					for (EndpointReference epr : eventTypeMap.get(eventType)) {
						if(epr.equals(nodeConfig.getEndTo()) == false){
							EndpointReference eventErp = getEventEpr(epr);
							nodeConfigOutput.getEventTypeRecipients(eventType).add(eventErp);
						}
					}
				}
				for (MonitoringConfigSet set : nodeConfig.getMonitoringSets()) {					
					for (MonitoringConfigPublication setPublish : set.getPublications().getPublications()) {
						MonitoringConfigSubscription mc = new MonitoringConfigSubscription();
						mc.setEventType(setPublish.getEventType());
						nodeConfigOutput.getSubscriptions().getSubscriptions().add(mc);
					}
				}
				
				//Create Monitoring Change Node Output 
				NodeConfigChangeOutput nodeConfigChangeOutput = new NodeConfigChangeOutput();
				nodeConfigChangeOutput.setExternalID(INTERNAL_ID_PREFIX + "Chang" + nodeConfig.getIdentifier());
				nodeConfigChangeOutput.setSubscriptions(new MonitoringConfigSubscriptions());
				MonitoringConfigSubscription mcActivate = new MonitoringConfigSubscription();
				mcActivate.setEventType(Event.getEventType(ChangeConfigEvent.class));
				nodeConfigChangeOutput.getSubscriptions().getSubscriptions().add(mcActivate);
				nodeConfig.getOutputs().addOutput(nodeConfigChangeOutput);
				
			}
		}		

		private static EndpointReference getEventEpr(EndpointReference endTo) {
			EndpointReference retVal = null;
			try {
				retVal = new EndpointReference(endTo);
				retVal.setAddress(retVal.getAddress().replace("/node", "/events"));
			} catch (Exception e) {
				e.printStackTrace();
			}			
			return retVal;
		}	
		
		private DeploymentNodeConfig createDeploymentNodeConfig(long id,
				String cluster, NodeRepository nodeRepository) {
			
			NodeRepositoryNodeConfig nodeRepositoryNodeConfig = nodeRepository.getNodes(cluster).get((int)(id - 1));
			DeploymentNodeConfig retVal = new DeploymentNodeConfig();
			retVal.setIdentifier(id);
			retVal.setEndTo(nodeRepositoryNodeConfig.getEndTo());
			return retVal;
		}
		
		
		
	}
	
}
