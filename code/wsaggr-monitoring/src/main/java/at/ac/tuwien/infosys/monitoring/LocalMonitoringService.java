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

import at.ac.tuwien.infosys.monitoring.config.MonitoringConfig;
import at.ac.tuwien.infosys.monitoring.config.MonitoringStartupConfig;
import at.ac.tuwien.infosys.monitoring.config.NodeRepositoryNodeConfig;

public class LocalMonitoringService extends AbstractMonitoringService implements Runnable{

	private List<MonitoringServiceNodeConf> serviceNodes = new ArrayList<MonitoringServiceNodeConf>();
	
	private RemoteMonitoringService remoteService;
	
	public LocalMonitoringService(MonitoringEngineInternal engine){
		super(engine);
	}
	
	@Override
	public void configureStartup(MonitoringStartupConfig startupConfig) throws Exception {
		super.configureStartup(startupConfig);
		remoteService = new RemoteMonitoringService(startupConfig.getEndpoint().getEndTo());
		NodeRepositoryNodeConfig endpoint = startupConfig.getEndpoint();
		for (NodeRepositoryNodeConfig nodeConfig : startupConfig.getNodeConfig().getNodes()) {
			if(endpoint.getEndTo().equals(nodeConfig.getEndTo()) == false){
				MonitoringEngineInternal mEngine = createMonitoringEngine();
				MonitoringServiceNode sNode = new MonitoringServiceNode(
						new LocalMonitoringService(mEngine), mEngine);
				sNode.setEPR(nodeConfig.getEndTo());
				sNode.deploy(nodeConfig.getEndTo().getAddress());
				
				final NodeRepositoryNodeConfig nodeRepoConfig = new NodeRepositoryNodeConfig();
				nodeRepoConfig.setEndTo(nodeConfig.getEndTo());
				nodeRepoConfig.setIdentifier(nodeConfig.getIdentifier());
				getNodeRepository().registerNode(startupConfig.getNodeConfig().getCluster(), nodeRepoConfig);
				serviceNodes.add(new MonitoringServiceNodeConf(nodeRepoConfig, sNode));
			}
		}		
	}
	
	@Override
	public void shutdown() {
		super.shutdown();
		for (MonitoringServiceNodeConf node : serviceNodes) {
			getNodeRepository().unregisterNode(node.getConfig());
		}
	}
	
	@Override
	public void run() {
		try {
			final MonitoringConfig config = parseMonitoringConfig(
					LocalMonitoringService.class.getResourceAsStream("monitoringTest.xml"));
			remoteService.deploy(config);
		} catch(Exception e){
			e.printStackTrace();
		}
	}
	
	public void terminate() throws Exception {
		remoteService.undeploy();
		remoteService.shutdown();
		
	}
	
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		try {			
			final MonitoringEngineInternal engine = createMonitoringEngine();
						
			LocalMonitoringService service;
			if(args.length > 0){
				Class<?> serviceClazz = Class.forName(args[0]);
				service = (LocalMonitoringService)serviceClazz.getConstructor(MonitoringEngine.class).newInstance(engine);
			}
			else {
				service = new LocalMonitoringService(engine);
			}
			
			final MonitoringStartupConfig config = parseMonitoringStartupConfig(
					LocalMonitoringService.class.getResourceAsStream("localStartupConfig.xml"));
			service.configureStartup(config);
			
			Thread t = new Thread(service);
			t.run();
			
			System.out.println("Waiting for shutdown...");
			System.in.read();
			System.out.println("Shutting down...");
			service.terminate();
			t.join(1000);
			System.out.println("_Closed_");
			System.exit(0);
		} catch (Exception e) {
			e.printStackTrace();
		}		
	}
	
}
