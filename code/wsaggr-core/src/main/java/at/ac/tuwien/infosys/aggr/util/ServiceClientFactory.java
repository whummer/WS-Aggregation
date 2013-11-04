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

package at.ac.tuwien.infosys.aggr.util;

import java.net.URL;

import at.ac.tuwien.infosys.aggr.node.DataServiceNode;
import at.ac.tuwien.infosys.aggr.node.Registry;
import at.ac.tuwien.infosys.aggr.request.NonConstantInput;
import at.ac.tuwien.infosys.aggr.request.RequestInput;
import at.ac.tuwien.infosys.ws.EndpointReference;
import at.ac.tuwien.infosys.ws.WebServiceClient;

public class ServiceClientFactory {

	public static WebServiceClient getClient(NonConstantInput input) throws Exception {
		if(input.getServiceEPR() != null) {
			return WebServiceClient.getClient(input.getServiceEPR());
		} else if(input.getServiceURL() != null) {
			return WebServiceClient.getClient(new EndpointReference(new URL(input.getServiceURL())));
		} else if(input instanceof RequestInput) {
			if(((RequestInput)input).getFeature() != null) {
				DataServiceNode node = Registry.getRegistryProxy().getRandomDataServiceNode(
						((RequestInput)input).getFeature());
				return WebServiceClient.getClient(node.getEPR());
			}
		}
		throw new RuntimeException("Cannot determine endpoint(s) for request input: " + input);
	}
	
}
