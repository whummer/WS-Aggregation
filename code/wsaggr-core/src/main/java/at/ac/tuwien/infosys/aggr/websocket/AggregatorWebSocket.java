package at.ac.tuwien.infosys.aggr.websocket;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.eclipse.jetty.websocket.WebSocket;
import org.w3c.dom.DOMException;
import org.w3c.dom.Element;

import at.ac.tuwien.infosys.aggr.monitor.ModificationNotification;
import at.ac.tuwien.infosys.aggr.node.AggregatorNode.AggregatorException;
import at.ac.tuwien.infosys.aggr.node.BrowserAggregatorNode;
import at.ac.tuwien.infosys.aggr.request.WAQLQuery.PreparationQuery;
import at.ac.tuwien.infosys.util.Util;
import at.ac.tuwien.infosys.util.coll.BlockingMap;

public class AggregatorWebSocket implements WebSocket.OnTextMessage {

	// ALEX find out why you cant connect same browser window twice
	
	// LOW: write comments
	// LOW: create new project
	protected static final Logger logger = at.ac.tuwien.infosys.util.Util.getLogger(AggregatorWebSocket.class);
	
	private Connection connection;
	private BrowserAggregatorNode banode;
	private StatisticsManager st;
	private Util util;
	
	// ALEX: this user isn't notified when OTHER users join or leave 
	private Set<AggregatorWebSocket> browserSockets; 
	private BlockingMap<String,Element> blockingRequestMap = null;
	private Map<String, EventStreamData> eventStreams;
	//private ArrayList<UUID> activeRequests;
	
	private UUID userID;
	

	public AggregatorWebSocket(BrowserAggregatorNode banode, UUID uuid) {
		this.browserSockets = BrowserAggregatorNode.getBrowserSockets();
		this.userID = uuid;
		this.banode = banode;
		//activeRequests = new ArrayList<UUID>();
		eventStreams = new HashMap<String, EventStreamData>();
		util = new Util();
		st = new StatisticsManager();
		logger.setLevel(Level.DEBUG);
	}

	public void onOpen(Connection con) {
		this.connection = con;
		
		send(loginMessage());
		browserSockets.add(this);
		send(st.getActiveUsersXMLString());
		send(st.getActiveBrowserAggregatorsXMLString());
		
		logger.info("New client protocol: " + connection.getProtocol() + ". Clients: " + browserSockets.size());
	}
	
	/**
	 * <message type="login" clientId="clientid"></message>
	 */
	private String loginMessage() {
		Element login;
		try {
			login = util.xml.createElement("message");
		} catch (Exception e) {
			logger.warn("XML error. " + e.getMessage());
			return null;
		}
		login.setAttribute("type", "login");
		login.setAttribute("clientID", String.valueOf(userID));
		return util.xml.toString(login);
	}

	/**
	 * <message type="logout" clientId="clientid"></message>
	 */
	private void logout() {
		if(browserSockets.remove(this)) {
			logger.info("Clients: " + browserSockets.size());
		}
		send(st.getActiveUsersXMLString());
	}
	
	
	public void onClose(int closeCode, String message) {
		logout();
	}

	public void onMessage(String data) {
		if (!isOnline()) {
			return;
		}

		if (data.trim().startsWith("<")) {
			logger.info(data);
			Element el;
			try {
				el = util.xml.toElement(data);
			} catch (Exception e) {
				logger.warn("XML error. " + e.getMessage());
				return;
			}

			String type = el.getAttribute("type");
			
			/* logout request */
			if(type.equals("logout")) {
				logout();
				return;
			}
			
			/* result for event stream */
			if(type.equals("eventresult")) {
				processEventResult(el);
				return;
			}

			/* result for single requests */
			if (type.equals("result")) {
				processSingleRequestResult(el);
				return;
			}

			/* statistical data requests */
			if (type.equals("statusupdate")) {
				send(st.getActiveUsersXMLString());
				send(st.getActiveBrowserAggregatorsXMLString());
			}
			if (type.equals("requestsupdate")) {
				send(st.getRecentRequestsXMLString());
			}
			if (type.equals("eventsupdate")) {
				send(st.getRecentEventsXMLString());
			}
			if (type.equals("resultsupdate")) {
				send(st.getRecentResultsXMLString());
			}
			if (type.equals("performanceupdate")) {
				// LOW combine to 1 message?
				send(st.getReqSpeedXMLString());
				send(st.getAvgSpeedXMLString());
				send(st.getMinSpeedXMLString());
				send(st.getMaxSpeedXMLString());
			}
			return;
		}

		send("Error: Wrong message type.");
	}
	

	
	/**
	 * <message type="result" clientID="" requestID="" messageID="">
	 * 		<result>foo</result> 
	 * </message>
	 */
	private void processSingleRequestResult(Element el) {
		if (blockingRequestMap == null) {
			logger.error("blocking Map null");
			return;
		}

		Element result = util.xml.getChildElements(el, "r").get(0);
		// String result = util.xml.getChildElements(el, "r").get(0).getTextContent();
		String messageID = el.getAttribute("messageID");

		// LOW call with what result
		blockingRequestMap.put(messageID, result);
	}


	
	/**
	 * <message type="eventresult" clientID="" eventStreamID="">
	 * 		<result>foo</result> 
	 * </message>
	 */
	private void processEventResult(Element el) {
		String eventStreamID = el.getAttribute("eventStreamID"); 
		Element result = util.xml.getChildElements(el, "r").get(0);
		
		if(banode == null) {
			logger.error("BrowserAggregatorNode was never set!");
			return;
		}
		
		EventStreamData eventStream = eventStreams.get(eventStreamID);
		if(eventStream == null) {
			logger.warn("A result for an unknown stream arrived.");
			return;
		}
		
		eventStream.setEndTime();
		st.addResult(eventStreamID, util.xml.toString(result).length(), eventStream.getEventCount(), eventStream.getSpeed(), BrowserAggregatorNode.location);
		
		try {
			// LOW call with what result
			banode.onResult(eventStream.getTopologyID(), eventStream.getInputUID(), result);
		} catch (AggregatorException e) {
			logger.error("Error sending result back. " + e.getMessage());
		}
	}

	
	/**
	 * 	<message type="request" requestID="someid" clientID="clientID" messageID="messageID">
	 * 		<queries>
	 * 			<r>query</r> 
	 * 		</queries>
	 * 		<input>...data...</input> 
	 * 	</message>
	 */
	public String applyPrepQueries(String rid, Element element,List<PreparationQuery> queries, BlockingMap<String,Element> blockingRequestMap) 
			throws DOMException, Exception {
		if (!isOnline()) {
			return null;
		}
		
		this.blockingRequestMap = blockingRequestMap;
		
		UUID currentRequestID = UUID.fromString(rid); 
		UUID currentMessageID = UUID.randomUUID();
		//activeRequests.add(currentRequestID);
		
		Element xmlRequestElement = util.xml.createElement("message");//doc.createElement("message");
		Element queryList = util.xml.createElement("queries");
		Element input = util.xml.createElement("input");
		
		xmlRequestElement.setAttribute("type", "request");
		xmlRequestElement.setAttribute("requestID", String.valueOf(currentRequestID));
		xmlRequestElement.setAttribute("messageID", String.valueOf(currentMessageID));
		xmlRequestElement.setAttribute("clientID", String.valueOf(userID));
		
		util.xml.appendChild(input, element); 

		for(PreparationQuery q : queries) {
			util.xml.appendChild(queryList, util.xml.toElement(q.getValue()));
		}
		
		util.xml.appendChild(xmlRequestElement, queryList); 
		util.xml.appendChild(xmlRequestElement, input); 

		send(util.xml.toString(xmlRequestElement));		
		return String.valueOf(currentMessageID);
	}
	

	
	/**
	 * <message type="queries" streamID="eventstreamid">
	 * 		<queries>
	 * 			<r>query</r> 
	 * 		</queries>
	 * </message>
	 */
	public void saveEventStreamDataForAsynchronousEvents(EventStreamData eventStream) {
		eventStreams.put(eventStream.getEventStreamID(), eventStream);
		
		Element qEl;
		try {
			qEl = util.xml.createElement("message");
			qEl.setAttribute("type", "queries");
			qEl.setAttribute("streamID", eventStream.getEventStreamID());
			qEl.setAttribute("clientID", String.valueOf(userID));
			
			Element queryList = util.xml.createElement("queries");
			for(PreparationQuery q : eventStream.getQueries()) {
				util.xml.appendChild(queryList, util.xml.toElement(q.getValue()));
			}
			util.xml.appendChild(qEl, queryList); 
			
		} catch (Exception e) {
			logger.warn("XML error. " + e.getMessage());
			return;
		} 

		send(util.xml.toString(qEl));	
	}
	
	/**
	 * <message type="event" streamID="eventstreamid">
	 * 		<input>event</input>
	 * </message>
	 */
	public void forwardEvent(String eventStreamID, ModificationNotification event) {
		
		if(!isForwardingToEventStream(eventStreamID)) {
			throw new RuntimeException("Event arrived before a query list was defined for this event stream.");
		}
		
		Element ev;
		try {
			ev = event.getDataAsElement();
		} catch (Exception e) {
			logger.warn("Error getting event. " + e.getMessage());
			return;
		}
		logger.debug("Event: " + util.xml.toString(ev));
		
		Element evEl;
		try {
			evEl = util.xml.createElement("message");
			evEl.setAttribute("type", "event");
			evEl.setAttribute("streamID", eventStreamID);
			evEl.setAttribute("clientID", String.valueOf(userID));
			
			Element input = util.xml.createElement("input");
			util.xml.appendChild(input, ev); 
			util.xml.appendChild(evEl, input);
			
		}  catch (Exception e) {
			logger.warn("XML error. " + e.getMessage());
			return;
		} 

		send(util.xml.toString(evEl));	
		logger.debug("forwarded event to browser: " + util.xml.toString(evEl));
		eventStreams.get(eventStreamID).addToEventCount();
		st.addEvent(eventStreamID, util.xml.toString(ev).length(), BrowserAggregatorNode.location);
	}
	
	public boolean isForwardingToEventStream(String eventStreamID) {
		if(eventStreams.containsKey(eventStreamID)) {
			return true;
		}
		else {
			return false;
		}
	}
	
	private boolean isOnline() {
		if (!browserSockets.contains(this)) {
			logger.error("The client isn't logged on, this message shouldn't appear!");
			return false;
		}
		return true;
	}

	private void send(String msg) {
		try {
			connection.sendMessage(msg);
		} catch (IOException e) {
			logger.warn("Error while sending message to browser: " + e.getMessage());
		}
	}

	
	public void setBANode(BrowserAggregatorNode banode) {
		this.banode = banode;
	}

	public void addRequestStatistics(String messageID, int qCount, int length,
			String location, String result, double speed) {
		st.addRequest(messageID, qCount, length, location, result, speed);
	}

	
}
