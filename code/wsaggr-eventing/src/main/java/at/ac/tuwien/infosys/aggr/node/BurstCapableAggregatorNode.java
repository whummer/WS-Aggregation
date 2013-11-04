package at.ac.tuwien.infosys.aggr.node;

import javax.jws.WebMethod;
import javax.jws.WebParam;
import javax.jws.WebService;
import javax.jws.soap.SOAPBinding;
import javax.jws.soap.SOAPBinding.ParameterStyle;
import javax.jws.soap.SOAPBinding.Style;
import javax.jws.soap.SOAPBinding.Use;
import javax.xml.bind.annotation.XmlRootElement;

import at.ac.tuwien.infosys.aggr.monitor.ModificationNotification;
import at.ac.tuwien.infosys.aggr.monitor.ModificationNotification.EventStreamIdSOAPHeader;
import at.ac.tuwien.infosys.bursthandling.BurstManager;
import at.ac.tuwien.infosys.bursthandling.strategy.EventBurstHandling.EventBurstHandlingException;
import at.ac.tuwien.infosys.events.request.EscalationRequest;
import at.ac.tuwien.infosys.events.request.PauseEscalationRequest;
import at.ac.tuwien.infosys.events.request.StopEscalationRequest;
import at.ac.tuwien.infosys.util.Configuration;
import at.ac.tuwien.infosys.util.Util;
import at.ac.tuwien.infosys.util.par.GlobalThreadPool;
import at.ac.tuwien.infosys.ws.EndpointReference;

/**
 * Extends the AggragatorNode with burst handling. Using the BurstManager
 * specified the load of specified events streams can be reduced by using burst
 * handling strategies.
 * 
 * @author andrea
 * 
 */
@WebService(targetNamespace = Configuration.NAMESPACE, endpointInterface = "at.ac.tuwien.infosys.aggr.node.IBurstCapableAggregatorNode")
@XmlRootElement
public class BurstCapableAggregatorNode extends AggregatorNode implements
		IBurstCapableAggregatorNode {
	/** Logger object */
	private static final org.apache.log4j.Logger logger = Util
			.getLogger(BurstCapableAggregatorNode.class);
	
	/** Feature string for recognition */
	public static final String feature = "burstCapableAggregator";
	
	/** BurstManager, that handles and executes burst strategies */
	private BurstManager burstManager = new BurstManager(this);

	/**
	 * Constructor as in super class.
	 * 
	 * @param epr
	 *            end point for the aggregator node.
	 * @param startWorkerThreads
	 *            boolean flag for worker threads.
	 */
	public BurstCapableAggregatorNode(EndpointReference epr,
			boolean startWorkerThreads) {
		super(epr, startWorkerThreads);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see at.ac.tuwien.infosys.aggr.node.IBurstCapableAggregatorNode#
	 * handleOverloadedQuery
	 * (at.ac.tuwien.infosys.bursthandling.strategy.EventBurstHandling)
	 */
	@Override
	@SOAPBinding(style = Style.DOCUMENT, use = Use.LITERAL, parameterStyle = ParameterStyle.BARE)
	@WebMethod(operationName = "OverloadedQuery")
	public void handleOverloadedQuery(
			@WebParam(name = "escalationRequest") EscalationRequest request)
			throws EventBurstHandlingException {
		this.burstManager.startEscalation(request.getEventStreamID(),
				request.getStrategy());
	}

	private boolean first = true;

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * at.ac.tuwien.infosys.aggr.node.AggregatorNode#onEvent(at.ac.tuwien.infosys
	 * .aggr.monitor.ModificationNotification,
	 * at.ac.tuwien.infosys.aggr.monitor.
	 * ModificationNotification.EventStreamIdSOAPHeader)
	 */
	@Override
	@SOAPBinding(style = Style.DOCUMENT, use = Use.LITERAL, parameterStyle = ParameterStyle.BARE)
	@WebMethod(operationName = "Event")
	public void onEvent(
			@WebParam final ModificationNotification event,
			@WebParam(header = true, name = ModificationNotification.LOCALNAME_EVENTSTREAM_ID, targetNamespace = Configuration.NAMESPACE) final EventStreamIdSOAPHeader header)
			throws AggregatorException {
		Runnable r = new Runnable() {
			@Override
			public void run() {
				try {
					if (header != null)
						event.setEventStreamID(header.value);
					if (logger.isDebugEnabled())
						logger.debug("Aggregator onEvent: "
								+ this
								+ " - "
								+ event
								+ (!event.hasHeaders() ? "" : " - headers: "
										+ event.getHeadersCopy()));

					if (first == true) {
						System.out.println("EventStreamID: "
								+ event.getEventStreamID());
						first = false;
					}

					// first: let the data service monitor (which monitors
					// services for changes in the returned data) consume (or
					// ignore) the event.
					monitor.handleEvent(event);

					// second: let the burst handling strategy decide how to
					// proceed
					// case 1) strategy does not effect event -> proceed
					// case 2) strategy handles event -> do not proceed
					// case 3) event is result of strategy handling
					// -> do not proceed
					boolean proceed = burstManager.handleEvent(event);

					// third: let the 'general' event manager (which performs
					// XQuery Window queries on the received events) consume (or
					// ignore) the event.
					if (proceed) {
						eventCoordinator.handleEvent(event);
					}
				} catch (Exception e) {
					logger.info(
							"Aggregator could not process externally received event: "
									+ event, e);
				}
			}
		};

		GlobalThreadPool.execute(r);
	}

	@Override
	@SOAPBinding(style = Style.DOCUMENT, use = Use.LITERAL, parameterStyle = ParameterStyle.BARE)
	@WebMethod(operationName = "StopEscalation")
	public void stopEscalation(
			@WebParam(name = "stopEscalationRequest") StopEscalationRequest request) {
		this.burstManager.stopEscalation(request.getEventStreamID());
	}

	@Override
	@SOAPBinding(style = Style.DOCUMENT, use = Use.LITERAL, parameterStyle = ParameterStyle.BARE)
	@WebMethod(operationName = "PauseEscalation")
	public void pauseEscalation(
			@WebParam(name = "pauseEscalationRequest") PauseEscalationRequest request) {
		// TODO
		throw new UnsupportedOperationException();
	}
	
	@Override
	@WebMethod(exclude=true)
	public void deploy(String url) throws Exception {
		super.deploy(url);
		Runnable r = new Runnable() {
			public void run() {
				try {
					// for the distinction of arbitrary aggregators and burst capable aggregators
					Registry.getRegistryProxy().addDataServiceNode(BurstCapableAggregatorNode.feature, getEpr());
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
			}
		};
		GlobalThreadPool.executePeriodically(r, 60*1000, 5*1000);
	}

}
