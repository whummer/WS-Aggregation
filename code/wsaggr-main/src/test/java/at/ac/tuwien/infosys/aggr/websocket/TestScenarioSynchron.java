package at.ac.tuwien.infosys.aggr.websocket;

import java.util.UUID;

import org.w3c.dom.Element;

import at.ac.tuwien.infosys.aggr.AggregationClient;
import at.ac.tuwien.infosys.aggr.request.AggregationRequest;
import at.ac.tuwien.infosys.aggr.request.RequestInput;
import at.ac.tuwien.infosys.aggr.util.ServiceStarter;

public class TestScenarioSynchron extends TestScenario {
	
	public static void main(String[] args) throws Exception {
		setup();
		//runManyRequests();
		runSynchronousRequest();
		System.exit(0);
	}

	
	public static void runSynchronousRequest() throws Exception {

		AggregationClient client = new AggregationClient(
				ServiceStarter.getDefaultGatewayEPR(), "wsaggr", util.str.md5("wsaggr!"));

		String requestID = UUID.randomUUID().toString();
		AggregationRequest request = new AggregationRequest();
		request.setRequestID(requestID);

		RequestInput input = new RequestInput();
		String inputID = "1";
		input.setExternalID(inputID);
		input.setServiceURL("http://www.yahoo.com/");
		//input.setCache(false);
		request.getInputs().addInput(input);

		request.getQueries().addPreparationQuery(inputID, 
				"<r>{for $news in //a[@class='bullet y-fp-pg-controls y-link-1 medium'] " +
				"return <headline>{$news}</headline>}</r>");
		/*
		request.getQueries().addPreparationQuery(inputID, 
				"<r>{for $news in //headline " +
				"return <headline>{$news}</headline>}</r>");
		*/

		// wait until a browser is connected
		checkForSockets();
		
		System.out.println("Sending aggregation request to gateway..");
		Element e = client.aggregate(request);
		System.out.println("Result computed.");
		util.xml.print(e);

	}
	
	
	public static void runManyRequests() throws Exception {

		AggregationClient client = new AggregationClient(
				ServiceStarter.getDefaultGatewayEPR(), "wsaggr", util.str.md5("wsaggr!"));

		String requestID = UUID.randomUUID().toString();
		AggregationRequest request = new AggregationRequest();
		request.setRequestID(requestID);
		String requestID2 = UUID.randomUUID().toString();
		AggregationRequest request2 = new AggregationRequest();
		request.setRequestID(requestID2);

		RequestInput input = new RequestInput();
		String inputID = "1";
		input.setExternalID(inputID);
		input.setServiceURL("http://www.yahoo.com/");

		request.getInputs().addInput(input);
		request.getQueries().addPreparationQuery(inputID, 
				"<r>{for $news in //a[@class='bullet y-fp-pg-controls y-link-1 medium'] " +
				"return <headline>{$news}</headline>}</r>");

		request2.getInputs().addInput(input);
		request2.getQueries().addPreparationQuery(inputID, 
				"<r>{for $news in //a[@class='bullet y-fp-pg-controls y-link-1 medium'] " +
				"return <headline>{$news}</headline>}</r>");

		checkForSockets();
		System.out.println("Sending aggregation request to gateway..");
		Element e = client.aggregate(request);
		System.out.println("Result computed.");
		util.xml.print(e);
		
		Thread.sleep(3*1000);
		
		Element e2 = client.aggregate(request2);
		System.out.println("Result computed.");
		util.xml.print(e2);
		
		Thread.sleep(100*1000);
	}
}
