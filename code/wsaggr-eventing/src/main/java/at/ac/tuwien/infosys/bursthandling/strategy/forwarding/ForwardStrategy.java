/**
 * 
 */
package at.ac.tuwien.infosys.bursthandling.strategy.forwarding;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import org.apache.log4j.Logger;
import org.w3c.dom.Element;

import at.ac.tuwien.infosys.aggr.AggregationClient;
import at.ac.tuwien.infosys.aggr.monitor.ModificationNotification;
import at.ac.tuwien.infosys.aggr.node.AggregatorNode;
import at.ac.tuwien.infosys.aggr.node.AggregatorNode.AggregatorException;
import at.ac.tuwien.infosys.aggr.request.AggregationRequest;
import at.ac.tuwien.infosys.bursthandling.strategy.EventBurstHandling;
import at.ac.tuwien.infosys.events.ws.WSEvent;
import io.hummer.util.Configuration;

/**
 * @author andrea
 * 
 */
@XmlRootElement(name = ForwardStrategy.JAXB_ELEMENT_NAME, namespace = Configuration.NAMESPACE)
@XmlJavaTypeAdapter(EventBurstHandling.Adapter.class)
@XmlSeeAlso(EventBurstHandling.class)
public class ForwardStrategy extends EventBurstHandling {
	/**
	 * Logger object.
	 */
	private static final Logger logger = Logger
			.getLogger(EventBurstHandling.class);
	/**
	 * Strategy name.
	 */
	public static final String JAXB_ELEMENT_NAME = "forwardStrategy";
	/**
	 * Default amount of nodes used for forwarding.
	 */
	private static final int DEFAULT_NODE_COUNT = 2;
	/**
	 * Default state of pure forwarding.
	 */
	private static final boolean DEFAULT_PURE_FORWARD = false;
	/**
	 * Used nodes for forwarding.
	 */
	@XmlTransient
	private List<AggregatorNode> nodesToForward;
	/**
	 * Next node indicator.
	 */
	@XmlTransient
	private int nodeForwardIndex;
	/**
	 * ID Mapping.
	 */
	@XmlTransient
	private Map<String, EventStreamInfo> specialIDToOriginalIDs;
	/**
	 * Nodes used for forwarding.
	 */
	@XmlElement(name = "nodeCount")
	private int nodeCount;
	/**
	 * Flag pure forwarding (true = original aggregator does no event handling).
	 */
	@XmlElement(name = "pureFoward")
	private boolean pureForward;

	public ForwardStrategy() {
		this(ForwardStrategy.DEFAULT_NODE_COUNT,
				ForwardStrategy.DEFAULT_PURE_FORWARD);
	}

	public ForwardStrategy(int minNodes) {
		this(minNodes, ForwardStrategy.DEFAULT_PURE_FORWARD);
	}

	public ForwardStrategy(boolean pureForward) {
		this(ForwardStrategy.DEFAULT_NODE_COUNT, pureForward);
	}

	/**
	 * Constructs a new forward strategy, that uses at least minNodes for
	 * forwarding. If pureForward is true, the original aggregator does not
	 * handle any events henceforth.
	 * 
	 * @param nodeCount
	 *            amount of nodes used.
	 * @param pureFoward
	 *            true if the original aggregator handles none of the events.
	 */
	public ForwardStrategy(int nodeCount, boolean pureFoward) {
		this.nodeCount = nodeCount;
		this.pureForward = pureFoward;
		this.nodesToForward = new ArrayList<AggregatorNode>();
		this.specialIDToOriginalIDs = new HashMap<String, ForwardStrategy.EventStreamInfo>();
	}

	/**
	 * Initializes the forwarding strategy. The used aggregator nodes are
	 * chosen.
	 * 
	 * @throws EventBurstHandlingException
	 * 
	 * @throws Exception
	 */
	@Override
	public void initialize(String eventStreamID)
			throws EventBurstHandlingException {
		this.nodeForwardIndex = 0;
		this.nodesToForward.clear();
		this.specialIDToOriginalIDs.clear();

		List<AggregatorNode> availableNodes = this.burstManager
				.provideAggregators();
		int counter = 0;
		for (AggregatorNode agg : availableNodes) {
			// events can also be handled by normal aggregators
			// and this condition never evaluates to true, because of registry
//			 if (agg instanceof BurstCapableAggregatorNode) {
			nodesToForward.add(agg);
			counter++;
//			 }
			if (counter == this.nodeCount) {
				break;
			}
		}
		
		logger.info(counter + " nodes found");
		
		// TODO: other handling? exception?
		if (counter == 0) {
			logger.warn("No nodes for forwarding available. Burst handling strategy will have no impact!");
		} else if (counter < this.nodeCount) {
			logger.warn("Could not request the min node count. (" + counter
					+ " out of " + this.nodeCount + " nodes)");
		}

		// do aggregation requests
		Map<String, AggregationRequest> requests;
		try {
			requests = this.burstManager.getAggregationRequest(eventStreamID);
		} catch (Exception e) {
			throw new EventBurstHandlingException(
					"Aggregation requests for forwarding strategy could not be created.");
		}
		String inputUID = this.burstManager
				.getInputUIDForEventStream(eventStreamID);

		for (String topologyID : requests.keySet()) {
			for (AggregatorNode agg : nodesToForward) {

				String specialEventStreamID = UUID.randomUUID().toString();
				this.specialIDToOriginalIDs.put(specialEventStreamID,
						new EventStreamInfo(topologyID, inputUID));
				AggregationRequest req = requests.get(topologyID);
				Element param;
				try {
					param = WSEvent
							.createHeader(
									ModificationNotification.SOAP_HEADER_EVENT_STREAM_ID,
									specialEventStreamID);
					req.getMonitor().getEpr().addReferenceParameter(param);

					AggregationClient client = new AggregationClient(
							agg.getEPR());
					client.aggregate(req);
				} catch (Exception e) {
					logger.error("something happend. ", e);
					// TODO: ja was mach ich jetzt bloß?!
				}

			}
		}
	}

	/**
	 * Determines which node will be used for forwarding next.
	 * 
	 * @return the aggregator node for forwarding or null if no other node can /
	 *         should be used.
	 */
	private AggregatorNode nextNode() {
		// no nodes for forwarding available
		if (this.nodesToForward.size() == 0) {
			return null;
		}
		// move on to next node
		this.nodeForwardIndex++;

		// check index
		if (nodeForwardIndex >= this.nodesToForward.size()) {
			// if not pure forward -> use original node before next round
			if (!this.pureForward) {
				this.nodeForwardIndex = -1;
				return null;
			}
			// else move on to the first node
			else {
				this.nodeForwardIndex = 0;
			}
		}
		// return the next node to be used
		return this.nodesToForward.get(this.nodeForwardIndex);
	}

	/**
	 * Handles an event for the burst strategy. # Event is forwarded. # Event is
	 * passed through and handled by the original aggregator. # Event is
	 * identified as result of a forwarding and the result is sent to the
	 * original aggregator.
	 * 
	 * @param event
	 *            to be processed.
	 * @return true if the event has to be processed by the original aggregator.
	 *         False if it has already been processed.
	 * @throws AggregatorException
	 */
	@Override
	public boolean handleEvent(ModificationNotification event)
			throws AggregatorException {
		// event is a result -> finish processing
		if (!examineEvent(event)) {
			return false;
		}

		AggregatorNode aggNode = nextNode();
		// event is handled by the "root aggregator node"
		if (aggNode == null) {
			logger.info("event is handled by this node...");
			return true;
		}

		// forward
//		try {
//			IBurstCapableAggregatorNode forward = DynamicWSClient.createClientJaxws(
//						IBurstCapableAggregatorNode.class, new URL(aggNode.getEPR()
//								.getAddress() + "?wsdl"));
//			for (String eventStreamID : this.specialIDToOriginalIDs.keySet()) {
//				forward.onEvent(event, new EventStreamIdSOAPHeader(eventStreamID));
//				logger.info("event " + event.getIdentifier() + " has been forwarded to "
//						+ aggNode.getEPR().getAddress() + " with special id " + eventStreamID);
//			}
//			return false;
//		} catch (MalformedURLException e) {
			logger.error("Could not forward event to aggregator "
					+ aggNode.getEPR().getAddress() + ".");
			return true;
//		}
	}

	@Override
	public String info() {
		return "Forwarding strategy with at least " + this.nodeCount
				+ " nodes.";
	}

	/**
	 * @return the minNodes
	 */
	@XmlTransient
	public int getMinNodes() {
		return nodeCount;
	}

	/**
	 * Checks if the event is a result, that does not require further
	 * processing.
	 * 
	 * @param event
	 *            to be checked.
	 * @return true if further processing has to be done. False otherwise.
	 * @throws AggregatorException
	 */
	private boolean examineEvent(ModificationNotification event)
			throws AggregatorException {
		if (this.specialIDToOriginalIDs.containsKey(event.getEventStreamID())) {
			logger.info("Event result received -> forward!");
			EventStreamInfo info = this.specialIDToOriginalIDs.get(event
					.getEventStreamID());
			this.burstManager.finishEventHandling(info.getTopologyID(),
					info.getInputUID(), event);
			return false;
		}
		return true;
	}

	private class EventStreamInfo {
		private String topologyID;
		private String inputUID;

		public EventStreamInfo(String topologyID, String inputUID) {
			this.topologyID = topologyID;
			this.inputUID = inputUID;
		}

		public String getTopologyID() {
			return topologyID;
		}

		public String getInputUID() {
			return inputUID;
		}
	}

	@Override
	public void shutdown() {
		// free used aggregator nodes
		// stop listening for results??
		// or continue for a while?

	}
}
// TODO: eventuell check ob nodes noch verfügbar sind