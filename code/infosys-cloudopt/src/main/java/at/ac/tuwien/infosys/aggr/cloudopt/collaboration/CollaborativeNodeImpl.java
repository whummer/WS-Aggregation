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

package at.ac.tuwien.infosys.aggr.cloudopt.collaboration;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.jws.WebService;

import org.apache.log4j.Logger;

import at.ac.tuwien.infosys.util.Configuration;
import at.ac.tuwien.infosys.ws.AbstractNode;
import at.ac.tuwien.infosys.ws.EndpointReference;


@WebService(targetNamespace=Configuration.NAMESPACE, endpointInterface="at.ac.tuwien.infosys.aggr.cloudopt.collaboration.ICollaborativeNode")
public abstract class CollaborativeNodeImpl extends AbstractNode implements ICollaborativeNode{
	Logger logger = Logger.getLogger(CollaborativeNodeImpl.class);
	
	// url, node
	private Map<String, EndpointReference> nodes = Collections.synchronizedMap(new HashMap<String, EndpointReference>());
	
//	@Override
//	public void registerNewNode(NodeURI uri) {
//		// TODO Auto-generated method stub
//		logger.debug("Registering new collaborative node, url: " + uri.getUri());
//	}
	
	@Override
	public void registerNewNode(EndpointReference nodeEndpointReference) {
		logger.debug("Registering new collaborative node, url: " + nodeEndpointReference.getAddress());
	}

	@Override
	public void hello(MyString hello) {
		System.out.println(hello + " and I am " + this);
		
	}

	@Override
	public void hello1(String hello) {
		System.out.println(hello + " and I am " + this);
		
	}
}
