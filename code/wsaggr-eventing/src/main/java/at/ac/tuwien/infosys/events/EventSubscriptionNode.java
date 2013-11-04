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

import at.ac.tuwien.infosys.events.ws.DefaultEventSubscriptionManager;
import at.ac.tuwien.infosys.events.ws.EventGetStatusRequest;
import at.ac.tuwien.infosys.events.ws.EventGetStatusResponse;
import at.ac.tuwien.infosys.events.ws.EventRenewRequest;
import at.ac.tuwien.infosys.events.ws.EventRenewResponse;
import at.ac.tuwien.infosys.events.ws.EventSubscribeRequest;
import at.ac.tuwien.infosys.events.ws.EventSubscribeResponse;
import at.ac.tuwien.infosys.events.ws.EventSubscriptionManager;
import at.ac.tuwien.infosys.events.ws.EventSubscriptionManagerProcessor;
import at.ac.tuwien.infosys.events.ws.EventUnsubscribeRequest;
import at.ac.tuwien.infosys.events.ws.EventUnsubscribeResponse;
import at.ac.tuwien.infosys.events.ws.WSEvent;
import at.ac.tuwien.infosys.util.Util;
import at.ac.tuwien.infosys.ws.AbstractNode;
import at.ac.tuwien.infosys.ws.EndpointReference;

@WebService(targetNamespace = WSEvent.NAMESPACE)
public class EventSubscriptionNode extends AbstractNode implements EventSubscriptionManager {

	protected static final Logger LOGGER = Util.getLogger(EventSubscriptionNode.class);

	private EventSubscriptionManagerProcessor eventProcessor;
	
	public EventSubscriptionNode(EndpointReference epr) {
		this(epr, new DefaultEventSubscriptionManager(epr));
	}
	
	public EventSubscriptionNode(EndpointReference epr,
			EventSubscriptionManagerProcessor eventProcessor) {
		setEpr(epr);
		this.eventProcessor = eventProcessor;
	}

	@WebMethod(exclude=true)
	protected void setEventProcessor(
			DefaultEventSubscriptionManager eventProcessor) {
		this.eventProcessor = eventProcessor;
	}
	
	@WebMethod(exclude=true)
	public EventSubscriptionManagerProcessor getEventProcessor() {
		return eventProcessor;
	}

	
	@WebResult(name = "SubscribeResponse", targetNamespace = WSEvent.NAMESPACE)
	@SOAPBinding(style = Style.DOCUMENT, use = Use.LITERAL, parameterStyle = ParameterStyle.BARE)
	public EventSubscribeResponse subscribe(
			@WebParam(name = "Subscribe", targetNamespace = WSEvent.NAMESPACE) 
			EventSubscribeRequest request){
		return eventProcessor.subscribe(request);
	}

	@WebResult(name = "UnsubscribeResponse", targetNamespace = WSEvent.NAMESPACE)
	@SOAPBinding(style = Style.DOCUMENT, use = Use.LITERAL, parameterStyle = ParameterStyle.BARE)
	public EventUnsubscribeResponse unsubscribe(
			@WebParam(name = "Unsubscribe", targetNamespace = WSEvent.NAMESPACE) 
			EventUnsubscribeRequest request) {
		return eventProcessor.unsubscribe(request);
	}

	@WebResult(name = "RenewResponse", targetNamespace = WSEvent.NAMESPACE)
	@SOAPBinding(style = Style.DOCUMENT, use = Use.LITERAL, parameterStyle = ParameterStyle.BARE)
	public EventRenewResponse renew(
			@WebParam(name = "Renew", targetNamespace = WSEvent.NAMESPACE) 
			EventRenewRequest request) {
		return eventProcessor.renew(request);
	}

	@WebResult(name = "GetStatusResponse", targetNamespace = WSEvent.NAMESPACE)
	@SOAPBinding(style = Style.DOCUMENT, use = Use.LITERAL, parameterStyle = ParameterStyle.BARE)
	public EventGetStatusResponse getStatus(
			@WebParam(name = "GetStatus", targetNamespace = WSEvent.NAMESPACE) 
			EventGetStatusRequest request) {
		return eventProcessor.getStatus(request);
	}	

}
