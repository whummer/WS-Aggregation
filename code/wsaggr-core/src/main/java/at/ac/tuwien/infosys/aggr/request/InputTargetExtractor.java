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

package at.ac.tuwien.infosys.aggr.request;

import io.hummer.util.ws.AbstractNode;
import io.hummer.util.ws.EndpointReference;

import java.net.URL;

import at.ac.tuwien.infosys.aggr.node.DataServiceNode;
import at.ac.tuwien.infosys.aggr.node.Registry;
import at.ac.tuwien.infosys.aggr.proxy.RegistryProxy;

public class InputTargetExtractor {

	public static AbstractNode extractDataSourceNode(NonConstantInput input) throws Exception {
		return extractDataSourceNode(input, null);
	}
	public static AbstractNode extractDataSourceNode(NonConstantInput input, AbstractNode owner) throws Exception {
		if(input instanceof RequestInput) {
			if(((RequestInput)input).getQuery() != null)
				return owner;
		}
		RegistryProxy registry = Registry.getRegistryProxy();
		AbstractNode node = new DataServiceNode();
		if(input.getServiceEPR() != null) {
			node.setEPR(input.getServiceEPR());
		} else if(input.getServiceURL() != null) {
			node.setEPR(new EndpointReference(new URL(input.getServiceURL())));
		} else if(input.getFeature() != null) {
			DataServiceNode target = registry.getRandomDataServiceNode(input.getFeature());
			if(target == null)
				throw new RuntimeException("No target node found for feature: " + input.getFeature());
			node.setEPR(target.getEPR());
		} else {
			throw new Exception("No endpoint information in request input: " + input);
		}
		return node;
	}

}
