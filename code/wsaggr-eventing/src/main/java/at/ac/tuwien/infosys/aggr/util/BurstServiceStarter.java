package at.ac.tuwien.infosys.aggr.util;

import java.util.logging.Level;
import java.util.logging.Logger;

import at.ac.tuwien.infosys.aggr.node.AggregatorNode;
import at.ac.tuwien.infosys.aggr.node.BurstCapableAggregatorNode;
import at.ac.tuwien.infosys.util.Configuration;
import at.ac.tuwien.infosys.util.Util;
import at.ac.tuwien.infosys.util.perf.MemoryAgent;
import at.ac.tuwien.infosys.ws.EndpointReference;

public class BurstServiceStarter {

	private static final org.apache.log4j.Logger logger = Util.getLogger(BurstServiceStarter.class);
	
	public static void startAggregators(int aggregators) throws Exception {
		Logger.getAnonymousLogger().getParent().setLevel(Level.OFF);
		for(int i = 1; i <= aggregators; i++) {
			final int id = i;
			new Thread() {
				public void run() {
					try {
						startAggregator(id);
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}.start();
			Thread.sleep(200);
		}
		System.out.println(aggregators + " burst capable aggregator instances started.");
	}
	
	public static void startAggregator(int ID) throws Exception {
		String id = ID < 10 ? "0" + ID : "" + ID;
		int port = Integer.parseInt("97" + id);
		String host = Configuration.getHost(Configuration.PROP_HOST);
		String bindhost = Configuration.getHost(Configuration.PROP_BINDHOST,Configuration.PROP_HOST);
		startAggregator(host, bindhost, port);
	}
	
	public static void startAggregator(String host, String bindhost, int port) throws Exception {
		String address = "http://" + host + ":" + port + "/aggregator";
		EndpointReference epr = new EndpointReference(
				"<wsa:EndpointReference xmlns:wsa=\"http://schemas.xmlsoap.org/ws/2004/08/addressing\" " +
				"xmlns:tns=\"" + Configuration.NAMESPACE + "\">" +
					"<wsa:Address>" + address + "</wsa:Address>" +
					"<wsa:ServiceName PortName=\"AggregatorNodePort\">" +
						"tns:AggregatorNodeService" +
					"</wsa:ServiceName>" +
				"</wsa:EndpointReference>");
		AggregatorNode a = new BurstCapableAggregatorNode(epr, true);
		
		String bindaddress = "http://" + bindhost + ":" + port + "/aggregator";
		a.deploy(bindaddress);

		String wsdlURL = address.endsWith("?wsdl") ? address : (address + "?wsdl");
		long size = MemoryAgent.getSafeDeepSizeOf(a);
		String heap = size <= 0 ? "" : " (Used heap memory: " + size + ")";
		
		logger.info("BurstCapableAggregatorNode started: " + epr.getAddress());
		
		System.out.println("Started burst capable aggregator, SOAP/WSDL: " + wsdlURL + heap);
	}
}
