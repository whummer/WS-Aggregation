package at.ac.tuwien.infosys.aggr.websocket;

import java.util.Date;
import java.util.List;

import at.ac.tuwien.infosys.aggr.request.WAQLQuery.PreparationQuery;

public class EventStreamData {
	
	private String topologyID;
	private String inputUID;
	private String eventStreamID;
	private List<PreparationQuery> queries;
	private int eventCount = 0;
	
	private long startTime;
	private long endTime;
	
	public EventStreamData(String topologyID, String inputUID, String eventStreamID, List<PreparationQuery> queries) {
		super();
		this.topologyID = topologyID;
		this.inputUID = inputUID;
		this.eventStreamID = eventStreamID;
		this.queries = queries;
		startTime = new Date().getTime();
		endTime = 0;
	}

	public String getTopologyID() {
		return topologyID;
	}

	public String getInputUID() {
		return inputUID;
	}

	public String getEventStreamID() {
		return eventStreamID;
	}

	public List<PreparationQuery> getQueries() {
		return queries;
	}

	public int getEventCount() {
		return eventCount;
	}

	public void addToEventCount() {
		this.eventCount++;
	}
	
	public void setEndTime() {
		endTime = new Date().getTime();
	}

	public double getSpeed() {
		return (endTime - startTime) / 1000;
	}
	
}
