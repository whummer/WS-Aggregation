package at.ac.tuwien.infosys.aggr.websocket;

import org.w3c.dom.Element;

import at.ac.tuwien.infosys.util.Util;

public interface StatisticsData {
	
	public static final Util util = new Util();
	
	public Element toXML();
	
	public double getSpeed();
	public void setSpeed(double speed);
}
