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

import io.hummer.util.ws.EndpointReference;
import at.ac.tuwien.infosys.events.ws.WebServiceClientUtil;

public class RemoteMonitoringEngine implements MonitoringEngine {

	private EndpointReference endTo;

	public RemoteMonitoringEngine(EndpointReference endTo){
		this.endTo = endTo;
	}
	
	@Override
	public void start() throws Exception {
		WebServiceClientUtil.execute(endTo, new MonitoringServiceNode.StartRequest(), null);
	}

	@Override
	public void stop() throws Exception {
		WebServiceClientUtil.execute(endTo, new MonitoringServiceNode.StopRequest(), null);
	}

	@Override
	public void destroy() throws Exception {
		WebServiceClientUtil.execute(endTo, new MonitoringServiceNode.DestroyRequest(), null);
	}
	
	@Override
	public void configure(DeploymentNodeConfig config) throws Exception {
		WebServiceClientUtil.execute(endTo, config, null);
	}
}
