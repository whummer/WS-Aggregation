package at.ac.tuwien.infosys.aggr.node;

import java.util.List;

import javax.jws.WebMethod;
import javax.jws.WebParam;
import javax.jws.WebResult;
import javax.jws.WebService;
import javax.jws.soap.SOAPBinding;
import javax.jws.soap.SOAPBinding.ParameterStyle;
import javax.jws.soap.SOAPBinding.Style;
import javax.jws.soap.SOAPBinding.Use;

import at.ac.tuwien.infosys.aggr.events.query.EventingQueryCoordinator;
import at.ac.tuwien.infosys.aggr.events.query.EventStoresManager.ByteArray;
import at.ac.tuwien.infosys.aggr.events.query.EventingQueryCoordinator.ActiveQueryResult;
import at.ac.tuwien.infosys.aggr.flow.FlowManager;
import at.ac.tuwien.infosys.aggr.monitor.DataServiceMonitor;
import at.ac.tuwien.infosys.aggr.monitor.ModificationNotification;
import at.ac.tuwien.infosys.aggr.monitor.NotificationTask;
import at.ac.tuwien.infosys.aggr.monitor.ModificationNotification.EventStreamIdSOAPHeader;
import at.ac.tuwien.infosys.aggr.node.AggregatorNode.AggregatorException;
import at.ac.tuwien.infosys.aggr.performance.AggregatorPerformanceInfo;
import at.ac.tuwien.infosys.aggr.request.AggregationRequest;
import at.ac.tuwien.infosys.aggr.request.AggregationResponse;
import at.ac.tuwien.infosys.aggr.request.AggregationResponseConstructor;
import at.ac.tuwien.infosys.aggr.request.NonConstantInput;
import at.ac.tuwien.infosys.aggr.request.AbstractInput.InputWrapper;
import at.ac.tuwien.infosys.aggr.strategy.StrategyChain;
import at.ac.tuwien.infosys.aggr.strategy.Topology;
import at.ac.tuwien.infosys.aggr.util.Invoker;
import at.ac.tuwien.infosys.util.Configuration;
import at.ac.tuwien.infosys.ws.EndpointReference;

@WebService(targetNamespace = Configuration.NAMESPACE)
public interface IAggregatorNode {

	@WebMethod
	@WebResult(name = "result")
	@SOAPBinding(style = Style.DOCUMENT, use = Use.LITERAL, parameterStyle = ParameterStyle.BARE)
	public abstract AggregationResponse aggregate(
			@WebParam AggregationRequest request) throws AggregatorException;

	@WebMethod(exclude = true)
	public abstract void onEvent(final ModificationNotification event,
			String eventStreamID) throws AggregatorException;

	@SOAPBinding(style = Style.DOCUMENT, use = Use.LITERAL, parameterStyle = ParameterStyle.BARE)
	@WebMethod(operationName = "Event")
	public abstract void onEvent(
			@WebParam final ModificationNotification event,
			@WebParam(header = true, name = ModificationNotification.LOCALNAME_EVENTSTREAM_ID, targetNamespace = Configuration.NAMESPACE) final EventStreamIdSOAPHeader header)
			throws AggregatorException;

	@WebResult(name = "result")
	@WebMethod
	public abstract ActiveQueryResult pollActiveQueryResult(
			@WebParam(name = "topologyID") String topologyID,
			@WebParam(name = "clientID") String clientID,
			@WebParam(name = "onlyUpdates") Boolean onlyUpdates,
			@WebParam(name = "onlyOnce") Boolean onlyOnce)
			throws AggregatorException;

	@WebResult(name = "topologyID")
	@WebMethod
	public abstract String createTopology(@WebParam(name = "type") String type,
			@WebParam(name = "feature") List<String> feature,
			@WebParam(name = "request") AggregationRequest request)
			throws AggregatorException;

	@WebMethod
	public abstract boolean updateTopology(
			@WebParam(name = "topologyID") String topologyID,
			@WebParam(name = "updates") Object updates)
			throws AggregatorException;

	@WebMethod
	public abstract void onResult(
			@WebParam(name = "topologyID") String topologyID,
			@WebParam(name = "inputUID") String inputUID,
			@WebParam(name = "newResult") Object newResult)
			throws AggregatorException;

	@WebMethod
	@WebResult(name = "success")
	public abstract int inheritInput(
			@WebParam(name = "request") AggregationRequest request,
			@WebParam(name = "topology") Topology topology,
			@WebParam(name = "fromNode") AggregatorNode fromNode)
			throws AggregatorException;

	@WebMethod
	@WebResult(name = "byteArray")
	public abstract ByteArray receiveEventStream(
			@WebParam(name = "input") InputWrapper input,
			@WebParam(name = "forwardFutureEventsTo") EndpointReference forwardFutureEventsTo)
			throws AggregatorException;

	@WebMethod
	public abstract void stopForwardingEvents(
			@WebParam(name = "eventStreamID") String eventStreamID,
			@WebParam(name = "receiver") EndpointReference receiver,
			@WebParam(name = "request") AggregationRequest request);

	@WebMethod
	@WebResult(name = "success")
	public abstract boolean destroyTopology(
			@WebParam(name = "topologyID") String topologyID)
			throws AggregatorException;

	@WebResult(name = "Topology")
	@WebMethod
	public abstract List<Topology> getTopology(
			@WebParam(name = "topologyID") String topologyID)
			throws AggregatorException;

	@WebMethod
	public abstract void addListeners(
			@WebParam(name = "subscriptionID") String subscriptionID,
			@WebParam(name = "listener") List<NotificationTask> listeners);

	@WebResult(name = "subscriptionID")
	@WebMethod
	public abstract String monitorData(
			@WebParam(name = "request") AggregationRequest request,
			String eventStreamID) throws AggregatorException;

	@WebMethod
	public abstract ModificationNotification getMonitoredData(
			@WebParam(name = "subscriptionID") String subscriptionID)
			throws AggregatorException;

	@WebMethod(exclude = true)
	public abstract AggregationResponse handleRequest(
			final AggregationRequest theRequest);

	@WebMethod(exclude = true)
	public abstract String addEventingInput(String topologyID,
			NonConstantInput input, AggregationRequest request,
			boolean startQueriers) throws Exception;

	@WebMethod(exclude = true)
	public abstract void collectResults(final long requestID,
			final int requestCount,
			final AggregationResponseConstructor resultContainer,
			final boolean applyPrepQueries,
			final AggregationRequest requestObject,
			final FlowManager dependencyManager) throws Exception;

	@WebMethod(exclude = true)
	public abstract void initializeEventCoordinator(AggregationRequest request,
			String topologyType, String topologyID, boolean blocking)
			throws Exception;

	@WebMethod(exclude = true)
	public abstract void deploy(String url) throws Exception;

	@WebMethod(exclude = true)
	public abstract StrategyChain getStrategyChain();

	@WebMethod
	public abstract void setStrategy(
			@WebParam(name = "strategyChain") StrategyChain strategyChain)
			throws AggregatorException;

	@WebMethod
	public abstract void startMemoryProfiler();

	@WebMethod
	@WebResult(name = "AggregatorPerformanceInfo", targetNamespace = Configuration.NAMESPACE)
	public abstract AggregatorPerformanceInfo getPerformanceInfo(
			@WebParam(name = "aggregatorEPR") EndpointReference aggregatorEPR,
			@WebParam(name = "doNotDeleteData") Boolean doNotDeleteData,
			@WebParam(name = "detailed") Boolean detailed,
			@WebParam(name = "forUsername") String forUsername)
			throws AggregatorException;

	@WebMethod(exclude = true)
	public abstract DataServiceMonitor getMonitor();

	@WebMethod(exclude = true)
	public abstract EventingQueryCoordinator getEventCoordinator();

	@WebMethod(exclude = true)
	public abstract Invoker getInvoker();

}