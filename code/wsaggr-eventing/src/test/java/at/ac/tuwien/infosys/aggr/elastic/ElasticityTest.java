package at.ac.tuwien.infosys.aggr.elastic;

import java.net.URL;
import java.util.LinkedList;
import java.util.List;

import javax.xml.namespace.QName;

import org.apache.log4j.Logger;
import org.junit.Ignore;
import org.w3c.dom.Element;

import at.ac.tuwien.infosys.aggr.AggregationClient;
import at.ac.tuwien.infosys.aggr.events.query.EventingQueryCoordinator;
import at.ac.tuwien.infosys.aggr.monitor.MonitoringSpecification;
import at.ac.tuwien.infosys.aggr.node.AggregatorNode;
import at.ac.tuwien.infosys.aggr.node.DataServiceNode;
import at.ac.tuwien.infosys.aggr.node.Gateway;
import at.ac.tuwien.infosys.aggr.node.Registry;
import at.ac.tuwien.infosys.aggr.request.AggregationRequest;
import at.ac.tuwien.infosys.aggr.request.EventingInput;
import at.ac.tuwien.infosys.aggr.util.ServiceStarter;
import at.ac.tuwien.infosys.events.EventReceiverListener;
import at.ac.tuwien.infosys.events.EventReceiverService;
import at.ac.tuwien.infosys.events.test.EventServiceStockTrade;
import at.ac.tuwien.infosys.util.Configuration;
import at.ac.tuwien.infosys.util.Util;
import at.ac.tuwien.infosys.ws.AbstractNode;
import at.ac.tuwien.infosys.ws.EndpointReference;

@Ignore
public class ElasticityTest implements EventReceiverListener {

	private static final Util util = new Util();
	private static final Logger logger = Util.getLogger();

	private static AggregationClient client;
	private static EventReceiverService receiver;
	private static AggregatorNode aggregator;
	private static Gateway gateway;
	private static EventServiceStockTrade service;
	private static boolean testLocal = true;

	static {
		try {
			String host = Configuration.getHost();
			//host = "web4d.ftp.sh"; // TODO
			String url = "http://" + host + ":50429/client";
			url = "http://" + host + ":40402/client?wsdl";
			logger.info("Starting event receiver service: " + url);
			receiver = new EventReceiverService(false);
			receiver.deploy(url);
			receiver.setEPR(new EndpointReference(new URL(url)));
			if(testLocal) {
				ServiceStarter.startHSQLDBServer();
				service = EventServiceStockTrade.start(1, 1000L).get(0);
				aggregator = ServiceStarter.startAggregator(0);
				gateway = ServiceStarter.startGateway("http://localhost:8891/gateway?wsdl");
				logger.info("Creating client for gateway " + gateway.getEPR().getAddress());
				client = new AggregationClient(gateway.getEPR(),
						"wsaggr", util.str.md5("wsaggr!"));
			} else {
				client = new AggregationClient(
					Registry.getRegistryProxy().getDataServiceNodes(
							Gateway.REGISTRY_FEATURE_GATEWAY).get(0).getEPR(),
					"wsaggr", util.str.md5("wsaggr!"));
			}
			logger.info("Initialization finished.");
		} catch (Exception e) {
			logger.error("", e);
			System.exit(0);
		}
	}

	private static String addRequest() throws Exception {
		EndpointReference serviceNode = null;
		AggregationRequest req = new AggregationRequest();
		if(testLocal) {
			serviceNode = service.getEPR();
			String aggrWSDL = aggregator.getEPR().getAddress();
			if(!aggrWSDL.contains("?wsdl")) {
				aggrWSDL += "?wsdl";
			}
			req.setAssignedAggregator(aggrWSDL);
		} else {
			List<DataServiceNode> nodes = Registry.getRegistryProxy().getDataServiceNodes("Eventing");
			serviceNode = AbstractNode.selectRandomAvailableNode(nodes).getEPR();
		}
		EventingInput in = new EventingInput();
		in.setExternalID("1");
		in.setServiceURL(serviceNode.getAddress());
		req.getInputs().addInput(in);
		req.getQueries().addPreparationQuery("1", 
				"for sliding window $w in $input " +
				"start $s at $sp when true() " +
				"end $e at $ep when ($ep - $sp) ge 10 " +
				"return <avg>{avg($w/bid)}</avg>");
		EndpointReference epr = null;
		if(!testLocal) {
			epr = new EndpointReference(Registry.getRegistryProxy().getGateway().getEPR());
			QName name = EventingQueryCoordinator.HEADER_NAME_FORWARD_EVENT_TO;
			epr.addReferenceParameter(receiver.getEPR().toElement(
					"tns1:" + name.getLocalPart() + " xmlns:tns1=\"" + name.getNamespaceURI() + "\""));
		} else {
			epr = receiver.getEPR();
		}
		//System.out.println("Notify to: " + epr);
		req.setMonitor(new MonitoringSpecification(epr));
		Element e = client.aggregate(req);
		return e.getTextContent();
	}

	public void handleNewEvent(String subscriptionID, Element event) {
		System.out.println("result: " + event);
	}

	public void runTest() throws Exception {
		ElasticityState.getState();
		List<String> requests = new LinkedList<String>();
		int count = 0;
		while(count <= 2) {
			String id = addRequest();
			count++;
			System.out.println("added request " + count + ", id " + id + ": " + ElasticityState.getState());
			requests.add(id);
			Thread.sleep(7000);
		}
		Thread.sleep(20000);
		for(String request : requests) {
			client.destroyTopology(request);
			System.out.println(count + ": " + ElasticityState.getState());
			Thread.sleep(5000);
			count--;
		}

		Thread.sleep(3000);
	}

	public static void main(String[] args) throws Exception {
		try {
			ElasticityTest test = new ElasticityTest();
			receiver.addListener(test);
			test.runTest();
			ServiceStarter.stopHSQLDBServer();
			System.out.println("Done.");
		} catch (Throwable e) {
			e.printStackTrace();
		}
		System.exit(0);
	}

}
