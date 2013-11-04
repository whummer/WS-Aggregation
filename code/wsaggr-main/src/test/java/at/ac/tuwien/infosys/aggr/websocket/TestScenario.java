package at.ac.tuwien.infosys.aggr.websocket;

import at.ac.tuwien.infosys.aggr.node.BrowserAggregatorNode;
import at.ac.tuwien.infosys.aggr.util.ServiceStarter;
import at.ac.tuwien.infosys.util.Util;

public abstract class TestScenario {

	protected static Util util = new Util();
	
	public static void setup() throws Exception {

		ServiceStarter.startHSQLDBServer();
		ServiceStarter.startRegistry();
		ServiceStarter.startGateway();
		ServiceStarter.startBrowserAggregator(1);

	}
	
	public static void checkForSockets() throws InterruptedException {
		boolean noclients = true;
		boolean runs = false;
		
		while(noclients) {
			if(BrowserAggregatorNode.getNumberOfConnectedBrowserSockets() < 1) {
				if(!runs) {
					System.out.println("Please start a browser!");
					runs = true;
				}
				Thread.sleep(3*1000);
				continue;
			}
			noclients = false;
		}
	}

}
