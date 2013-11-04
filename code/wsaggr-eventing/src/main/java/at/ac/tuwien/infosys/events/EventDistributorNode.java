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

package at.ac.tuwien.infosys.events;

import javax.jws.WebMethod;
import javax.jws.WebParam;
import javax.jws.WebResult;
import javax.jws.WebService;
import javax.jws.soap.SOAPBinding;
import javax.jws.soap.SOAPBinding.ParameterStyle;
import javax.jws.soap.SOAPBinding.Style;
import javax.jws.soap.SOAPBinding.Use;

import org.apache.log4j.Logger;

import at.ac.tuwien.infosys.events.ws.EventSubscriptionManager;
import at.ac.tuwien.infosys.events.ws.EventSubscriptionManagerProcessor;
import at.ac.tuwien.infosys.events.ws.WSEvent;
import at.ac.tuwien.infosys.util.Util;
import at.ac.tuwien.infosys.ws.EndpointReference;

@WebService(targetNamespace = WSEvent.NAMESPACE)
public class EventDistributorNode extends EventSubscriptionNode implements EventSubscriptionManager {

	private static final Logger LOGGER = Util.getLogger(EventDistributorNode.class);

	public EventDistributorNode(EndpointReference epr,
			EventSubscriptionManagerProcessor eventProcessor) {
		super(epr, eventProcessor);
	}
	
	public EventDistributorNode(EndpointReference epr) {
		super(epr);
	}

	@WebMethod(operationName="event")
	@WebResult(name = "result")
	@SOAPBinding(style = Style.DOCUMENT, use = Use.LITERAL, parameterStyle = ParameterStyle.BARE)
	public void processEvent(@WebParam WSEvent event) throws Exception {
		//TODO: check for the return type
		//WSEvent event = new WSEvent();
		//Object o = event.getContent();
		LOGGER.debug(String.format("Received event '%s'.", event.getIdentifier()));
		getEventProcessor().processEvent(event);
	}
	

}
