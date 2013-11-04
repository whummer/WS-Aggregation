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

package at.ac.tuwien.infosys.aggr.events.query;

import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.namespace.QName;

import org.apache.log4j.Logger;
import org.w3c.dom.Element;

import at.ac.tuwien.infosys.aggr.monitor.ModificationNotification;
import at.ac.tuwien.infosys.aggr.node.AggregatorNode;
import at.ac.tuwien.infosys.aggr.node.Registry;
import at.ac.tuwien.infosys.aggr.performance.EventTransferRateMeasurer;
import at.ac.tuwien.infosys.aggr.request.AbstractInput.InputWrapper;
import at.ac.tuwien.infosys.aggr.request.EventingInput;
import at.ac.tuwien.infosys.aggr.request.NonConstantInput;
import at.ac.tuwien.infosys.aggr.request.RequestInput;
import at.ac.tuwien.infosys.aggr.util.ServiceClientFactory;
import at.ac.tuwien.infosys.events.ws.WSEvent;
import at.ac.tuwien.infosys.util.Configuration;
import at.ac.tuwien.infosys.util.Util;
import at.ac.tuwien.infosys.util.coll.DoubleHashSet;
import at.ac.tuwien.infosys.util.perf.MemoryAgent;
import at.ac.tuwien.infosys.util.xml.XMLUtil;
import at.ac.tuwien.infosys.ws.EndpointReference;
import at.ac.tuwien.infosys.ws.WebServiceClient;
import at.ac.tuwien.infosys.ws.request.InvocationRequest;
import at.ac.tuwien.infosys.ws.request.InvocationResult;

@SuppressWarnings("unchecked")
public class EventStoresManager {

	private static final Logger logger = Util.getLogger(EventStoresManager.class);
	private static final String DEFAULT_QUERIER_CLASS = "at.ac.tuwien.infosys.events.query.EventQuerierMXQuery";
	public static Class<? extends EventQuerier> querierClazz;
	public static EventQuerier querierInstance;

	private Util util = new Util();
	private XMLUtil xmlUtil = util.xml;
	private AggregatorNode owner;
	private EventTransferRateMeasurer<String> measurer = new EventTransferRateMeasurer<String>();

	static {
		try {
			String className = Configuration.getValue("wsaggr.query.eventing.engine");
			querierClazz = (Class<EventQuerier>)Class.forName(className);
			querierInstance = querierClazz.newInstance();
			querierInstance.close();
		} catch (Exception e) {
			logger.info("Could not load class " + Configuration.getValue("wsaggr.query.eventing.engine") + 
					" with key 'wsaggr.query.eventing.engine' from configuration. Using default: " + DEFAULT_QUERIER_CLASS);
			try {
				querierClazz = (Class<EventQuerier>)Class.forName(DEFAULT_QUERIER_CLASS);
				querierInstance = querierClazz.newInstance();
				querierInstance.close();
			} catch (Exception e1) {
				logger.error("Could not load event querier class: " + DEFAULT_QUERIER_CLASS, e1);
			}
		}
	}

	protected static class EventStreamProps {
		String eventStreamID;
		EventStream stream;
		InputWrapper input;
		Map<String,List<EventQuerier>> topologyToQueriers = new HashMap<String, List<EventQuerier>>();
		
		private EventStreamProps(EventStream s, String id, NonConstantInput input) {
			this.eventStreamID = id;
			this.stream = s;
			this.input = new InputWrapper(input);
		}
		private List<EventQuerier> getQueriers() {
			List<EventQuerier> result = new LinkedList<EventQuerier>();
			for(List<EventQuerier> q : topologyToQueriers.values()) {
				result.addAll(q);
			}
			return result;
		}
		public boolean removeQuerier(EventQuerier q) {
			boolean done = false;
			for(String t : new HashSet<String>(topologyToQueriers.keySet())) {
				done |= topologyToQueriers.get(t).remove(q);
				if(topologyToQueriers.get(t).isEmpty()) {
					topologyToQueriers.remove(t);
				}
			}
			return done;
		}

		public void addQuerier(String topologyID, EventQuerier querier) {
			if(!topologyToQueriers.containsKey(topologyID))
				topologyToQueriers.put(topologyID, new LinkedList<EventQuerier>());
			topologyToQueriers.get(topologyID).add(querier);
		}
	}
	
	/** eventStreamID -> event stream */
	private final Map<String, EventStreamProps> eventStreams = new HashMap<String, EventStreamProps>();
	/** topologyID *<->* eventStreamID */
	private final DoubleHashSet<String,String> topologyToEventStreamID = new DoubleHashSet<String, String>();
	private final List<String> terminatedEventStreams = new LinkedList<String>();

	@XmlRootElement
	public static class ByteArray {
		public byte[] bytes;
		public String string;
	}


	public EventStoresManager(AggregatorNode owner) {
		this.owner = owner;
	}


	/**
	 * @return mapping topologyID->[inputs...]. 
	 * Inputs of all topologies that "listen" for 
	 * data from a given event stream ID
	 */
	public Map<String,Set<NonConstantInput>> getTopologyInputsForEventStream(String eventStreamID) {
		Map<String,Set<NonConstantInput>> map = new HashMap<String, Set<NonConstantInput>>();
		synchronized (eventStreams) {
			if(!eventStreams.containsKey(eventStreamID)) {
				return map;
			}
		}
		synchronized (eventStreams) {
			for(String topologyID : eventStreams.get(eventStreamID).topologyToQueriers.keySet()) {
				if(!map.containsKey(topologyID)) {
					map.put(topologyID, new HashSet<NonConstantInput>());
				}
				for(EventQuerier q : eventStreams.get(eventStreamID).topologyToQueriers.get(topologyID)) {
					map.get(topologyID).add(q.getInput());
				}
				
			}
		}
		return map;
	}

	public EventStream getEventStreamForInput(NonConstantInput input, 
			String topologyID) throws Exception  {
		return getEventStreamForInput(input, topologyID, -1);
	}
	public EventStream getEventStreamForInput(NonConstantInput input, 
			String topologyID, int maxBufferSize) throws Exception {
		return getEventStreamForInput(input, topologyID, true, maxBufferSize);
	}
	public EventStream getEventStreamForInput(NonConstantInput input, 
			String topologyID, boolean createNewIfNecessary) throws Exception  {
		return getEventStreamForInput(input, topologyID, createNewIfNecessary, -1);
	}
	public EventStream getEventStreamForInput(NonConstantInput input, String topologyID, 
			boolean createNewIfNecessary, int maxBufferSize) throws Exception  {
		InputWrapper inputWrapper = new InputWrapper(input);
		String uuid = null;
		synchronized (eventStreams) {
			for(String eventStreamID : eventStreams.keySet()) {
				if(eventStreams.get(eventStreamID).input.equals(inputWrapper)) {
					uuid = eventStreamID;
					break;
				}
			}	
		}
		if(uuid == null) {
			if(!createNewIfNecessary) {
				return null;
			}
			uuid = UUID.randomUUID().toString();
			EventStream s = maxBufferSize > 0 ? 
					querierInstance.newStream(maxBufferSize) :
					querierInstance.newStream();
			initEventStream(s, input, uuid);
			return s;
		} else {
			if(logger.isDebugEnabled()) logger.debug("Re-using event stream buffer for input! - " + input);
			//System.out.println("Re-using event stream buffer for input! - " + input);
		}
		if(topologyID != null) {
			topologyToEventStreamID.put1(topologyID,uuid);
		}
		return eventStreams.get(uuid).stream;
	}

	public EventStream reconstructEventStreamFromDump(NonConstantInput input, String dump, String topologyID) throws Exception {
		Element events = xmlUtil.toElement(dump);
		String eventStreamID = events.getAttribute("eventStreamID");
		EventStream s = querierInstance.newStream();
		initEventStream(s, input, eventStreamID);
		topologyToEventStreamID.put1(topologyID, eventStreamID);
		List<Element> list = xmlUtil.getChildElements(events);
		for(Element event : list) {
			querierInstance.addEvent(s, event);
		}
		return s;
	}
	
	/**
	 * This method initializes an event stream by subscribing at the target
	 * eventing service specified in the given '<i>input</i>'. If 
	 * '<i>input</i>' is not an EventingInput OR if the dontSubscribe
	 * flag is set, the method does not subscribe with the target service.
	 * @param stream
	 * @param input
	 * @param eventStreamID
	 * @throws Exception
	 */
	private void initEventStream(EventStream stream, 
			NonConstantInput input, String eventStreamID) throws Exception {

		stream.eventStreamID = eventStreamID;
		synchronized (eventStreams) {
			eventStreams.put(eventStreamID, new EventStreamProps(stream, eventStreamID, input));
		}

		/* 	We distinguish between "actual" event streams from inputs using 
			WS-Eventing (which require a <Subscribe .. SOAP call), and event streams
			which represent the updates arriving from monitoring a certain page or
			service (<input interval="X"... ). Subcription is only required for 
			EventingInput, hence we return from this method if input is not an 
			EventingInput.. */

		if(!(input instanceof EventingInput)) {
			return;
		}
		
		/* Also return if the EventingInput has the "dontSubscribe" flag set! */
		if(((EventingInput)input).isDontSubscribe()) {
			if(logger.isDebugEnabled()) logger.debug("Not going to subscribe because 'dontSubscribe' flag is set in input: " + input);
			return;
		}
		
		EventingInput eventingInput = (EventingInput)input;

		WebServiceClient client = ServiceClientFactory.getClient(eventingInput);
		EndpointReference epr = new EndpointReference(owner.getEPR());
		
		/* If this node cannot be directly reached from the event-emitting service,
			we set the receiver EPR to the address of the Gateway and add a 
			parameter to the EPR which tells the Gateway where to forward the event to. */
		if(Configuration.isLikelyBehindFirewall()) {
			EndpointReference ownerEPR = epr;
			logger.info("The node is possibly behind a proxy/router/firewall. " +
					"-> Telling the gateway to receive and forward the events on our behalf.");
			epr = new EndpointReference(Registry.getRegistryProxy().getGateway().getEPR());
			QName name = EventingQueryCoordinator.HEADER_NAME_FORWARD_EVENT_TO;
			epr.addReferenceParameter(ownerEPR.toElement("tns1:" + name.getLocalPart() + " xmlns:tns1=\"" + name.getNamespaceURI() + "\""));
		}
		
		Element param = WSEvent.createHeader(ModificationNotification.
				SOAP_HEADER_EVENT_STREAM_ID, eventStreamID);
		epr.addReferenceParameter(param);
		String notifyTo = epr.toString("wse:NotifyTo xmlns:wse=\"" + WSEvent.NAMESPACE + "\"");
		RequestInput subscribe = new RequestInput(xmlUtil.toElement(
				"<wse:Subscribe xmlns:wse=\"" + WSEvent.NAMESPACE + "\"><wse:Delivery>" + 
				notifyTo +
				"</wse:Delivery>" + 
				eventingInput.getFilterToString() +
				"</wse:Subscribe>"));
		subscribe.addSoapHeader(WSEvent.HEADER_WSE_SUBSCRIBE);
		InvocationRequest req = subscribe.getRequest();
		logger.info("Sending <wse:Subscribe> message to service " + eventingInput.getServiceURL());
		InvocationResult ir = client.invoke(req);
		Element mgr = ir.getResponseBody();
		if(mgr.getLocalName().equals("SubscribeResponse")) {
			mgr = xmlUtil.getFirstChild(mgr);
		}
		Element subscrMgr = xmlUtil.changeRootElementName(mgr, 
				"wsa123:EndpointReference xmlns:wsa123=\"" + 
				EndpointReference.NS_WS_ADDRESSING + "\"");

		stream.subscriptionManager = xmlUtil.toJaxbObject(EndpointReference.class, subscrMgr);

		if(logger.isDebugEnabled()) logger.debug("Subscription Manager EPR: " + stream.subscriptionManager);

	}

	public void handleEvent(ModificationNotification event) throws Exception {
		
		Element header = event.getHeader(ModificationNotification.SOAP_HEADER_EVENT_STREAM_ID);
		if(header == null && event.getEventStreamID() != null) {
			try {
				header = WSEvent.createHeader(ModificationNotification.SOAP_HEADER_EVENT_STREAM_ID, event.getEventStreamID());
				event.addHeader(header);
			} catch (Exception e) {
				logger.warn("Could not add wsaggr:TopologyID header", e);
			}
		}
		if(header == null || header.getTextContent() == null) {
			logger.warn("Header with eventStreamID missing in incoming event.");
			return;
		}
		String eventStreamID = header.getTextContent().trim();

		Element data = null;
		try {
			data = event.getDataAsElement();
		} catch (Exception e) {
			logger.warn("Exception when converting event data: " + xmlUtil.toString(data), e);
		}
		
		measurer.addTransmittedData(eventStreamID, data);

		List<EventQuerier> theStores = getQueriers(eventStreamID);
		if(theStores == null || !eventStreams.containsKey(eventStreamID)) {
			try {
				if(event.getHeader(EventingQueryCoordinator.HEADER_NAME_EVENT_FORWARDED) == null) {
					String e = xmlUtil.toString(event);
					synchronized (eventStreams) {
						if(logger.isInfoEnabled()) logger.info(owner + " Could not find event store for event " + e + 
							" - Event headers: " + event.getHeadersAsString() + ". existing eventStreamIDs: " + 
							eventStreams.keySet());
					}

					if(!eventStreams.containsKey(eventStreamID) && !terminatedEventStreams.contains(eventStreamID)) {
						throw new RuntimeException("Cannot find stream store for eventStreamID " + eventStreamID);
					}

					logger.info("Trying to unsubscribe from event stream with ID " + eventStreamID);
					unsubscribeIfUnused(eventStreamID);
				}

			} catch (Exception e2) { 
				throw new RuntimeException("Could not find event store for event. Please unsubscribe me.", e2);
			}
			return;
		}

		try {

			if(logger.isDebugEnabled()) logger.debug("Adding event to event store: " + data);

			EventStream stream = eventStreams.get(eventStreamID).stream;
			querierInstance.addEvent(stream, data);
			stream.notifyDirectListeners(data);
			
			
		} catch (Exception e) {
			logger.warn("Exception when storing event data: " + xmlUtil.toString(data), e);
		}
		
	}

	public NonConstantInput getInputByEventStreamID(String eventStreamID) {
		EventStreamProps p = eventStreams.get(eventStreamID);
		if(p == null || p.input == null)
			return null;
		return (NonConstantInput)p.input.input;
	}

	public void freeResources(String topologyID) throws Exception {
		if(!topologyToEventStreamID.containsKey1(topologyID)) {
			logger.info("Could not find topologyID '" + topologyID + "' in event store manager");
		}
		else {
			for(String streamID : new LinkedList<String>(topologyToEventStreamID.get1(topologyID))) {
				try {
					if(topologyToEventStreamID.containsKey2(streamID)) {
						topologyToEventStreamID.remove(topologyID, streamID);
						unsubscribeIfUnused(streamID);
					}
				} catch (Exception e) {
					logger.info(e);
				}
			}
		}
		topologyToEventStreamID.remove1(topologyID);
		logger.info("freeResources: " + owner + " remaining streams " + 
				eventStreams.size() + " - " + topologyToEventStreamID.maxSize());
	}
	
	public void unsubscribeUnusedStreams() throws Exception {
		for(String s : new LinkedList<String>(topologyToEventStreamID.get2())) {
			unsubscribeIfUnused(s);
		}
	}
	public void unsubscribeIfUnused(String streamID) throws Exception {
		
		if(!eventStreams.containsKey(streamID)) {
			if(logger.isDebugEnabled()) logger.debug("Event stream id " + streamID + " not contained in 'eventStreams' map.");
			return;
		}

		// TODO: synchronize topologyToQueriers!
		if(eventStreams.get(streamID).topologyToQueriers.isEmpty()) {
			if(logger.isInfoEnabled()) logger.info("Destroying event subscription for eventStreamID " + streamID);

			EventStream stream = eventStreams.get(streamID).stream;
			stream.isActive.set(false);
			RequestInput unsubscribe = new RequestInput(xmlUtil.toElement(
					"<wse:Unsubscribe xmlns:wse=\"" + WSEvent.NAMESPACE + "\">" + 
					"</wse:Unsubscribe>"));
			unsubscribe.addSoapHeader(xmlUtil.toElement("<wsa:Action xmlns:wsa=\"" + 
					EndpointReference.NS_WS_ADDRESSING + "\">" + WSEvent.WSA_ACTION_UNSUBSCRIBE + 
					"</wsa:Action>"));
			try {
				WebServiceClient c = WebServiceClient.getClient(stream.subscriptionManager);
				c.invoke(unsubscribe.getRequest());
			} catch (Exception e) {
				logger.warn("Unable to invoke Unsubscribe operation, EPR: " + stream.subscriptionManager, e);
			}

//			if(!eventStreams.get(streamID).getQueriers().isEmpty()) {
//				// TODO remove? (can we ever end up in here?)
//				logger.info("Cannot unscubscribe as there still exist event queriers for event stream " + streamID);
//				return;
//			}

			topologyToEventStreamID.remove2(streamID);
			synchronized (eventStreams) {
				eventStreams.remove(streamID);
				terminatedEventStreams.add(streamID);
				measurer.removeStream(streamID);
			}
		}
	}
	
	public long getTotalEventBufferSize() {
		if(eventStreams.size() <= 0)
			return 0;
		try {
			List<Object> contexts = new LinkedList<Object>();
			synchronized (eventStreams) {
				for(EventStreamProps p : eventStreams.values()) {
					contexts.addAll(p.stream.contexts);
				}
			}
			// we assume here that all buffers are accessible from the context variable(s)...
			return MemoryAgent.deepSizeOf(contexts);
		} catch (Exception e) { }
		return -1;
	}
	
	public long getEventBufferSize(EventStream stream, IdentityHashMap<Object, Object> visitedObjects) {
		try {
			return MemoryAgent.deepSizeOf(stream.buffer, stream.bufferLock, visitedObjects);
		} catch (Exception e) { }
		try {
			return getEventBufferDump(stream).length();
		} catch (Exception e) { }
		return -1;
	}

	public String getEventBufferDump(EventStream stream) throws Exception {
		return querierInstance.getEventDumpAsString(stream);
	}

	public EventQuerier addQuerier(String topologyID, String query, 
			NonConstantInput input, EventStream eventStream) throws Exception {
		EventQuerier querier = null;
		if(util.str.isEmpty(query)) {
			/* create pass-through querier.. */
			querier = new DefaultPassThroughQuerier();
			eventStream.addDirectListener(querier);
		} else {
			querier = querierClazz.newInstance();
		}
		querier.initQuery(input, query, eventStream);
		if(eventStream.eventStreamID == null) {
			logger.warn("Event Stream ID is null for EventStream " + eventStream);
			throw new RuntimeException("Event Stream ID is null for EventStream " + eventStream);
		}
		synchronized (eventStreams) {
			eventStreams.get(eventStream.eventStreamID).addQuerier(topologyID, querier);
		}
		//System.out.println("Create querier: " + topologyID);
		//Thread.dumpStack(); // TODO
		return querier;
	}
	
	public boolean closeAndRemoveStore(EventQuerier q) throws Exception {
		synchronized (eventStreams) {
			boolean deleted = false;
			for(String streamID : new HashSet<String>(eventStreams.keySet())) {
				boolean del = eventStreams.get(streamID).removeQuerier(q);
				if(del) {
					deleted = true;
					unsubscribeIfUnused(streamID);
				}
			}
			q.close();
			
			if(!deleted) {
				logger.info(owner + ": Event querier " + q + " to be terminated could not be found.");
				Thread.dumpStack();
			} else
				logger.info(owner + ": Event querier " + q + " closed and removed.");
			return deleted;
		}
	}

	public String getTopologyID(EventQuerier q) {
		synchronized (eventStreams) {
			for(EventStreamProps e : eventStreams.values()) {
				for(String t : e.topologyToQueriers.keySet()) {
					if(e.topologyToQueriers.get(t).contains(q))
						return t;
				}
			}
		}
		return null;
	}
	
	public List<EventQuerier> getQueriers(String eventStreamID) {
		EventStreamProps p = eventStreams.get(eventStreamID);
		if(p == null)
			return new LinkedList<EventQuerier>();
		return p.getQueriers();
	}
	public EventQuerier getQuerierByQuery(String eventStreamID, String query) {
		if(query == null) {
			logger.info("Null query provided as input to getQuerierByQuery.");
			return null;
		}
		for(EventQuerier q : eventStreams.get(eventStreamID).getQueriers()) {
			String q1 = query.trim();
			String q2 = q.getOriginalQuery().trim();
			if(q1.equals(q2)) {
				return q;
			}
		}
		return null;
	}

	public EventStream getEventStream(String eventStreamID) {
		EventStreamProps p = eventStreams.get(eventStreamID);
		if(p == null)
			return null;
		return eventStreams.get(eventStreamID).stream;
	}

	public EventTransferRateMeasurer<String> getMeasurer() {
		return measurer;
	}

}
