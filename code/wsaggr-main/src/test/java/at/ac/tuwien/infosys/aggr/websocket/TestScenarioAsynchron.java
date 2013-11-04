package at.ac.tuwien.infosys.aggr.websocket;

import java.util.List;
import java.util.UUID;

import org.w3c.dom.Element;

import at.ac.tuwien.infosys.aggr.AggregationClient;
import at.ac.tuwien.infosys.aggr.node.DataServiceNode;
import at.ac.tuwien.infosys.aggr.node.Registry;
import at.ac.tuwien.infosys.aggr.request.AggregationRequest;
import at.ac.tuwien.infosys.aggr.request.EventingInput;
import at.ac.tuwien.infosys.aggr.test.EventServiceStockTrade;
import at.ac.tuwien.infosys.aggr.util.ServiceStarter;

public class TestScenarioAsynchron extends TestScenario {

	public static void main(String[] args) throws Exception {
		setup();
		runAsynchronousRequest();
		System.exit(0);
	}
	
	
	public static void runAsynchronousRequest() throws Exception {

		EventServiceStockTrade.start(2, 500);
		List<DataServiceNode> services = Registry.getRegistryProxy().getDataServiceNodes("Eventing");

		AggregationClient client = new AggregationClient(
				ServiceStarter.getDefaultGatewayEPR(), "wsaggr", util.str.md5("wsaggr!"));

		String requestID = UUID.randomUUID().toString();
		AggregationRequest request = new AggregationRequest();
		request.setRequestID(requestID);

		EventingInput input = new EventingInput();
		String inputID = "1";
		input.setExternalID(inputID);
		input.setServiceURL(services.get(0).getEPR().getAddress());
		request.getInputs().addInput(input);
		request.getQueries().addPreparationQuery(inputID, 
				"<r>{for sliding window $w in $input " +
				"start when true() " +
				"end when true() " +
				"return <bid>{$w}</bid>}</r>");

		// wait for browser sockets
		checkForSockets();
		
		System.out.println("Sending aggregation request to gateway..");
		Element e = client.aggregate(request);
		util.xml.print(e);

	}


}
