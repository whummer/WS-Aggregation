package at.ac.tuwien.infosys.aggr.websocket;

import org.w3c.dom.Element;

public class StatisticsRequest implements StatisticsData {

	// LOW: it actually passes the messageid (change)
	private String requestID;
	private double speed;
	private int queryCount;
	private int inputLength;
	private String sourceLocation;
	private String result;
	
	

	public StatisticsRequest(String id, int inputLength, double speed, int queryCount, String sourceLocation, String result) {
		this.requestID = id;
		this.speed = speed;
		this.queryCount = queryCount;
		this.inputLength = inputLength;
		this.sourceLocation = sourceLocation;
		this.result = result;
	}

	public String getRequestID() {
		return requestID;
	}

	public void setRequestID(String requestID) {
		this.requestID = requestID;
	}

	public double getSpeed() {
		return speed;
	}

	@Override
	public void setSpeed(double speed) {
		this.speed = speed;
	}

	public int getQueryCount() {
		return queryCount;
	}

	public void setQueryCount(int queryCount) {
		this.queryCount = queryCount;
	}

	public int getInputLength() {
		return inputLength;
	}

	public void setInputLength(int inputLength) {
		this.inputLength = inputLength;
	}

	public String getSourceLocation() {
		return sourceLocation;
	}

	public void setSourceLocation(String sourceLocation) {
		this.sourceLocation = sourceLocation;
	}
	
	
	public String getResult() {
		return result;
	}

	public void setResult(String result) {
		this.result = result;
	}

	
	public Element toXML() {
		Element req;
		try {
			req = util.xml.createElement("request");
		} catch (Exception e) {
			return null;
		}
		
		req.setAttribute("id", requestID);
		req.setAttribute("inputlength", String.valueOf(getInputLength()));
		req.setAttribute("speed", String.valueOf(getSpeed()));
		req.setAttribute("querycount", String.valueOf(getQueryCount()));
		req.setAttribute("source", String.valueOf(getSourceLocation()));
		// LOW is this useful?
		req.setAttribute("result", String.valueOf(result.length())); 
		return req;
	}
}
