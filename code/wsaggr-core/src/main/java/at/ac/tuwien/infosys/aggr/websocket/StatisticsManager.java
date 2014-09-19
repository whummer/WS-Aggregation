package at.ac.tuwien.infosys.aggr.websocket;

import io.hummer.util.Util;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import org.w3c.dom.Element;

import at.ac.tuwien.infosys.aggr.node.BrowserAggregatorNode;

public class StatisticsManager {
	
	private Util util = new Util();


	/**
	 * processed events per second
	 * saved as 1 result / stream
	 */
	private double minSpeed = -1;
	private double maxSpeed = -1;
	private double avgSpeed = -1;
	
	/**
	 * avarage request processing speed
	 */
	private double reqSpeed = -1;

	/**
	 * saves data about past events, request, results
	 */
	private TreeMap<String, StatisticsData> events = new TreeMap<String, StatisticsData>();
	private Map<String, StatisticsData> requests = new HashMap<String, StatisticsData>();
	private Map<String, StatisticsData> results = new HashMap<String, StatisticsData>();
	
	
	/*
	public void addRequest(String id, int qCount, int inputLength, String location) {
		requests.put(id, new StatisticsRequest(id, inputLength, 0, qCount, location, null));
	}
	*/
	
	public void addRequest(String id, int qCount, int inputLength, String location, String result, double speed) {
		requests.put(id, new StatisticsRequest(id, inputLength, speed, qCount, location, result));
	}
	
	/*
	public void addRequestResult(String id, String result, double speed) {
		requests.get(id).setSpeed(speed);
		((StatisticsRequest) requests.get(id)).setResult(result);
	}
	*/
	
	public void addEvent(String eventStreamID, int length, String location) {
		events.put(eventStreamID, new StatisticsEvent(eventStreamID, length, 0, location)); 
	}
	
	public void addResult(String eventStreamID, int length, int eventCount, double speed, String location) {
		results.put(eventStreamID, new StatisticsResult(eventStreamID, length, eventCount, speed, location));
	}
	
	
	public String getRecentEventsXMLString() {
		if(events.size() > 20) {
			//events.tailMap();
		}
		return createComplexXMLAnswer("events", listToXML(events));
	}
	
	public String getRecentRequestsXMLString() {
		return createComplexXMLAnswer("requests", listToXML(requests));
	}
	
	public String getRecentResultsXMLString() {
		return createComplexXMLAnswer("results", listToXML(results));
	}
	
	
	public String getActiveUsersXMLString() {
		return createSimpleXMLAnswer("users", String.valueOf(BrowserAggregatorNode.getNumberOfConnectedBrowserSockets()));
	}
	
	public String getActiveBrowserAggregatorsXMLString() {
		return createSimpleXMLAnswer("banodes", String.valueOf(BrowserAggregatorNode.getbrowserAggregatorNodeCount()));
	}
	
	
	public String getMinSpeedXMLString() {
		computeMinSpeed();
		return createSimpleXMLAnswer("minspeed", String.valueOf(minSpeed));
	}
	
	public String getMaxSpeedXMLString() {
		computeMaxSpeed();
		return createSimpleXMLAnswer("maxspeed", String.valueOf(maxSpeed));
	}
	
	public String getAvgSpeedXMLString() {
		computeAvgSpeed();
		return createSimpleXMLAnswer("avgspeed", String.valueOf(avgSpeed));
	}
	
	public String getReqSpeedXMLString() {
		computeRequestSpeed();
		return createSimpleXMLAnswer("reqspeed", String.valueOf(reqSpeed));
	}
	
	
	private void computeMinSpeed() {
		if(minSpeed == -1) {
			if(results.isEmpty()) {
				return;
			}
			minSpeed = Integer.MAX_VALUE;
		}
		for(StatisticsData r : results.values()) {
			if(r.getSpeed() < minSpeed) {
				minSpeed = r.getSpeed();
			}
		}
	}
	
	private void computeMaxSpeed() {
		for(StatisticsData r : results.values()) {
			if(r.getSpeed() > maxSpeed) {
				maxSpeed = r.getSpeed();
			}
		}
	}
	
	
	private void computeAvgSpeed() {
		if(results.isEmpty()) {
			return;
		}
		int sum = 0;
		for(StatisticsData r : results.values()) {
			sum += r.getSpeed();
		}
		avgSpeed = sum / results.size();
	}
	
	private void computeRequestSpeed() {
		if(requests.isEmpty()) {
			return;
		}
		int sum = 0;
		for(StatisticsData r : requests.values()) {
			sum += r.getSpeed();
		}
		reqSpeed = sum / requests.size();
	}
	
	
	/**
	 * creates an xml element and returns its string value
	 * <message type ="statistics" category="category" content="content"></statistics>
	 */
	private String createSimpleXMLAnswer(String category, String content) {
		Element answer;
		try {
			answer = util.xml.createElement("message");
		} catch (Exception e) {
			return null;
		}
		answer.setAttribute("type", "statistics");
		answer.setAttribute("category", category);
		answer.setAttribute("content", content);
		return util.xml.toString(answer);
	}


	/**
	 * creates an xml element and returns its string value
	 * contains a list of either requests, events or results
	 * <message type="statistics" category="category">
	 * 		<list>
	 * 			<req>bla</req>
	 * 		</list>
	 * </message>
	 */
	private String createComplexXMLAnswer(String category, Element list) {
		Element answer;
		try {
			answer = util.xml.createElement("message");
			util.xml.appendChild(answer, list);
		} catch (Exception e) {
			return null;
		}
		answer.setAttribute("type", "statistics");
		answer.setAttribute("category", category);
		return util.xml.toString(answer);
	}
	
	
	private Element listToXML(Map<String, StatisticsData> list) {
		Element reqs;
		try {
			reqs = util.xml.createElement("list");
			for (StatisticsData r : list.values()) {
				util.xml.appendChild(reqs, r.toXML());
			}
		} catch (Exception e) {
			return null;
		}
		return reqs;
	}

}
