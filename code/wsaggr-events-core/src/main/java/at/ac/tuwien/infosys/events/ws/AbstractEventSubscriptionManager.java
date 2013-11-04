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

package at.ac.tuwien.infosys.events.ws;

import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.apache.log4j.Logger;

import at.ac.tuwien.infosys.events.ws.EventSubscribeFilter;
import at.ac.tuwien.infosys.util.Util;
import at.ac.tuwien.infosys.ws.EndpointReference;

public abstract class AbstractEventSubscriptionManager implements EventSubscriptionManager{

	protected static class EventSubscriptionInfo {
		
		private EndpointReference endpointReference;
		private Date expires;
		
		public EndpointReference getEndpointReference() {
			return endpointReference;
		}
		public void setEndpointReference(EndpointReference endpointReference) {
			this.endpointReference = endpointReference;
		}
		
		public Date getExpires() {
			return expires;
		}
		public void setExpires(Date expires) {
			this.expires = expires;
		}
		
	}
	
	private static final Logger LOGGER = Util.getLogger(AbstractEventSubscriptionManager.class);	
	
	private final Object syncRoot = new Object();
	
	private final EndpointReference epr;

	private Map<String, EventSubscriptionInfo> eventSubscription = new HashMap<String, EventSubscriptionInfo>();
	
	
	public AbstractEventSubscriptionManager(EndpointReference epr){
		this.epr = epr;
	}
	
	public EndpointReference getEPR(){
		return this.epr;
	}
	
	protected Collection<EventSubscriptionInfo> getSubscriptions() {
		return eventSubscription.values();
	}
	
	protected EventSubscriptionInfo getSubscription(String id){
		return eventSubscription.get(id);
	}
	
	@Override
	public EventSubscribeResponse subscribe(EventSubscribeRequest request) {
		final EventSubscribeResponse retVal = new EventSubscribeResponse();
		
		final EventSubscriptionInfo info = new EventSubscriptionInfo();
		info.setEndpointReference(request.getEndTo());
		info.setExpires(request.getExpires());
		final String id = UUID.randomUUID().toString();
		
		Util util = new Util();
		
		synchronized (syncRoot) {
			try {
				final EndpointReference epr = new EndpointReference(util.xml.toElement(getEPR()));
				epr.addReferenceParameter(util.xml.toElement(
						"<tns:Identifier xmlns:tns=\"" + 
								WSEvent.NAMESPACE + "\">" + id + "</tns:Identifier>"));
				retVal.setSubscriptionManager(epr);
				eventSubscription.put(id, info);
				onSubscribe(id, request.getFilter());
			} catch (Exception e) {
				//TODO: check the exception handling
				LOGGER.error(e.getMessage(), e);
			}
			
		}
		return retVal;
	}
	
	
	public abstract void onSubscribe(String endpointId, EventSubscribeFilter filter) throws Exception;
	

	@Override
	public EventRenewResponse renew(EventRenewRequest request) {
//		final EventRenewResponse retVal = new EventRenewResponse();		
//		
//		synchronized (syncRoot) {
//			//TODO: must be implemented
//		}
		throw new UnsupportedOperationException();
	}
	

	@Override
	public EventUnsubscribeResponse unsubscribe(EventUnsubscribeRequest request) {
//		final EventUnsubscribeResponse retVal = new EventUnsubscribeResponse();		
//		
//		synchronized (syncRoot) {
//			//TODO: must be implemented
//		}
		throw new UnsupportedOperationException();
	}
	
	protected abstract void onUnsubscribe(String endpointId);
	

	@Override
	public EventGetStatusResponse getStatus(EventGetStatusRequest request) {
//		final EventGetStatusResponse retVal = new EventGetStatusResponse();		
//		
//		synchronized (syncRoot) {
//			//TODO: must be implemented
//		}
		throw new UnsupportedOperationException();
	}		
	

}
