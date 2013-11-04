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

import at.ac.tuwien.infosys.aggr.testbed.RequestExecuter;

/**
 * @author Daniel Domberger
 *
 */
public class StartGatewayRequest implements Request {
	private static final long serialVersionUID = 9029205117261247815L;
	
	private String gatewayUri;
	
	public StartGatewayRequest() {
		super();
	}
	
	public StartGatewayRequest(String gatewayUri) {
		super();
		this.gatewayUri = gatewayUri;
	}

	/* (non-Javadoc)
	 * @see at.ac.tuwien.infosys.aggr.testbed.messaging.Request#execute(at.ac.tuwien.infosys.aggr.testbed.RequestExecuter)
	 */
	@Override
	public void execute(RequestExecuter executer) {
		executer.startGateway(gatewayUri);
	}
	
	/**
	 * @return the gatewayUri
	 */
	public String getGatewayUri() {
		return gatewayUri;
	}

	/**
	 * @param gatewayUri the gatewayUri to set
	 */
	public void setGatewayUri(String gatewayUri) {
		this.gatewayUri = gatewayUri;
	}
}
