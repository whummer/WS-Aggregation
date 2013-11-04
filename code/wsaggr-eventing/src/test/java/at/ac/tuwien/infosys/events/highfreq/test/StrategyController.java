package at.ac.tuwien.infosys.events.highfreq.test;

import java.net.MalformedURLException;
import java.net.URL;

import at.ac.tuwien.infosys.aggr.node.IBurstCapableAggregatorNode;
import at.ac.tuwien.infosys.bursthandling.strategy.EventBurstHandling;
import at.ac.tuwien.infosys.bursthandling.strategy.EventBurstHandling.EventBurstHandlingException;
import at.ac.tuwien.infosys.events.request.EscalationRequest;
import at.ac.tuwien.infosys.events.request.StopEscalationRequest;
import at.ac.tuwien.infosys.ws.DynamicWSClient;

public class StrategyController {
	private EventBurstHandling strategy;
	private String eventStreamID;
	private IBurstCapableAggregatorNode aggClient = null;

	public void startEscalation() {
			try {
				aggClient = DynamicWSClient.createClientJaxws(
						IBurstCapableAggregatorNode.class, new URL(
								"http://localhost:9701/aggregator?wsdl"));

				aggClient.handleOverloadedQuery(new EscalationRequest(this
						.getEventStreamID(), this.getStrategy()));

				
			} catch (MalformedURLException e) {
				System.out.println("StrategyController - start - MalformedURLException: "
						+ e.getMessage());
				e.printStackTrace();
			} catch (EventBurstHandlingException e) {
				System.out.println("StrategyController - start - EventBurstHandlingException: "
						+ e.getMessage());
				e.printStackTrace();
			}
	}
	
	public void stopEscalation() {
		if (aggClient != null)
		aggClient.stopEscalation(new StopEscalationRequest(this
				.getEventStreamID()));
	}

	public StrategyController(String eventStreamID, EventBurstHandling strategy) {
		this.eventStreamID = eventStreamID;
		this.strategy = strategy;
	}

	public String getEventStreamID() {
		return eventStreamID;
	}

	public EventBurstHandling getStrategy() {
		return strategy;
	}

}
