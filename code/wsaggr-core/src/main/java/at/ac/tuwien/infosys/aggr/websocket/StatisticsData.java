package at.ac.tuwien.infosys.aggr.websocket;

import io.hummer.util.Util;

import org.w3c.dom.Element;

public interface StatisticsData {
	
	public static final Util util = new Util();
	
	public Element toXML();
	
	public double getSpeed();
	public void setSpeed(double speed);
}
