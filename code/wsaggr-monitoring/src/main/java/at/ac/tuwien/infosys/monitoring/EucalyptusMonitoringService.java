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

import at.ac.tuwien.infosys.monitoring.config.MonitoringStartupConfig;

public class EucalyptusMonitoringService extends AbstractMonitoringService {
	
	public EucalyptusMonitoringService(MonitoringEngineInternal engine) {
		super(engine);
	}
		
	@Override
	public void shutdown() {
		super.shutdown();
		System.exit(0);
	}	
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		try {

			final MonitoringEngineInternal engine = createMonitoringEngine();
						
			final AbstractMonitoringService service = new EucalyptusMonitoringService(engine);
			
			final MonitoringStartupConfig config = parseMonitoringStartupConfig(
					EucalyptusMonitoringService.class.getResourceAsStream("eucalyptusStartupConfig.xml"));
			service.configureStartup(config);
			
			System.out.println("Waiting for stop...");
			System.in.read();
			service.shutdown();				
			
		} catch (Exception e) {
			e.printStackTrace();
		}		
	}
	
}
