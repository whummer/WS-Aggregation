package at.ac.tuwien.infosys.bursthandling;

import io.hummer.util.Util;
import io.hummer.util.ws.EndpointReference;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import at.ac.tuwien.infosys.aggr.events.query.EventQuerier;
import at.ac.tuwien.infosys.aggr.events.query.EventStoresManager;
import at.ac.tuwien.infosys.aggr.monitor.ModificationNotification;
import at.ac.tuwien.infosys.aggr.monitor.MonitoringSpecification;
import at.ac.tuwien.infosys.aggr.node.AggregatorNode;
import at.ac.tuwien.infosys.aggr.node.AggregatorNode.AggregatorException;
import at.ac.tuwien.infosys.aggr.node.BurstCapableAggregatorNode;
import at.ac.tuwien.infosys.aggr.performance.SortedAggregatorsList;
import at.ac.tuwien.infosys.aggr.request.AggregationRequest;
import at.ac.tuwien.infosys.aggr.request.EventingInput;
import at.ac.tuwien.infosys.aggr.request.NonConstantInput;
import at.ac.tuwien.infosys.bursthandling.strategy.EventBurstHandling;
import at.ac.tuwien.infosys.bursthandling.strategy.EventBurstHandling.EventBurstHandlingException;

public class BurstManager {
	/**
	 * Logger object
	 * */
	private static final org.apache.log4j.Logger logger = Util
			.getLogger(BurstManager.class);
	/**
	 * Maps the different burst strategies to event streams.
	 */
	private Map<String, EventBurstHandling> eventStreamIdToStrategy;
	/**
	 * Node, its burst handling is done.
	 */
	private BurstCapableAggregatorNode managedNode;

	public BurstManager(BurstCapableAggregatorNode managedNode) {
		this.managedNode = managedNode;
		this.eventStreamIdToStrategy = new HashMap<String, EventBurstHandling>();
	}

	/**
	 * Returns the set strategy for the given event stream id.
	 * 
	 * @param eventStreamID
	 *            of requested strategy.
	 * @return the current strategy. (null if there is no strategy applied)
	 */
	public EventBurstHandling getStrategy(String eventStreamID) {
		return this.eventStreamIdToStrategy.get(eventStreamID);
	}

	/**
	 * Set an burst handling strategy for the given event stream.
	 * 
	 * @param eventStreamID
	 *            for that the strategy should be applied.
	 * @param strategy
	 *            to be applied.
	 * @throws EventBurstHandlingException 
	 */
	public void startEscalation(String eventStreamID, EventBurstHandling strategy) throws EventBurstHandlingException {
		EventBurstHandling current = this.eventStreamIdToStrategy.put(
				eventStreamID, strategy);
		strategy.setManager(this);
		strategy.initialize(eventStreamID);
		if (current != null) {
			logger.warn("Current strategy for event stream id " + eventStreamID
					+ " has been overwritten!");
		}
		logger.info("New burst strategy for event stream id " + eventStreamID
				+ ": " + strategy.info());
	}

	/**
	 * Checks whether there is escalation handling for the given event stream
	 * id.
	 * 
	 * @param eventStreamID
	 *            to be checked.
	 * @return true if there is a burst strategy set.
	 */
	private boolean isEscalation(String eventStreamID) {
		return this.eventStreamIdToStrategy.get(eventStreamID) != null;
	}

	public void stopEscalation(String eventStreamID) {
		if (!this.eventStreamIdToStrategy.containsKey(eventStreamID)) {
			logger.warn("Stopping escalation for event stream id "
					+ eventStreamID
					+ " failed. (No strategy has been applied.)");
		} else {
			this.eventStreamIdToStrategy.get(eventStreamID).shutdown();
			this.eventStreamIdToStrategy.remove(eventStreamID);
			logger.info("Stopped escalation for event stream id "
					+ eventStreamID + ".");
		}

	}

	/**
	 * Handles the event, if an burst strategy is applied for the corresponding
	 * event stream.
	 * 
	 * @param event
	 *            to be handled.
	 * @return true if the event needs further processing of the aggregator
	 *         node. (False otherwise.)
	 * @throws AggregatorException
	 */
	public boolean handleEvent(final ModificationNotification event)
			throws AggregatorException {
		if (isEscalation(event.getEventStreamID())) {
			return this.eventStreamIdToStrategy.get(event.getEventStreamID())
					.handleEvent(event);
		} else {
			return true;
		}
	}

	/**
	 * Finishes the event handling by passing the result to onResult.
	 * 
	 * @param topologyID
	 *            of the corresponding result.
	 * @param inputUID
	 *            of the corresponding result.
	 * @param event
	 *            the result.
	 * @throws AggregatorException
	 */
	public void finishEventHandling(String topologyID, String inputUID,
			ModificationNotification event) throws AggregatorException {
		this.managedNode.onResult(topologyID, inputUID, event);
	}

	/**
	 * Returns available aggregator nodes, sorted by their performance.
	 * 
	 * @return list of Aggregator nodes.
	 */
	public List<AggregatorNode> provideAggregators() {
		SortedAggregatorsList aggList = SortedAggregatorsList
				.getInstance(this.managedNode);
		try {
			aggList.getAndSortAggregatorsByPerformance();
		} catch (Exception e) {
			logger.error("Could not sort aggrgators by performance.", e);
		}
		return aggList.getSortedAggregators();
	}

	/**
	 * Returns new aggregation request for all querier of the managed node for
	 * the given event stream mapped by their topology id.
	 * 
	 * @param eventStreamID
	 *            of the relevant event stream
	 * @return map of topology request pairs.
	 * @throws Exception
	 *             if the endpoint reference can not be created
	 */
	public Map<String, AggregationRequest> getAggregationRequest(
			String eventStreamID) throws Exception {
		Map<String, AggregationRequest> requests = new HashMap<String, AggregationRequest>();
		EventStoresManager mngr = this.managedNode.getEventCoordinator()
				.getEventManager();
		List<EventQuerier> queries = mngr.getQueriers(eventStreamID);

		for (EventQuerier eq : queries) {
			AggregationRequest ar = new AggregationRequest();
			NonConstantInput in = new EventingInput();
			in.setServiceURL(this.managedNode.getEPR().getAddress());
			((EventingInput) in).setDontSubscribe(true);
			ar.getInputs().addInput(in);
			ar.getQueries().addPreparationQuery(eq.getOriginalQuery());
			EndpointReference epr = new EndpointReference(managedNode.getEPR());
			MonitoringSpecification listener = new MonitoringSpecification(epr);
			ar.setMonitor(listener);
			eq.getInput().getUniqueID();
			requests.put(mngr.getTopologyID(eq), ar);
		}
		return requests;
	}

	/**
	 * Returns the InputUID for the given event stream.
	 * 
	 * @param eventStreamID
	 *            id of the event stream.
	 * @return corresponding inputUID.
	 */
	public String getInputUIDForEventStream(String eventStreamID) {
		return this.managedNode.getEventCoordinator().getEventManager()
				.getInputByEventStreamID(eventStreamID).getExternalID();
	}

}
