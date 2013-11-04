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
import javax.jws.WebService;
import javax.jws.soap.SOAPBinding;
import javax.jws.soap.SOAPBinding.ParameterStyle;
import javax.jws.soap.SOAPBinding.Style;
import javax.jws.soap.SOAPBinding.Use;

import org.apache.log4j.Logger;

import at.ac.tuwien.infosys.events.ws.EventSubscribeFilter;
import at.ac.tuwien.infosys.events.ws.EventSubscribeRequest;
import at.ac.tuwien.infosys.events.ws.EventSubscribeResponse;
import at.ac.tuwien.infosys.events.ws.EventSubscriptionEndRequest;
import at.ac.tuwien.infosys.events.ws.WSEvent;
import at.ac.tuwien.infosys.events.ws.WebServiceClientUtil;
import at.ac.tuwien.infosys.util.Util;
import at.ac.tuwien.infosys.ws.AbstractNode;
import at.ac.tuwien.infosys.ws.EndpointReference;

@WebService(targetNamespace = WSEvent.NAMESPACE)
public class EventConsumerNode extends AbstractNode implements EventSubscriber, NodeState {

	private static final Logger LOGGER = Util.getLogger(EventConsumerNode.class);
	
	private EndpointReference eventSourceEPR;
	private String subscribeTicket;

	private EventSubscribeFilter filter;
	
	private final Object syncRoot = new Object();
	
	public EventConsumerNode(EndpointReference epr) {
		setEpr(epr);
	}
	
	public EndpointReference getEventSourceEPR() {
		return eventSourceEPR;
	}

	public void setEventSourceEPR(EndpointReference eventSourceEPR) {
		this.eventSourceEPR = eventSourceEPR;
	}
	

	public EventSubscribeFilter getFilter() {
		return filter;
	}

	public void setFilter(EventSubscribeFilter filter) {
		this.filter = filter;
	}


	@Override
	@WebMethod(operationName = "End")
	@SOAPBinding(style = Style.DOCUMENT, use = Use.LITERAL, parameterStyle = ParameterStyle.BARE)	
	public void end(EventSubscriptionEndRequest request) {			
		synchronized (syncRoot) {
			final String identifier = request.getSubscriptionManager().getReferenceParameters().getByName("Identifier").getTextContent();
			if(subscribeTicket != null && subscribeTicket.equals(identifier)){
				LOGGER.debug(String.format("Consumer '%s' subscription '%s' ended from manager.", getEPR().getAddress(), subscribeTicket));
				subscribeTicket = null;
			}
		}
	}
	
	@Override
	@WebMethod(operationName = "Notify")
	@SOAPBinding(style = Style.DOCUMENT, use = Use.LITERAL, parameterStyle = ParameterStyle.BARE)	
	public void notify(@WebParam EventConsumerNotification event) {				
		LOGGER.debug(String.format("Aggregator notify '%s': %s", subscribeTicket, event.getContent()));
	}
	
	
	@WebMethod(operationName="event")
	@SOAPBinding(style = Style.DOCUMENT, use = Use.LITERAL, parameterStyle = ParameterStyle.BARE)
	public void processEvent(@WebParam WSEvent event) throws Exception {
		LOGGER.debug(String.format("Event '%s' received.", event.getIdentifier()));
		onEvent(event);
	}
	
	protected void onEvent(WSEvent event) throws Exception {
		
	}
	
	@WebMethod
	@Override
	public void start() throws Exception {
		synchronized (syncRoot) {
			if(subscribeTicket == null){
				final EventSubscribeRequest request = new EventSubscribeRequest();
				final EndpointReference epr = new EndpointReference(getEPR());
				request.setEndTo(epr);
				
				if(getFilter() != null){
					final EventSubscribeFilter filter = new EventSubscribeFilter(getFilter());				
					request.setFilter(filter);
				}
				
				final EventSubscribeResponse response = WebServiceClientUtil.execute(getEventSourceEPR(), request, EventSubscribeResponse.class);
				subscribeTicket = response.getSubscriptionManager().getReferenceParameters().getByName("Identifier").getTextContent();
				LOGGER.debug(String.format("Consumer '%s' started with ticket '%s'.", getEPR().getAddress(), subscribeTicket));
			}
		}
	}
	
	@WebMethod
	@Override
	public void stop() {
		synchronized (syncRoot) {
			if(subscribeTicket != null){
				//subscribeTicket.subscriptionManager.
				//TODO: must be implemented
			}
			LOGGER.debug(String.format("Consumer '%s' stopped.", getEPR().getAddress()));
		}
	}
		
}
