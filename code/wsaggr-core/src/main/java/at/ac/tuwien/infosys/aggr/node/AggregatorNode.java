/*
 * Project 'WS-Aggregation':
 * http://www.infosys.tuwien.ac.at/prototype/WS-Aggregation/
 *
 * Copyright 2010-2012 Vienna University of Technology
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package at.ac.tuwien.infosys.aggr.node;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import javax.jws.WebMethod;
import javax.jws.WebParam;
import javax.jws.WebResult;
import javax.jws.WebService;
import javax.jws.soap.SOAPBinding;
import javax.jws.soap.SOAPBinding.ParameterStyle;
import javax.jws.soap.SOAPBinding.Style;
import javax.jws.soap.SOAPBinding.Use;
import javax.xml.bind.annotation.XmlRootElement;

import org.apache.log4j.Logger;
import org.w3c.dom.Element;

import at.ac.tuwien.infosys.aggr.flow.FlowManager;
import at.ac.tuwien.infosys.aggr.flow.FlowNode.DependencyUpdatedInfo;
import at.ac.tuwien.infosys.aggr.performance.AggregatorPerformanceInfo;
import at.ac.tuwien.infosys.aggr.performance.AggregatorPerformanceInfo.UserDataRate;
import at.ac.tuwien.infosys.aggr.performance.AggregatorPerformanceInfo.InputDataRate;
import at.ac.tuwien.infosys.aggr.performance.AggregatorPerformanceInfo.InterAggregatorsDataRate;
import at.ac.tuwien.infosys.aggr.performance.AggregatorPerformanceInfo.StreamDataRate;
import at.ac.tuwien.infosys.aggr.proxy.AggregatorNodeProxy;
import at.ac.tuwien.infosys.aggr.proxy.RegistryProxy;
import at.ac.tuwien.infosys.aggr.request.AbstractInput;
import at.ac.tuwien.infosys.aggr.request.AggregationRequest;
import at.ac.tuwien.infosys.aggr.request.AggregationResponse;
import at.ac.tuwien.infosys.aggr.request.AggregationResponseConstructor;
import at.ac.tuwien.infosys.aggr.request.ConstantInput;
import at.ac.tuwien.infosys.aggr.request.EventingInput;
import at.ac.tuwien.infosys.ws.request.InvocationResult;
import at.ac.tuwien.infosys.aggr.request.NonConstantInput;
import at.ac.tuwien.infosys.aggr.request.RequestInput;
import at.ac.tuwien.infosys.aggr.request.WAQLQuery;
import at.ac.tuwien.infosys.aggr.request.AbstractInput.InputWrapper;
import at.ac.tuwien.infosys.aggr.request.AbstractInput.RequestInputs;
import at.ac.tuwien.infosys.aggr.request.AggregationResponse.DebugInfo;
import at.ac.tuwien.infosys.aggr.request.WAQLQuery.PreparationQuery;
import at.ac.tuwien.infosys.aggr.strategy.StrategyChain;
import at.ac.tuwien.infosys.aggr.strategy.Topology;
import at.ac.tuwien.infosys.aggr.monitor.DataServiceMonitor;
import at.ac.tuwien.infosys.aggr.monitor.ModificationNotification;
import at.ac.tuwien.infosys.aggr.monitor.NotificationTask;
import at.ac.tuwien.infosys.aggr.monitor.ModificationNotification.EventStreamIdSOAPHeader;
import at.ac.tuwien.infosys.util.Configuration;
import at.ac.tuwien.infosys.aggr.util.Invoker;
import at.ac.tuwien.infosys.aggr.util.RequestAndResultQueues;
import at.ac.tuwien.infosys.util.Util;
import at.ac.tuwien.infosys.aggr.util.Invoker.InvokerTask;
import at.ac.tuwien.infosys.aggr.util.RequestAndResultQueues.RequestWorker;
import at.ac.tuwien.infosys.aggr.events.query.EventQuerier;
import at.ac.tuwien.infosys.aggr.events.query.EventStoresManager;
import at.ac.tuwien.infosys.aggr.events.query.EventingQueryCoordinator;
import at.ac.tuwien.infosys.aggr.events.query.EventStoresManager.ByteArray;
import at.ac.tuwien.infosys.aggr.events.query.EventStream;
import at.ac.tuwien.infosys.aggr.events.query.EventingQueryCoordinator.ActiveQueryResult;
import at.ac.tuwien.infosys.util.coll.Pair;
import at.ac.tuwien.infosys.util.misc.PerformanceInterceptor;
import at.ac.tuwien.infosys.util.misc.PerformanceInterceptor.EventType;
import at.ac.tuwien.infosys.util.par.GlobalThreadPool;
import at.ac.tuwien.infosys.util.par.Parallelization;
import at.ac.tuwien.infosys.util.perf.PerformanceProfiler;

import at.ac.tuwien.infosys.ws.EndpointReference;
import at.ac.tuwien.infosys.ws.AbstractNode;

@WebService(targetNamespace=Configuration.NAMESPACE, endpointInterface = "at.ac.tuwien.infosys.aggr.node.IAggregatorNode")
@XmlRootElement
public class AggregatorNode extends AbstractNode 
		implements RequestWorker<AggregationRequest, AggregationResponse>, IAggregatorNode {

	private static final int READ_TIMEOUT_MS = 1000*60*2;
	private static final int READ_TIMEOUT_VERYLONG_MS = 1000*60*60;
	private static final boolean AUTO_START_MEM_PROFILING = true;
	private static final PerformanceProfiler memProfiler = new PerformanceProfiler();
	protected static final Logger logger = at.ac.tuwien.infosys.util.Util.getLogger(AggregatorNode.class);
	
	protected Util util = new Util();
	private StrategyChain strategyChain;
	private Invoker invoker;
	private RequestAndResultQueues<AggregationRequest, AggregationResponse> queues;
	protected DataServiceMonitor monitor;
	protected EventingQueryCoordinator eventCoordinator;


	public static class AggregatorException extends Exception {
		private static final long serialVersionUID = 1L;
		public AggregatorException() {}
		public AggregatorException(Throwable t) {
			super(t);
		}
	}
	
	/**
	 * Required constructor for JAXB, should not be used by the programmer!
	 */
	@Deprecated
	public AggregatorNode() {}
	
	public AggregatorNode(EndpointReference epr, boolean startWorkerThreads) {
		setEpr(epr);
		if(startWorkerThreads) {
			strategyChain = StrategyChain.loadDefault(this);
			strategyChain.setOwner(this);
			queues = new RequestAndResultQueues<AggregationRequest, AggregationResponse>(this/*, 400, 1000*/);
			if(AUTO_START_MEM_PROFILING)
				startMemoryProfiler();
			invoker = Invoker.getInstance();
			monitor = new DataServiceMonitor(this);
			eventCoordinator = new EventingQueryCoordinator(this);
		}
	}

	@WebMethod
	@WebResult(name="result")
	@SOAPBinding(style=Style.DOCUMENT, use=Use.LITERAL, parameterStyle=ParameterStyle.BARE)
	public AggregationResponse aggregate(@WebParam AggregationRequest request) throws AggregatorException {

		try {

			if(request.getInputs().getInputsCopy() == null || request.getInputs().getInputsCopy().isEmpty()) {
				throw new Exception("Aggregation request contains zero inputs.");
			}

			if(request.containsEventingOrMonitoringInput()) {
				/* this is a continuous/event-based/long-running query 
				 * and has to be treated specially... */
				String id = createTopology(null, null, request);
				AggregationResponse response = new AggregationResponse(request);
				response.setResult(util.xml.toElement("<result><queryID>" + id + "</queryID></result>"));
				return response;
			}
			
			long requestID = InvokerTask.getNewRequestID();
			request.setInternalID(requestID);

			System.out.println("Aggregator request: " + util.xml.toString(request));
			if(logger.isDebugEnabled()) logger.debug("Aggregator request: " + util.xml.toString(request));
			queues.putRequest(request);

			AggregationResponse response = queues.takeResponse(requestID);

			queues.releaseResources(requestID);
			
			if(response.isError()) {
				if(response.isException())
					throw (Exception)response.getResult();
				throw new Exception("Illegal result retrieved in Aggregator: " + response.getResult());
			}
			return response;
		} catch (Exception e) {
			logger.warn("Could not execute aggregation request.", e);
			throw new AggregatorException(e);
		}
	}

	@WebMethod(exclude=true)
	public void onEvent(final ModificationNotification event, 
			String eventStreamID) throws AggregatorException {
		onEvent(event, eventStreamID != null ? 
				new EventStreamIdSOAPHeader(eventStreamID) : null);
	}

	@SOAPBinding(style=Style.DOCUMENT, use=Use.LITERAL, parameterStyle=ParameterStyle.BARE)
	@WebMethod(operationName="Event")
	public void onEvent(
			@WebParam final ModificationNotification event,
			@WebParam(header=true, 
					name=ModificationNotification.LOCALNAME_EVENTSTREAM_ID,
					targetNamespace=Configuration.NAMESPACE) 
			final EventStreamIdSOAPHeader header) throws AggregatorException {
		Runnable r = new Runnable() {
			@Override
			public void run() {
				try {
					if(header != null)
						event.setEventStreamID(header.value);

					if(logger.isDebugEnabled()) logger.debug("Aggregator onEvent: " + this + " - " + event + (!event.hasHeaders() ? "" : " - headers: " + event.getHeadersCopy()));
					// first: 	let the data service monitor (which monitors services for 
					// 			changes in the returned data) consume (or ignore) the event. 
					monitor.handleEvent(event);
					// second: 	let the 'general' event manager (which performs XQuery Window 
					//			queries on the received events) consume (or ignore) the event.
					eventCoordinator.handleEvent(event);
				} catch (Exception e) {
					logger.info("Aggregator could not process externally received event: " + event, e);
				}
			}
		};
		
		GlobalThreadPool.execute(r);
	}
	
	@WebResult(name="result")
	@WebMethod
	public ActiveQueryResult pollActiveQueryResult(
			@WebParam(name="topologyID") String topologyID, 
			@WebParam(name="clientID") String clientID, 
			@WebParam(name="onlyUpdates") Boolean onlyUpdates, 
			@WebParam(name="onlyOnce") Boolean onlyOnce) throws AggregatorException {
		try {
			return eventCoordinator.pollActiveQueryResult(topologyID, clientID, onlyUpdates, onlyOnce);
		} catch (Exception e) {
			throw new AggregatorException(e);
		}
	}

	@WebResult(name="topologyID")
	@WebMethod
	public String createTopology(
			@WebParam(name="type") String type,
			@WebParam(name="feature") List<String> feature,
			@WebParam(name="request") AggregationRequest request) throws AggregatorException {

		// added. required if aggregation request is not sent through the gateway
		if(type == null)
			type = "tree(1,0)";
		
		try {
		
			if(request != null)
				return strategyChain.createTopology(type, request);
			else if(feature != null && feature.size() > 0)
				return strategyChain.createTopology(type, feature);
			throw new IllegalArgumentException("Either feature(s) or an aggregation request (for data monitoring) need to be specified for new topology.");

		} catch (Exception e) {
			throw new AggregatorException(e);
		}

	}

	@WebMethod
	public boolean updateTopology(
			@WebParam(name="topologyID") String topologyID,
			@WebParam(name="updates") Object updates) throws AggregatorException {
		if(logger.isDebugEnabled()) logger.debug("Updating topology with ID " + topologyID + ", aggregator " + this);
		try {
			strategyChain.updateTopology(topologyID, updates);
			eventCoordinator.updateTopology(topologyID, updates);
		} catch (Exception e) {
			throw new AggregatorException(e);
		}
		return true;
	}

	@WebMethod
	public void onResult(@WebParam(name="topologyID") String topologyID,
			@WebParam(name="inputUID") String inputUID,
			@WebParam(name="newResult") Object newResult) throws AggregatorException {
		try {
			Element result = util.xml.getChildElements((Element)newResult).get(0);
			eventCoordinator.addResultFromInputAndNotifyClient(topologyID, inputUID, result);
		} catch (Exception e) {
			logger.warn("Unable to store remotely received result into local event store.", e);
			throw new RuntimeException("Unable to store remotely received result into local event store.", e);
		}
	}

	@WebMethod
	@WebResult(name="success")
	public int inheritInput(
			@WebParam(name="request") AggregationRequest request,
			@WebParam(name="topology") Topology topology,
			@WebParam(name="fromNode") AggregatorNode fromNode) throws AggregatorException {

		try {

		AggregatorNodeProxy aggr = new AggregatorNodeProxy(fromNode);
		NonConstantInput in = (NonConstantInput)request.getAllInputs().get(0);
		EventStoresManager mgr = eventCoordinator.getEventManager();
		EventStream stream = mgr.getEventStreamForInput(in, null, false);
		
		final String topologyID = topology.getTopologyID();
		String eventStreamID = null;
		boolean isNewStream = stream == null;
		int size = 0;
		
		if(isNewStream) {
			request.setTopologyID(topologyID);
			String dump = aggr.receiveEventStreamDump(in, getEPR());
			size = dump.length();
			
			String tagStart = dump.substring(0, dump.indexOf(">"));
			tagStart = tagStart.substring(tagStart.indexOf("eventStreamID=\"") + "eventStreamID=\"".length());
			eventStreamID = tagStart.substring(0, tagStart.indexOf("\"")); //stream.eventStreamID;
			eventCoordinator.initTempBuffer(eventStreamID);
			stream = eventCoordinator.getEventManager().reconstructEventStreamFromDump(in, dump, topologyID);
			
		} else {
			logger.info("The target event stream already exists for this aggregator: " + stream.eventStreamID);
		}
		
		topology = eventCoordinator.getOrAddTopology(topology);
		eventCoordinator.initialize(request, null, topologyID, true);
		eventCoordinator.addEventingInput(topologyID, (EventingInput)in, request);
		
		if(isNewStream) {

			// TODO: the previous command and the next command (creating new  
			// subscription and terminating event forwarding) should actually happen 
			// somehow "atomically" (simultaneously) to avoid duplicate events..
			aggr.stopProcessingAndForwarding(eventStreamID, getEPR(), request);
			// wait a second until the source aggregator has forwarded its last events
			Thread.sleep(1000);
			eventCoordinator.finalizeAndRemoveTempBuffers(eventStreamID);
		}

		topology.moveTargetServiceRequest(request, fromNode, this);
		final Topology theTopology = topology;

		final AggregatorNode master = topology.getMasterAggregator();
		if(master.equals(this)) {
			updateTopology(topologyID, topology);
		} else {
			Runnable r = new Runnable() {
				public void run() {
					AggregatorNodeProxy masterProxy = new AggregatorNodeProxy(master.getEPR(), true);
					try {
						masterProxy.updateTopology(topologyID, theTopology);
					} catch (Exception e) {
						logger.warn("Could not notify partners of new topology.", e);
					}
				}
			};
			r.run();
		}
		return size;
		
		} catch (Exception e) {
			throw new AggregatorException(e);
		}
	}
	
	@WebMethod
	@WebResult(name="byteArray")
	public ByteArray receiveEventStream(@WebParam(name="input") InputWrapper input,
			@WebParam(name="forwardFutureEventsTo") EndpointReference forwardFutureEventsTo) throws AggregatorException {
		ByteArray array = new ByteArray();
		try {
			NonConstantInput in = (NonConstantInput)input.input;
			EventStream stream = eventCoordinator.getEventManager().getEventStreamForInput(in, null, false);
			if(stream == null) {
				logger.warn("Cannot serialize, event stream is null for input " + input.input);
				return null;
			}
			array.string = eventCoordinator.getEventManager().getEventBufferDump(stream);
			eventCoordinator.doForwardEvents(stream.eventStreamID, forwardFutureEventsTo);
			return array;
		} catch (Exception e) {
			logger.warn("Error serializing event store buffer.", e);
		}
		return null;
	}
	
	@WebMethod
	public void stopForwardingEvents(
			@WebParam(name="eventStreamID") String eventStreamID,
			@WebParam(name="receiver") EndpointReference receiver,
			@WebParam(name="request") AggregationRequest request) {
		eventCoordinator.stopProcessingAndForwarding(eventStreamID, receiver, request);
	}

	@WebMethod
	@WebResult(name="success")
	public boolean destroyTopology(
			@WebParam(name="topologyID") String topologyID) throws AggregatorException {
		strategyChain.destroyTopology(topologyID);
		try {
			eventCoordinator.destroyTopology(topologyID);
		} catch (Exception e) {
			throw new AggregatorException(e);
		}
		System.gc();
		return true;
	}
	@WebResult(name="Topology")
	@WebMethod
	public List<Topology> getTopology(@WebParam(name="topologyID") String topologyID) throws AggregatorException {
		try {
			List<Topology> result = eventCoordinator.getTopology(topologyID);
			return result;
		} catch (Exception e) {
			throw new AggregatorException(e);
		}
	}

	@WebMethod
	public void addListeners(@WebParam(name="subscriptionID") String subscriptionID, @WebParam(name="listener") List<NotificationTask> listeners) {
		monitor.addListeners(listeners);
	}

	@WebResult(name="subscriptionID")
	@WebMethod
	public String monitorData(@WebParam(name="request") AggregationRequest request, 
			String eventStreamID) 
			throws AggregatorException {

		String monitoringTaskID = monitor.monitor(request);

		monitor.getTask(request.getRequestID(), monitoringTaskID).setEventStreamID(eventStreamID);

		// register this monitoring subscription ID for later lookup of the corresponding AbstractInput..
		eventCoordinator.addMonitoringIDforInput(monitoringTaskID, request.getAllInputs().get(0));
		
		return monitoringTaskID;
	}

	@WebMethod
	public ModificationNotification getMonitoredData(@WebParam(name="subscriptionID") String subscriptionID) throws AggregatorException {
		return monitor.getMonitoredData(subscriptionID);
	}
	
	@Override
	@WebMethod(exclude=true)
	public AggregationResponse handleRequest(final AggregationRequest theRequest) {

		AggregationResponseConstructor result = null;

		try {
			result = new AggregationResponseConstructor(theRequest, false);

			String query = theRequest.getQueries().getQuery();
			String intermediateQuery = theRequest.getQueries().getIntermediateQuery();
			List<PreparationQuery> preparationQueries = theRequest.getQueries().getPreparationQueries();
			String topologyID = theRequest.getTopologyID();
			if(topologyID == null || topologyID.trim().equals(""))
				topologyID = null;
			if(query == null || query.trim().equals(""))
				query = null;
			if(preparationQueries == null || preparationQueries.size() <= 0)
				preparationQueries = null;
			if(intermediateQuery == null || intermediateQuery.trim().equals(""))
				intermediateQuery = null;
			
			List<AbstractInput> inputs = new LinkedList<AbstractInput>(theRequest.getAllInputs());
			
			if(inputs.size() <= 0) {
				return result.getTotalResult(false);
			}
			
			long partnersRequestID = invoker.getNewRequestID();
			int partnersRequestCount = 0;
			
			
			FlowManager dependencyManager = theRequest.getManager();
			if(dependencyManager == null) {
				dependencyManager = new FlowManager(theRequest, -1);
				theRequest.setManager(dependencyManager);
			}
			
			// append (independent) constant inputs to result
			for(int i = 0; i < inputs.size(); i ++) {
				AbstractInput in = inputs.get(i);
				if((in instanceof ConstantInput) && 
						dependencyManager.isIndependent(in)) {
					try {
						DebugInfo debug = new DebugInfo();
						
						List<? extends AbstractInput> generated = in.generateInputs();
						
						for(AbstractInput g : generated) {
							List<DependencyUpdatedInfo> updated = dependencyManager.update(
									new InvocationResult(g.getTheContent()), g);
							if(logger.isDebugEnabled()) logger.debug("updated: " + updated);
							// set debug info
							if(updated.size() > 0) {
								debug.hasDependency = true;
								debug.updated.addAll(updated);
							}
							debug.setInResponseTo(g.getExternalID());
							result.addDebugInfo(debug);
							//System.out.println("adding constant input " + g);
							result.addInvocationResponse(g, g.getTheContentAsElement(), null, true);
						}
						inputs.remove(i--);
					} catch (Exception e) {
						logger.error("Exception when adding constant inputs and updating dependencies.", e);
					}
				}
			}
			
			while(true /*containsRequestInputs(inputs)*/) {
				
				Map<AbstractNode,List<RequestInput>> sortedRequests = new HashMap<AbstractNode, List<RequestInput>>();
				synchronized (strategyChain) {
					// generate inputs!
					List<AbstractInput> independentInputs = 
						dependencyManager.filterInputsWithoutDependencies(true);
					if(independentInputs.isEmpty()) {
						if(inputs.size() > 0) {
							// Cannot resolve one or more data dependencies! collect temporary results and return error.
							collectResults(partnersRequestID, partnersRequestCount, result, true, theRequest, dependencyManager);
							result.setTotalResult(new Exception("Cannot resolve one or more data dependencies in query. Remaining input(s): " + inputs));
							return result.getTotalResult(false);
						}
						break;
					}
					List<AbstractInput> generatedInputs = new LinkedList<AbstractInput>();
					for(AbstractInput i : independentInputs) {
						List<AbstractInput> temp = i.generateInputs();
						generatedInputs.addAll(temp);
					}
					
					if(generatedInputs.size() < independentInputs.size()) {
						throw new RuntimeException("Set of generated inputs is smaller than original set of inputs before generation: before: " + 
								independentInputs + " - after: " + generatedInputs);
					}
					
					strategyChain.generateRequests(topologyID, generatedInputs, sortedRequests, theRequest);
					//System.out.println("AggregatorNode: assigned inputs: " + sortedRequests);
					if(logger.isDebugEnabled()) logger.debug("generated requests: " + sortedRequests + " - " + independentInputs);
				}
			
				
				int sentRequests = 0;
				List<AbstractNode> nodes = new LinkedList<AbstractNode>(sortedRequests.keySet());
				
				for(AbstractNode node : nodes) {
					if((node instanceof AggregatorNode) && !node.equals(this)) {
						
						List<RequestInput> partnerInputs = sortedRequests.get(node);
						removeAllInputsByUID(inputs, partnerInputs);
						
						
						if(partnerInputs.size() > 0) {
							AggregatorNode partner = (AggregatorNode)node;
							if(this.getEPR().equals(partner.getEPR())) {
								logger.warn("EPR of partner equals own EPR!");
								logger.debug("" + sortedRequests.get(partner));
								return result.getTotalResult(false);
							}
							
							WAQLQuery queries = new WAQLQuery();
							queries.setIntermediateQuery(intermediateQuery);
							queries.setPreparationQueries(preparationQueries);
							RequestInputs requestInputs = new RequestInputs(partnerInputs);
							AggregationRequest a = new AggregationRequest(0, theRequest.getRequestID(),
									partnerInputs.get(0).topologyID, requestInputs, queries);
							a.setTimeout(theRequest.getTimeout());
							Element message = util.xml.toElement(a);
							
							RequestInput input = new RequestInput();
							input.setTheContent(message);
							InvokerTask task = new InvokerTask(partnersRequestID, partner, input, 
									util.test.isNullOrTrue(theRequest.getTimeout()));
							invoker.addRequest(task);
							sentRequests ++;
							partnersRequestCount ++;
							
							// add debug info
							for(RequestInput in : partnerInputs) {
								DebugInfo debug = new DebugInfo();
								debug.inResponseToInput = in;
								debug.inResponseToInputOriginal = (RequestInput)in.
											getFirstInListByExternalID(theRequest.getAllInputs());
								debug.toNode = partner;
								result.addDebugInfo(debug);
							}
						}
					}
				}
				
				
				long dataServicesRequestID = invoker.getNewRequestID();
				int dataServicesRequestCount = 0;
				for(AbstractNode node : nodes) {
					
					if(node instanceof DataServiceNode) {
						
						List<RequestInput> ourInputs = sortedRequests.get(node);
						removeAllInputsByUID(inputs, ourInputs);
						
						for(RequestInput input : ourInputs) {

							if(logger.isDebugEnabled()) logger.debug("Processing input: " + input);
							InvokerTask request = new InvokerTask(dataServicesRequestID, node, input,
									util.test.isNullOrTrue(theRequest.getTimeout()));
							invoker.addRequest(request);
							sentRequests ++;
							dataServicesRequestCount ++;

						}
					}
				}
				
				if(sentRequests <= 0 && inputs.size() > 0) {
					// Cannot resolve one or more data dependencies! collect temporary results and return error.
					collectResults(partnersRequestID, partnersRequestCount, result, true, theRequest, dependencyManager);
					result.setTotalResult(new Exception("Cannot resolve one or more data dependencies in query. Remaining inputs: " + inputs));
					return result.getTotalResult(false);
				}
				
				// collect results from "own" target services
				String tmpID = PerformanceInterceptor.event(EventType.START_COLLECT_RESULTS);
				collectResults(dataServicesRequestID, dataServicesRequestCount, result, true, theRequest, dependencyManager);
				invoker.releaseResources(dataServicesRequestID);
				PerformanceInterceptor.event(EventType.FINISH_COLLECT_RESULTS, tmpID);
				
				// collect results from partner aggregators
				collectResults(partnersRequestID, partnersRequestCount, result, false, theRequest, dependencyManager);
				invoker.releaseResources(partnersRequestID);
				
				if(logger.isDebugEnabled()) logger.debug("Remaining inputs: " + inputs);
			}
						
			
			DebugInfo debug = null;
			
			if(theRequest.isDebug()) {
				debug = new DebugInfo();
				result.addDebugInfo(debug);
			}
			
			for(DebugInfo d : result.getDebugInfos()) {
				for(DependencyUpdatedInfo info : d.updated) {
					//System.out.println(info + " - " + info.provider);
					AbstractInput from = info.provider;
					AbstractInput to = info.receiver;
					// find matching DebugInfos
					for(DebugInfo d1 : result.getDebugInfos()) {
						if(to.getExternalID().equals(d1.getInResponseTo())) {
							// add to "provided by" list
							d1.getProvidedBy().add(from);
						}
					}
				}
			}

			String tmpID = PerformanceInterceptor.event(EventType.START_FINALIZE_RESULT);
			AggregationResponse response = result.getTotalResult(debug, true);
			PerformanceInterceptor.event(EventType.FINISH_FINALIZE_RESULT, tmpID);
			
			return response;
		} catch (Throwable e) {
			result.setTotalResult(e);
			return result.getTotalResult(false);
		}
		
	}
	
	private void removeAllInputsByUID(List<? extends AbstractInput> toSearch, List<? extends AbstractInput> toRemove) {
		for(int i = 0; i < toSearch.size(); i ++) {
			for(int j = 0; j < toRemove.size(); j ++) {
				if(toSearch.get(i).getUniqueID().equals(toRemove.get(j).getUniqueID())) {
					toSearch.remove(i--);
					break;
				}
			}
		}
	}

	@WebMethod(exclude=true)
	public String addEventingInput(String topologyID, NonConstantInput input,
			AggregationRequest request, boolean startQueriers) throws Exception {
		return eventCoordinator.addEventingInput(topologyID, input, request, startQueriers);
	}

	@WebMethod(exclude=true)
	public void collectResults(final long requestID, final int requestCount, 
			final AggregationResponseConstructor resultContainer, 
			final boolean applyPrepQueries, final AggregationRequest requestObject, 
			final FlowManager dependencyManager) throws Exception {
		
		LinkedBlockingQueue<AggregationResponse> resultList = invoker.getResponseQueue(requestID);

		final AtomicInteger requestCounter = new AtomicInteger();
		final AtomicInteger resultCounter = new AtomicInteger();
		final AtomicReference<Throwable> exception = new AtomicReference<Throwable>();
		
		int numParallelResultCollectorThreads = 30;
		
		for(int i = 0; i < requestCount; i++) {
			final AtomicReference<AggregationResponse> ref = new AtomicReference<AggregationResponse>();
			Parallelization.warnIfNoResultAfter(ref, "Waiting for result " + (i+1) + " of " + requestCount, 5000);
			
			AggregationResponse r = null;
			if(util.test.isNullOrTrue(requestObject.getTimeout())) {
				r = resultList.poll(READ_TIMEOUT_MS, TimeUnit.MILLISECONDS);
			} else {
				r = resultList.poll(READ_TIMEOUT_VERYLONG_MS, TimeUnit.MILLISECONDS);
			}
			ref.set(r);

			if(r == null) {
				throw new Exception("Did not receive a result from queue within " + READ_TIMEOUT_MS + "ms!");
			} else {
				
				Runnable job = new Runnable() {
					public void run() {
						try {

							AggregationResponse r = ref.get();
							
							// set debug info
							DebugInfo debug = null;
							if(requestObject.isDebug()) {
								debug = new DebugInfo();
								resultContainer.addDebugInfo(debug);
							}
							
							if(r.isException()) {
								if(debug != null)
									debug.setDataSourceError(ref.get().getResult() + "");
								//throw (Exception)r.getResult();
							} else {

								RequestInput input = ((InvokerTask)r.getInResponseTo()).input;

								if(input != null && debug != null) {
									debug.setInResponseTo(input.getExternalID());
									debug.inResponseToInput = input;
									debug.inResponseToInputOriginal = (RequestInput)input.getFirstInListByExternalID(requestObject.getAllInputs());
								}
								
								InvocationResult invocationResult = (InvocationResult)r.getResult();
								Element partialResults = invocationResult.getResultAsElement();
								
								if(partialResults.getTagName().contains("Body")) {
									List<Element> children = util.xml.getChildElements(partialResults);
									if(children.size() > 0) {
										partialResults = children.get(0);
									}
								}
								//System.out.println("partial result: " + util.xml.toString(partialResults));

								if(debug != null)
									debug.setResultBeforeQuery(partialResults);

								// add response to container, execute queries etc.
								partialResults = resultContainer.addInvocationResponse(
										input, partialResults, debug, true);

								// update data dependencies in other sub-requests
								invocationResult.setResult(partialResults);
								List<DependencyUpdatedInfo> updated = dependencyManager.update(
										invocationResult, ((InvokerTask)r.getInResponseTo()).input);
								if(logger.isDebugEnabled()) logger.debug("updated: " + updated);
								
								// set debug info
								if(debug != null) {
									if(updated.size() > 0) {
										debug.hasDependency = true;
										debug.updated.addAll(updated);
									}
									debug.setResultAfterQuery(partialResults);
									debug.toNode = ((InvokerTask)r.getInResponseTo()).target;
								}
							}
						} catch (Throwable e) {
							logger.warn("Exception while collecting query results.", e);
							exception.set(e);
						}
						resultCounter.getAndIncrement();
					}
				};
				
				GlobalThreadPool.execute(job);
				requestCounter.incrementAndGet();
			}
			
			/* from time to time, we wait for all results from the background threads.. */
			if((i % numParallelResultCollectorThreads == (numParallelResultCollectorThreads - 1) 
					|| i == (requestCount - 1)) && resultCounter.get() < requestCounter.get()) {
				int diff = requestCounter.get() - resultCounter.get();
				AtomicReference<Object> done = new AtomicReference<Object>();
				Parallelization.warnIfNoResultAfter(ref, "AggregatorNode: Waiting for " + diff + 
						" results. So far, processed " + (i-diff+1) + " of " + requestCount + 
						" inputs", 5000);
				while(resultCounter.get() < requestCounter.get()) {
					Thread.sleep(300);
				}
				done.set(true);
			}
		}

		if(exception.get() != null) {
			if(exception.get() instanceof Exception)
				throw (Exception)exception.get();
			throw new Exception(exception.get());
		}
	}

	@WebMethod(exclude=true)
	public void initializeEventCoordinator(AggregationRequest request,
			String topologyType, String topologyID, boolean blocking) throws Exception {
		getEventCoordinator().initialize(request, topologyType, topologyID, blocking);
	}

	@Override
	@WebMethod(exclude=true)
	public void deploy(String url) throws Exception {
		super.deploy(url);
		Runnable r = new Runnable() {
			public void run() {
				try {
					Registry.getRegistryProxy().addAggregatorNode(AggregatorNode.this);
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
			}
		};
		GlobalThreadPool.executePeriodically(r, 60*1000, 5*1000);
	}
	
	@WebMethod(exclude=true)
	public StrategyChain getStrategyChain() {
		return strategyChain;
	}
	
	@WebMethod
	public void setStrategy(@WebParam(name="strategyChain") StrategyChain strategyChain) throws AggregatorException {
		synchronized (this.strategyChain) {
			try {
				this.strategyChain = StrategyChain.loadFromJaxbObject(strategyChain, this);
			} catch (Exception e) {
				throw new AggregatorException(e);
			}
		}
	}

	@WebMethod
	public void startMemoryProfiler() {
		memProfiler.start();
	}
	@WebMethod
	@WebResult(name="AggregatorPerformanceInfo", targetNamespace=Configuration.NAMESPACE)
	public AggregatorPerformanceInfo getPerformanceInfo(
			@WebParam(name="aggregatorEPR") EndpointReference aggregatorEPR, /* needed for compat. with same method in Gateway */
			@WebParam(name="doNotDeleteData") Boolean doNotDeleteData,
			@WebParam(name="detailed") Boolean detailed,
			@WebParam(name="forUsername") String forUsername) throws AggregatorException {
		if(detailed == null)
			detailed = false;
		AggregatorPerformanceInfo info = new AggregatorPerformanceInfo();
		info.setUsedMemory(memProfiler.getMaximumMemUsage());
		info.setRequestQueueLength(queues.getQueueLength());
		info.setWindowSizeCPU(memProfiler.getMeasurementWindowInMilliseconds());
		info.setMaxUsedCPUOverWindow(memProfiler.getMaximumCPU());
		info.setCurrentUsedCPU(memProfiler.getCurrentCPU());
		info.setOpenFiles(memProfiler.getMaximumOpenFiles());

		int intervalSecs = 40;

		info.setStreamDataRate(new LinkedList<StreamDataRate>());
		Map<String,Double> sRates = eventCoordinator.getEventManager()
				.getMeasurer().getKBytesPerSecondAsMap(intervalSecs);
		Map<String,Double> sFreqs = eventCoordinator.getEventManager()
				.getMeasurer().getEventsPerMinuteAsMap();
		for(String streamID : sRates.keySet()) {
			StreamDataRate r = new StreamDataRate();
			r.setDataRate(sRates.get(streamID));
			r.setEventFreq(sFreqs.get(streamID));
			if(detailed) {
				EventStream s = eventCoordinator.getEventManager().getEventStream(streamID);
				IdentityHashMap<Object,Object> visitedObjects = new IdentityHashMap<Object,Object>();
				// add the contexts to the list of visited objects, because
				// after the recursive memory calculation algorithm has reached
				// the context object(s), it should not continue adding the size
				// of all objects that are reachable from the context(s).
				for(Object ctx : s.contexts)
					visitedObjects.put(ctx, null);
				if(s != null) {
					r.setBufferSize((double)eventCoordinator.getEventManager().getEventBufferSize(s, visitedObjects));
				}
				visitedObjects = null; // for GC'ing
				System.gc();
			}
			NonConstantInput input = (EventingInput)eventCoordinator
					.getEventManager().getInputByEventStreamID((String)streamID);
			if(input == null)
				logger.warn("Could not find input of eventStreamID " + streamID);
			else {
				r.setStream(new InputWrapper(input));
				info.getStreamDataRate().add(r);
			}
		}
		
		info.setInputDataRate(new LinkedList<InputDataRate>());
		Map<EventQuerier,Double> iRates = eventCoordinator
				.getQueryInputsDataRate().getKBytesPerSecondAsMap(intervalSecs);
		for(EventQuerier q : iRates.keySet()) {
			InputDataRate r = new InputDataRate();
			r.setDataRate(iRates.get(q));
			if(q.getInput().request != null) {
				if(q.getInput().request instanceof AggregationRequest) {
					AggregationRequest ar = (AggregationRequest)q.getInput().request;
					r.setRequestID(ar.getRequestID());
				}
			}
			r.setInputID(q.getInput().getExternalID());
			if(r.getRequestID() == null)
				logger.warn("Could not determine requestID of input " + q.getInput() + " of request " + q.getInput().request);
			info.getInputDataRate().add(r);
		}

		info.setInterAggregatorDataRate(new LinkedList<InterAggregatorsDataRate>());
		Map<Pair<AggregatorNode,AggregatorNode>,Double> aRates = eventCoordinator
				.getPartnerAggrTransferRate().getKBytesPerSecondAsMap(intervalSecs);
		for(Pair<AggregatorNode,AggregatorNode> pair : aRates.keySet()) {
			InterAggregatorsDataRate r = new InterAggregatorsDataRate();
			r.setDataRate(aRates.get(pair));
			r.setAggregatorURL1(pair.getFirst().getEPR().getAddress());
			r.setAggregatorURL2(pair.getSecond().getEPR().getAddress());
			info.getInterAggregatorDataRate().add(r);
		}

		info.setInterAggregatorDataRate(new LinkedList<InterAggregatorsDataRate>());
		Map<String,Double> uRates = eventCoordinator
				.getUserEventRate().getKBytesPerSecondAsMap(intervalSecs);
		Map<String,Double> uFreqs = eventCoordinator
				.getUserEventRate().getKBytesPerSecondAsMap(intervalSecs);
		for(String username : uRates.keySet()) {
			if(forUsername == null || forUsername.equals(username)) {
				UserDataRate r = new UserDataRate();
				r.setDataRate(uRates.get(username));
				r.setEventFreq(uFreqs.get(username));
				r.setUsername(username);
				info.getUserDataRate().add(r);
			}
		}
		
		if(doNotDeleteData != null && !doNotDeleteData.booleanValue()) {
			RegistryProxy.resetCache();
			System.gc();
			System.gc();
			try {
				strategyChain.resetCache();
			} catch (Exception e) {
				throw new AggregatorException(e);
			}
		}
		return info;
	}
	
	protected Runnable getTerminateTask(TerminateRequest params) {
		return new Runnable() {
			@Override
			public void run() {
				try {
					Registry.getRegistryProxy().removeAggregatorNode(AggregatorNode.this);
				} catch (Exception e) {}
			}
		};
	}

	@Override
	@WebMethod(exclude=true)
	public String toString() {
		return "[A " + getEPR().getAddress() +
				//(epr.getServiceName() != null ? (",s:" + epr.getServiceName()) : "") + 
				"]";
	}
	@WebMethod(exclude=true)
	public DataServiceMonitor getMonitor() {
		return monitor;
	}
	@WebMethod(exclude=true)
	public EventingQueryCoordinator getEventCoordinator() {
		return eventCoordinator;
	}
	@WebMethod(exclude=true)
	public Invoker getInvoker() {
		return invoker;
	}

	@WebMethod(exclude=true)
	public int hashCode() {
		return getEPR().hashCode();
	}
	@WebMethod(exclude=true)
	public boolean equals(Object o) {
		boolean equals = getEPR().equals(o);
		return equals;
	}

}
