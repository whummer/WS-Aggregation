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

/**
 * 
 */
package at.ac.tuwien.infosys.aggr.testbed.messaging;

import io.hummer.util.ws.AbstractNode;
import io.hummer.util.ws.EndpointReference;

import java.net.URL;

import at.ac.tuwien.infosys.aggr.node.DataServiceNode;
import at.ac.tuwien.infosys.aggr.proxy.RegistryProxy;
import at.ac.tuwien.infosys.aggr.testbed.RequestExecuter;
import at.ac.tuwien.infosys.ws.SoapService;

/**
 * @author Daniel Domberger
 *
 */
public class DeploySoapRequest extends DeployServiceRequest {
	private static final long serialVersionUID = 1735740958173054565L;

	public DeploySoapRequest() {}
	
	public DeploySoapRequest(SoapService service, String uri, EndpointReference registry) {
		super(service, uri, registry);
	}
	/* (non-Javadoc)
	 * @see at.ac.tuwien.infosys.aggr.testbed.DeployRequest#deploy(at.ac.tuwien.infosys.aggr.testbed.ServiceDeployer)
	 */
	@Override
	public void execute(RequestExecuter executer) {
		executer.deploySoapService(service, uri);
		
		try {
			// set own URL
			if(service instanceof AbstractNode) {
				((AbstractNode)service).setEPR(new EndpointReference(new URL(uri)));
			}
			
			// add service to registry
			new RegistryProxy(registry).addDataServiceNode(null, 
					new DataServiceNode(new EndpointReference(new URL(uri))));
		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}
		
		service.onDeploy();
	}
	
	public void setService(SoapService service) {
		this.service = service;
	}
}
