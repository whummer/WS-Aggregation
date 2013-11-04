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

package net.esper.adapter.ws;

import javax.jws.WebService;

import at.ac.tuwien.infosys.aggr.request.EventingOutput;
import at.ac.tuwien.infosys.events.EventSubscriptionNode;
import at.ac.tuwien.infosys.events.ws.DefaultEventSubscriptionManager;
import at.ac.tuwien.infosys.events.ws.EventSubscriptionManager;
import at.ac.tuwien.infosys.events.ws.WSEvent;
import at.ac.tuwien.infosys.util.Configuration;
import at.ac.tuwien.infosys.ws.EndpointReference;

@WebService(targetNamespace = Configuration.NAMESPACE)
public class EventingOutputNode extends EventSubscriptionNode implements EventSubscriptionManager{

	public EventingOutputNode(EndpointReference epr) {
		super(epr);
	}

	public void configure(EventingOutput output) throws Exception {
		setEPR(output.endTo);
		setEventProcessor(new DefaultEventSubscriptionManager(output.endTo));
		deploy(output.endTo.getAddress());
	}

	public void processEvent(WSEvent wsEvent) {
		getEventProcessor().processEvent(wsEvent);
	}	

}
