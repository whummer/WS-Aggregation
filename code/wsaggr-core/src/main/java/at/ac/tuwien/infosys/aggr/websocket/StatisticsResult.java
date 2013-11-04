package at.ac.tuwien.infosys.aggr.websocket;

import org.w3c.dom.Element;


/**
 * this class only represents results of event streams
 * 
 * @author Alex
 *
 */
public class StatisticsResult implements StatisticsData {

	// can add topology id and other stuff
	
	private String eventStreamID;
	private int length;
	private int eventCount;
	private double speed;
	private String sourceLocation;

	
	
	public StatisticsResult(String eventStreamID, int length, int eventCount, double speed, String sourceLocation) {
		this.eventStreamID = eventStreamID;
		this.length = length;
		this.eventCount = eventCount;
		this.speed = speed;
		this.sourceLocation = sourceLocation;
	}

	public String getEventStreamID() {
		return eventStreamID;
	}

	public void setEventStreamID(String eventStreamID) {
		this.eventStreamID = eventStreamID;
	}

	public int getEventCount() {
		return eventCount;
	}

	public void setEventCount(int eventCount) {
		this.eventCount = eventCount;
	}
	
	public void addEventCount() {
		this.eventCount++;
	}

	public int getLength() {
		return length;
	}

	public void setLength(int length) {
		this.length = length;
	}

	public double getSpeed() {
		return speed;
	}

	public void setSpeed(double speed) {
		this.speed = speed;
	}

	public String getSourceLocation() {
		return sourceLocation;
	}

	public void setSourceLocation(String sourceLocation) {
		this.sourceLocation = sourceLocation;
	}


	public Element toXML() {
		Element req;
		try {
			req = util.xml.createElement("result");
		} catch (Exception e) {
			return null;
		}
		
		req.setAttribute("eventStreamID", String.valueOf(getEventStreamID()));
		req.setAttribute("eventCount", String.valueOf(getEventCount()));
		req.setAttribute("length", String.valueOf(getLength()));
		req.setAttribute("speed", String.valueOf(getSpeed()));
		req.setAttribute("source", String.valueOf(getSourceLocation()));
		return req;
	}
}
