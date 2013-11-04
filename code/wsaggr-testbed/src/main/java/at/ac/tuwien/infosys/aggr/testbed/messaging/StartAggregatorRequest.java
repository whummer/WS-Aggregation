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
public class StartAggregatorRequest implements Request {
	
	private static final long serialVersionUID = 1L;

	int port;
	String gatewayUrl;
	String aggregatorUrl;
	
	public StartAggregatorRequest(int port, String gatewayUrl, String aggregatorUrl) {
		this.port = port;
		this.gatewayUrl = gatewayUrl;
		this.aggregatorUrl = aggregatorUrl;
	}
	
	public void setGatewayUrl(String gatewayUrl) {
		this.gatewayUrl = gatewayUrl;
	}
	
	/* (non-Javadoc)
	 * @see at.ac.tuwien.infosys.aggr.testbed.messaging.Request#execute(at.ac.tuwien.infosys.aggr.testbed.RequestExecuter)
	 */
	@Override
	public void execute(RequestExecuter executer) {
		executer.startAggregator(port, gatewayUrl, aggregatorUrl);
	}
}