package at.ac.tuwien.infosys.aggr.websocket;

import org.w3c.dom.Element;

public class StatisticsEvent implements StatisticsData {

	private int length;
	private double speed;
	private String sourceLocation;
	private String eventStreamID;
	
	
	public StatisticsEvent(String eventStreamID, int length, double speed, String sourceLocation) {
		this.eventStreamID = eventStreamID;
		this.length = length;
		this.speed = speed;
		this.sourceLocation = sourceLocation;
	}

	public String getEventStreamID() {
		return eventStreamID;
	}

	public void setEventStreamID(String eventStreamID) {
		this.eventStreamID = eventStreamID;
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

	@Override
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
			req = util.xml.createElement("event");
		} catch (Exception e) {
			return null;
		}
		req.setAttribute("length", String.valueOf(getLength()));
		req.setAttribute("eventStreamID", String.valueOf(getEventStreamID()));
		req.setAttribute("source", String.valueOf(getSourceLocation()));
		return req;
	}
}
