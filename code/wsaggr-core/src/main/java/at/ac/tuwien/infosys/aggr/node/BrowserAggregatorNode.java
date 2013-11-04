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
package at.ac.tuwien.infosys.aggr.node;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

import javax.jws.WebMethod;
import javax.jws.WebParam;
import javax.jws.WebService;
import javax.jws.soap.SOAPBinding;
import javax.jws.soap.SOAPBinding.ParameterStyle;
import javax.xml.bind.annotation.XmlRootElement;

import org.w3c.dom.Element;

import at.ac.tuwien.infosys.aggr.monitor.ModificationNotification;
import at.ac.tuwien.infosys.aggr.monitor.ModificationNotification.EventStreamIdSOAPHeader;
import at.ac.tuwien.infosys.aggr.request.AggregationRequest;
import at.ac.tuwien.infosys.aggr.request.AggregationResponseConstructor;
import at.ac.tuwien.infosys.aggr.request.NonConstantInput;
import at.ac.tuwien.infosys.aggr.request.WAQLQuery.PreparationQuery;
import at.ac.tuwien.infosys.aggr.util.Invoker;
import at.ac.tuwien.infosys.aggr.websocket.AggregatorWebSocket;
import at.ac.tuwien.infosys.aggr.websocket.EventStreamData;
import at.ac.tuwien.infosys.aggr.websocket.WebSocketServer;
import at.ac.tuwien.infosys.util.Configuration;
import at.ac.tuwien.infosys.util.coll.BlockingMap;
import at.ac.tuwien.infosys.ws.EndpointReference;

@WebService(targetNamespace=Configuration.NAMESPACE)
@XmlRootElement
public class BrowserAggregatorNode extends AggregatorNode {
	
	// ALEX pick a specific aggregator node (implement browser choosing from a list)
	public static final String location = "wsaggr";
	public static final String connectionProtocol = "wsaggr";
	
	private static final List<BrowserAggregatorNode> browserAggregatorNodes = new ArrayList<BrowserAggregatorNode>();
	
	private static final Set<AggregatorWebSocket> browserSockets = new CopyOnWriteArraySet<AggregatorWebSocket>();
	
	private int port = 8082;
	private WebSocketServer webSocketServer;
	private BlockingMap<String,Element> blockingRequestMap = new BlockingMap<String,Element>();
	

	@Deprecated
	public BrowserAggregatorNode() {}
	
	public BrowserAggregatorNode(EndpointReference epr, boolean startWorkerThreads, int port) {
		super(epr, startWorkerThreads);
		
		// LOW when browsers can pick banodes from list uncomment line
		//this.port = port;
		
		webSocketServer = new WebSocketServer(this.port, this);
		webSocketServer.setBrowserAggregatorNode(this); 
		webSocketServer.start();
		browserAggregatorNodes.add(this);
	}

	/**
	 * This method is used for the "plain vanilla" request-response (not event-based) input processing. 
	 * The method receives an input XML element, selects an available browser aggregator, and sends a query which
	 * the browser applies to the input XML element.
	 */
	@WebMethod(exclude=true)
	public Element applyPrepQueries(Element inputResult, NonConstantInput input, 
			AggregationResponseConstructor resultContainer) throws Exception {

		/* select random browser node */
		// LOW pick specific browser
		AggregatorWebSocket browser = getRandomWebSocket();

		if(browser == null) {
			throw new AggregatorException(new RuntimeException("No browser aggregator node available."));
		}

		/* get the aggregation request which is currently being handled. */
		AggregationRequest request = resultContainer.getRequest();
		
		/* get the list of preparation queries we need to apply.. */
		List<PreparationQuery> queries = request.getMatchingQueries(input);

		/* retrieve XML data from external service */
		Element element = Invoker.getResultForInput(input).getResultAsElement();

		// apply preparation query/queries to element, using browser
		Date before = new Date();
		String messageID = browser.applyPrepQueries(request.getRequestID(), element, queries, blockingRequestMap);
		Element afterQueryProcessing = blockingRequestMap.get(messageID);
		Date after = new Date();
		
		double speed = after.getTime() - before.getTime();
		browser.addRequestStatistics(messageID, 
				queries.size(), 
				util.xml.toString(element).length(), 
				location, 
				util.xml.toString(afterQueryProcessing), 
				speed);
		
		return afterQueryProcessing;
	}

	/**
	 * This method is called if the user posts a query
	 */
	@Override
	public String addEventingInput(String topologyID, NonConstantInput input,
			AggregationRequest request, boolean startQueriers) throws Exception {

		/* the following call to super.addEventingInput creates a subscription at 
		 * the target service (from which we want to receive events), but does *not*
		 * create a querier for these events ('startQueriers' parameter is false) 
		 * because we are going to perform these queries on the browser side.. */
		String eventStreamID = super.addEventingInput(topologyID, input, request, false);

		/* usually this list should contain exactly one query. */
		List<PreparationQuery> queries = request.getMatchingQueries(input);

		// LOW pick specific browser
		AggregatorWebSocket browser = getRandomWebSocket();
		if(browser == null) {
			throw new AggregatorException(new RuntimeException("No browser aggregator node available."));
		}
		
		// send eventStreamID and query/queries to the browser.
		EventStreamData eventStream = new EventStreamData(topologyID, input.getUniqueID(), eventStreamID, queries);
		browser.saveEventStreamDataForAsynchronousEvents(eventStream);

		return eventStreamID;
	}

	/**
	 * This method is called if a new event is received asynchronously from one of the
	 * external data services. This method forwards the event to the responsible 
	 * browser Web socket for further processing.
	 */
	@SOAPBinding(parameterStyle=ParameterStyle.BARE) @WebMethod(operationName="Event")
	public void onEvent(
			@WebParam final ModificationNotification event,
			@WebParam(header=true, 
					name=ModificationNotification.LOCALNAME_EVENTSTREAM_ID,
					targetNamespace=Configuration.NAMESPACE) 
			final EventStreamIdSOAPHeader header) throws AggregatorException {
		
		String eventStreamID = header.value;

		logger.info("Event on stream " + eventStreamID + ": " + event);
		
		AggregatorWebSocket browser = getWebSocketByEventStream(eventStreamID);
		if(browser == null) {
			throw new AggregatorException(new RuntimeException("No browser aggregator is handling this event stream."));
		}
		browser.forwardEvent(eventStreamID, event);
	}

	/**
	 * This method is called when a new query evaluation result arrives 
	 * asynchronously from one of the browser aggregator nodes. 
	 */
	@WebMethod
	public void onResult(@WebParam(name="topologyID") String topologyID,
			@WebParam(name="inputUID") String inputUID,
			@WebParam(name="newResult") Object newResult) throws AggregatorException {
		try {
			Element result = (Element)newResult;
			eventCoordinator.addResultFromInputAndNotifyClient(topologyID, inputUID, result);
		} catch (Exception e) {
			logger.warn("Unable to store remotely received result into local event store.", e);
			throw new RuntimeException("Unable to store remotely received result into local event store.", e);
		}
	}
	
	
	private AggregatorWebSocket getRandomWebSocket() {
		return util.coll.getRandom(browserSockets);
	}
	
	private AggregatorWebSocket getWebSocketByEventStream(String eventStreamID) {
		for(AggregatorWebSocket ws : getBrowserSockets()) {
			if(ws.isForwardingToEventStream(eventStreamID)) {
				return ws;
			}
		}
		return null;
	}
	
	
	public int getPort() {
		return port;
	}

	
	public static List<BrowserAggregatorNode> getbrowserAggregatorNodes() {
		return browserAggregatorNodes;
	}
	
	public static int getbrowserAggregatorNodeCount() {
		return browserAggregatorNodes.size();
	}
	
	
	public static Set<AggregatorWebSocket> getBrowserSockets() {
		return browserSockets;
	}
	
	public static int getNumberOfConnectedBrowserSockets() {
		return browserSockets.size();
	}

	


}
