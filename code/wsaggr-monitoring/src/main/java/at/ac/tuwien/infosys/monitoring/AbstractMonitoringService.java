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

import java.io.InputStream;

import org.apache.log4j.Logger;

import at.ac.tuwien.infosys.events.EventTypeRepository;
import at.ac.tuwien.infosys.monitoring.config.MonitoringConfig;
import at.ac.tuwien.infosys.monitoring.config.MonitoringStartupConfig;
import at.ac.tuwien.infosys.monitoring.config.NodeRepositoryNodeConfig;
import at.ac.tuwien.infosys.util.Configuration;
import at.ac.tuwien.infosys.util.Util;

public abstract class AbstractMonitoringService implements MonitoringService {

	class MonitoringServiceNodeConf 
	{
		private NodeRepositoryNodeConfig config;
		private MonitoringServiceNode node;

		public MonitoringServiceNodeConf(NodeRepositoryNodeConfig config, MonitoringServiceNode node){
			this.config = config;
			this.node = node;
		}

		public NodeRepositoryNodeConfig getConfig() {
			return config;
		}

		public MonitoringServiceNode getNode() {
			return node;
		}		
		
	}
	
	private static final Logger LOGGER = Util.getLogger(AbstractMonitoringService.class);

	private DeploymentConfig dConfig;
	
	private final MonitoringEngineInternal engine;
	
	private MonitoringServiceNodeConf monitoringServiceNode;

	private EventTypeRepository eventTypeRepository;

	private NodeRepository nodeRepository;
	
	private DeploymentConfigFactory deploymentConfigFactory;

	private static final Util UTIL = new Util();

	public AbstractMonitoringService(MonitoringEngineInternal engine){
		this.engine = engine;
	}

	public void configureStartup(MonitoringStartupConfig startupConfig) throws Exception {
		eventTypeRepository = RepositoryHelpers
				.generateEventTypeRepository(startupConfig.getEventTypeConfig());
		engine.setEventTypeRepository(eventTypeRepository);
		
		nodeRepository = RepositoryHelpers.generateNodeRepository(startupConfig
				.getNodeConfig());
		
		deploymentConfigFactory = new DeploymentConfigFactory.SimpleDeploymentConfigFactory();
		
		MonitoringServiceNode mServiceNode = new MonitoringServiceNode(new LocalMonitoringService(engine), engine);
		NodeRepositoryNodeConfig endpoint = startupConfig.getEndpoint();
		mServiceNode.setEPR(endpoint.getEndTo());
		mServiceNode.deploy(endpoint.getEndTo().getAddress());
		
		monitoringServiceNode = new MonitoringServiceNodeConf(endpoint, mServiceNode);
		nodeRepository.registerNode(startupConfig.getNodeConfig().getCluster(), endpoint);		
	}
	
	@Override
	public void shutdown() {
		getNodeRepository().unregisterNode(monitoringServiceNode.getConfig());		
	}
	
	
	protected EventTypeRepository getEventTypeRepository() {
		return eventTypeRepository;
	}

	public NodeRepository getNodeRepository() {
		return nodeRepository;
	}

	protected MonitoringEngine getEngine() {
		return engine;
	}

	
	@Override
	public void undeploy() throws Exception {
		if(dConfig != null){
			LOGGER.debug("Stopping engine.");			
			for (DeploymentNodeConfig dNodeConfig : dConfig.getDeploymentNodesConfig()) {
				MonitoringEngine node = new RemoteMonitoringEngine(dNodeConfig.getEndTo());
				node.stop();			
			}
			LOGGER.debug("Destroy engine.");			
			for (DeploymentNodeConfig dNodeConfig : dConfig.getDeploymentNodesConfig()) {
				MonitoringEngine node = new RemoteMonitoringEngine(dNodeConfig.getEndTo());
				node.destroy();			
			}
		}
	}
	
	@Override
	public void deploy(MonitoringConfig config) throws Exception {
		dConfig = this.deploymentConfigFactory.createConfig(config, nodeRepository);
		
		if(LOGGER.isInfoEnabled()){
			final String deplConfig = UTIL.toString(dConfig, true);
			LOGGER.info("Active Deployment Config:\n" + deplConfig);
		}
		LOGGER.debug("Configuring engine.");						
		for (DeploymentNodeConfig dNodeConfig : dConfig.getDeploymentNodesConfig()) {
			MonitoringEngine node = new RemoteMonitoringEngine(dNodeConfig.getEndTo());
			node.configure(dNodeConfig);			
		}
		LOGGER.debug("Starting engine.");
		for (DeploymentNodeConfig dNodeConfig : dConfig.getDeploymentNodesConfig()) {
			MonitoringEngine node = new RemoteMonitoringEngine(dNodeConfig.getEndTo());
			node.start();			
		}
	}	

	public static MonitoringStartupConfig parseMonitoringStartupConfig(InputStream stream) throws Exception {
		final String fileContent = UTIL.readFile(stream);
		MonitoringStartupConfig mConfig = UTIL.toJaxbObject(
				MonitoringStartupConfig.class, UTIL.toElement(fileContent));
		return mConfig;
	}
	
	public static MonitoringConfig parseMonitoringConfig(InputStream stream) throws Exception {
		final String fileContent = UTIL.readFile(stream);
		MonitoringConfig mConfig = UTIL.toJaxbObject(
				MonitoringConfig.class, UTIL.toElement(fileContent));
		return mConfig;
	}

	public static MonitoringEngineInternal createMonitoringEngine() throws Exception {
		final String monitoringEngine = Configuration.getValue("monitoring.engine");
		LOGGER.info(String.format("System configuerd to use engine: '%s'.",	monitoringEngine));

		final MonitoringEngineInternal engine = (MonitoringEngineInternal) Class.forName(monitoringEngine).newInstance();
		return engine;
	}
	
	

}
