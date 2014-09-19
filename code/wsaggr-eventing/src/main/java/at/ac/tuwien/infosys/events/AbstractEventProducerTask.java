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

import io.hummer.util.Util;
import io.hummer.util.ws.EndpointReference;
import io.hummer.util.ws.WebServiceClient;

import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TimerTask;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.log4j.Logger;
import org.w3c.dom.Element;

import at.ac.tuwien.infosys.aggr.monitor.ModificationNotification;
import at.ac.tuwien.infosys.aggr.request.RequestInput;
import at.ac.tuwien.infosys.aggr.xml.XPathProcessor;
import at.ac.tuwien.infosys.events.ws.EventSubscribeFilter;
import at.ac.tuwien.infosys.events.ws.WSEvent;

public abstract class AbstractEventProducerTask extends TimerTask implements Serializable {
	private static final long serialVersionUID = 655288298620712088L;

	private static final Logger LOGGER = Util.getLogger(AbstractEventProducerTask.class);
	private static final int MAX_SEND_ATTEMPTS = 2;
	private static final String NAMESPACE_XPATH = "http://www.w3.org/TR/1999/REC-xpath-19991116";

	private long count = 0;

	protected final Map<String, EndpointReference> eventConsumers = new HashMap<String, EndpointReference>();
	private final Map<String, WebServiceClient> eventConsumerClients = new HashMap<String, WebServiceClient>();
	private final Map<String, EventSubscribeFilter> eventFilters = new HashMap<String, EventSubscribeFilter>();
	private final Map<EndpointReference,AtomicInteger> failedSendAttempts = new HashMap<EndpointReference, AtomicInteger>();
	private final Util util = new Util();


	public AbstractEventProducerTask() { }
	public AbstractEventProducerTask(AbstractEventProducerTask toCopy) { 
		this.count = toCopy.count;
		this.eventConsumers.putAll(toCopy.eventConsumers);
		this.eventConsumerClients.putAll(toCopy.eventConsumerClients);
	}

	protected abstract WSEvent createEvent(WSEvent aggrEvent);

	public abstract AbstractEventProducerTask copy();
	
	private transient ThreadPoolExecutor executor;

	@Override
	public void run() {
		try {
			synchronized (eventConsumers) {
				if(LOGGER.isTraceEnabled()) LOGGER.trace("number of event consumers: " + eventConsumers.size());
			}

			WSEvent event = new WSEvent();
			event.setIdentifier(getCount());
			event = createEvent(event);
			RequestInput input = new RequestInput(util.xml.toElement(event));
			input.getSoapHeaders().addAll(event.getHeadersCopy());
			Element eventEl = util.xml.toElement(event);
			eventEl = util.xml.clone(eventEl); 	// we have to do this, because apparently the saxon xpath 
											// processor cannot handle jaxb-created elements!

			Set<String> subscriptionIDs;
			synchronized (eventConsumers) {
				subscriptionIDs = new HashSet<String>(eventConsumerClients.keySet());
			}

			for(final String subscriptionID : subscriptionIDs) {
				if(eventFilters.containsKey(subscriptionID)) {
					EventSubscribeFilter filter = eventFilters.get(subscriptionID);
					event = filterEvent(event, filter, eventEl);
					if(event == null)
						continue;
					input.setTheContent(util.xml.toElement(event));
				}
				
				final WebServiceClient client = eventConsumerClients.get(subscriptionID);
				final RequestInput theInput = input.copyViaJAXB();
				
				// perform each notification invocation in its own thread
				if(client != null) {
					Runnable r = new Runnable() {
						public void run() {
							EndpointReference epr = null;
							synchronized (eventConsumers) {
								epr = eventConsumers.get(subscriptionID);
							}
							if(epr != null) {
								
//								try {
//									System.out.println("subscription: " + epr.getPropOrParamByName(ModificationNotification.SOAP_HEADER_EVENT_STREAM_ID).getTextContent() + 
//											"\n\t - " + util.toStringCanonical(util.getFirstChild(util.toElement(eventFilters.get(subscriptionID)))) + 
//											"\n\t - " + util.toStringCanonical(theInput.getTheContentAsElement()));
//								} catch (Exception e1) { }
								
								if(!failedSendAttempts.containsKey(epr)) {
									failedSendAttempts.put(epr, new AtomicInteger());
								}
								try {
									client.invoke(theInput.getRequest());
									failedSendAttempts.get(epr).set(0);
								} catch (Exception e) {
									int attempts = failedSendAttempts.get(epr).incrementAndGet();
									String msg = "" + e + " - " + e.getCause() + " - " + (e.getCause() != null ? e.getCause().getCause() : "");
									if(msg.contains("unsubscribe me")) {
										attempts = Integer.MAX_VALUE;
									}
									if(attempts >= MAX_SEND_ATTEMPTS) {
										if(attempts >= Integer.MAX_VALUE)
											LOGGER.warn("! Unregistering subscriber upon request: " + client.getEndpointURL());
										else
											LOGGER.warn("! Unregistering subscriber after " + MAX_SEND_ATTEMPTS + " unsuccessful attempts to send event to " + client.getEndpointURL() + ": " + e);
										synchronized (eventConsumers) {
											eventConsumers.remove(subscriptionID);
											eventConsumerClients.remove(subscriptionID);
										}
									}
								}
							}
						}
					};
					getExecutor().execute(r);
				}
			}

		} catch (Exception e) {
			LOGGER.error(e.getMessage(), e);
		}
		count++;
	}
	
	protected WSEvent filterEvent(WSEvent event, EventSubscribeFilter filter, Element eventEl) throws Exception {
		if(filter.getContent() instanceof Element) {
			Element filterElement = (Element)filter.getContent();
			String theFilter = (filterElement).getTextContent();
			theFilter = theFilter == null ? "" : theFilter.trim();
			if(filterElement.getNodeName().equals("size")) {
				int size = Integer.parseInt(theFilter);
				ModificationNotification n = (ModificationNotification)event;
				int actualSize = util.xml.toString(event).length();
				while(actualSize < size) {
					util.xml.appendChild((Element)n.getData().get(0), util.xml.toElement("<foo>bar bar <test>bar bar bar</test>bar bar <test>bar bar bar</test></foo>"));
					actualSize = util.xml.toString(event).length();
				}
				return event;
			} else if(filterElement.getNodeName().equals("copy")) {
				if(util.xml.getChildElements(filterElement).size() > 0) {
					event.setContent(util.xml.getChildElements(filterElement).get(0));
					return event;
				}
			} else if(filter.getDialect() != null && filter.getDialect().equals(NAMESPACE_XPATH)) {
				String xpath = theFilter;
				if(!XPathProcessor.matches(xpath, eventEl)) {
					if(LOGGER.isTraceEnabled()) LOGGER.trace("Event element does not match xpath '" + xpath + "': " + util.xml.toString(eventEl));
					return null;
				} else {
					if(LOGGER.isTraceEnabled()) LOGGER.trace("Event element matches xpath '" + xpath + "': " + util.xml.toString(eventEl));
				}
			}
		}
		return event;
	}

	public long getCount() {
		return count;
	}
	public void setCount(long count) {
		this.count = count;
	}
	public Map<String, EndpointReference> getEventConsumers() {
		return eventConsumers;
	}
	public void addEventConsumer(String id, EndpointReference consumer, EventSubscribeFilter filter) throws Exception {
		synchronized (eventConsumers) {
			eventConsumers.put(id, consumer);
			eventConsumerClients.put(id, WebServiceClient.getClient(consumer));
		}
		if(filter != null) {
			synchronized (eventFilters) {
				eventFilters.put(id, filter);
			}
		}
	}
	public void removeEventConsumer(String identifier) {
		synchronized (eventConsumers) {
			eventConsumers.remove(identifier);
			eventConsumerClients.remove(identifier);
		}
	}

	public ThreadPoolExecutor getExecutor() {
		if(executor == null)
			executor = new ThreadPoolExecutor(10, 100, 20, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());
		return executor;
	}
}
