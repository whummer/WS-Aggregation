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

import at.ac.tuwien.infosys.ws.EndpointReference;
import at.ac.tuwien.infosys.ws.Service;

/**
 * @author Daniel Domberger
 *
 */
public abstract class DeployServiceRequest implements Request {
	private static final long serialVersionUID = -3102806342165781417L;
	
	Service service = null;
	String uri = null;
	EndpointReference registry = null;
	
	public DeployServiceRequest() {}
	
	public DeployServiceRequest(Service service, String uri, EndpointReference registry) {
		this.service = service;
		this.uri = uri;
		this.registry = registry;
	}
	
	/**
	 * This constructor takes the uri from the given service
	 * @param service	RestService to deploy
	 */
/*	public DeployServiceRequest(Service service) {
		this.service = service;
		this.uri = service.getUrl();
	}
*/	
	public void setUri(String uri) {
		this.uri = uri;
	}
	
	public String getUri() {
		return uri;
	}
	
	public void setService(Service service) {
		this.service = service;
	}
	
	public Service getService() {
		return service;
	}
	
	public void setRegistry(EndpointReference registry) {
		this.registry = registry;
	}
	
	public EndpointReference getRegistry() {
		return registry;
	}
}
