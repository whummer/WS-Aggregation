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

package at.ac.tuwien.infosys.aggr.events.query;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.namespace.QName;

import org.apache.log4j.Logger;
import org.w3c.dom.Element;

import at.ac.tuwien.infosys.aggr.AggregationClient;
import at.ac.tuwien.infosys.ws.WebServiceClient;
import at.ac.tuwien.infosys.aggr.events.query.EventStream;
import at.ac.tuwien.infosys.aggr.flow.FlowManager;
import at.ac.tuwien.infosys.aggr.flow.FlowManager.InputDataDependency;
import at.ac.tuwien.infosys.aggr.flow.FlowNode.DependencyUpdatedInfo;
import at.ac.tuwien.infosys.ws.AbstractNode;
import at.ac.tuwien.infosys.aggr.node.AggregatorNode;
import at.ac.tuwien.infosys.aggr.node.DataServiceNode;
import at.ac.tuwien.infosys.aggr.node.Registry;
import at.ac.tuwien.infosys.aggr.performance.EventTransferRateMeasurer;
import at.ac.tuwien.infosys.aggr.performance.GarbageCollector;
import at.ac.tuwien.infosys.aggr.proxy.AggregatorNodeProxy;
import at.ac.tuwien.infosys.aggr.request.AbstractInput;
import at.ac.tuwien.infosys.aggr.request.AggregationRequest;
import at.ac.tuwien.infosys.aggr.request.AggregationResponseConstructor;
import at.ac.tuwien.infosys.aggr.request.ConstantInput;
import at.ac.tuwien.infosys.aggr.request.EventingInput;
import at.ac.tuwien.infosys.aggr.request.InputTargetExtractor;
import at.ac.tuwien.infosys.ws.request.InvocationResult;
import at.ac.tuwien.infosys.aggr.request.NonConstantInput;
import at.ac.tuwien.infosys.aggr.request.RequestInput;
import at.ac.tuwien.infosys.aggr.request.AbstractInput.DependencyUpdateMode;
import at.ac.tuwien.infosys.aggr.request.WAQLQuery.PreparationQuery;
import at.ac.tuwien.infosys.aggr.strategy.Topology;
import at.ac.tuwien.infosys.aggr.strategy.TopologyInitializer;
import at.ac.tuwien.infosys.aggr.monitor.ModificationNotification;
import at.ac.tuwien.infosys.aggr.monitor.MonitoringSpecification;
import at.ac.tuwien.infosys.util.Configuration;
import at.ac.tuwien.infosys.ws.EndpointReference;
import at.ac.tuwien.infosys.aggr.util.Invoker;
import at.ac.tuwien.infosys.aggr.util.RequestAndResultQueues;
import at.ac.tuwien.infosys.util.Util;
import at.ac.tuwien.infosys.aggr.util.RequestAndResultQueues.RequestWorker;
import at.ac.tuwien.infosys.util.coll.Pair;
import at.ac.tuwien.infosys.util.par.GlobalThreadPool;
import at.ac.tuwien.infosys.util.xml.ElementWrapper;
import at.ac.tuwien.infosys.util.xml.XMLUtil;

public class EventingQueryCoordinator implements EventQuerier.EventQueryListener, RequestWorker<ModificationNotification, Object> {

	private static final Logger logger = Util.getLogger(EventingQueryCoordinator.class);
	private static boolean SEND_NOTIFICATIONS_DIRECTLY = true;
	private static final Object dummyResultObject = new Object();
	private static final long CLIENT_RESULT_CACHE_SECONDS = 60*60;
	public static final QName HEADER_NAME_EVENT_FORWARDED = new QName(Configuration.NAMESPACE, "eventForwarded");
	public static final String LOCALNAME_FORWARD_EVENT_TO = "forwardTo";
	public static final QName HEADER_NAME_FORWARD_EVENT_TO = new QName(Configuration.NAMESPACE, LOCALNAME_FORWARD_EVENT_TO);
	private static final Element HEADER_EVENT_FORWARDED = new XMLUtil().toElementSafe("<tns:eventForwarded xmlns:tns=\"" + Configuration.NAMESPACE + "\">true</tns:eventForwarded>");
	private static WebServiceClient clientForEventForwarding;
	
	/** stores event data rates by eventing querier */
	private EventTransferRateMeasurer<EventQuerier> queryInputsDataRate = new EventTransferRateMeasurer<EventQuerier>();
	/** stores event data rates between pairs of aggregators */
	private EventTransferRateMeasurer<Pair<AggregatorNode,AggregatorNode>> partnerAggrTransferRate = new EventTransferRateMeasurer<Pair<AggregatorNode,AggregatorNode>>();
	/** stores event data rates by creator username */
	private EventTransferRateMeasurer<String> userEventRate = new EventTransferRateMeasurer<String>();

	private final RequestAndResultQueues<ModificationNotification, Object> eventQueue = new RequestAndResultQueues<ModificationNotification, Object>(this/*, 5, 50*/);
	private final EventStoresManager eventManager;
	private AggregatorNode owner;
	private Util util = new Util();
	/** topologyID -> topology object (exists for all aggregators involved in a topology) */
	private final Map<String, Topology> topologies = new HashMap<String, Topology>();
	/** topologyID -> query execution state (only exists for the master aggregator) */
	private final Map<String, QueryState> states = new HashMap<String, QueryState>();
	/** monitoringID -> input(s) for the monitored data service(s) */
	private final Map<String, List<AbstractInput>> monitoringIDsToInputs = new HashMap<String, List<AbstractInput>>();
	/** clientID --> last result returned to client (controlled by custom GarbageCollector, see below) */
	private final Map<String, ActiveQueryResult> clientResults = new HashMap<String, ActiveQueryResult>();
	/** eventStreamID --> temp buffers for event streams being passed from one aggregator to another */
	private final Map<String, EventStreamTempBuffer> eventstreamTempBuffers = new HashMap<String, EventStreamTempBuffer>();
	/** eventStreamID --> EPRs of aggregators that events are forwarded to */
	private final Map<String, List<EndpointReference>> eventstreamForwardTo = new HashMap<String, List<EndpointReference>>();
	
	
	public static class EventStreamTempBuffer {
		List<ModificationNotification> tempEventBufferOld = new LinkedList<ModificationNotification>();
		List<ModificationNotification> tempEventBufferNew = new LinkedList<ModificationNotification>();
	}
	
	public static class QueryState {
		private AggregationRequest request;
		private String topologyType;
		private FlowManager flowManager;
		private final List<NonConstantInput> finishedOrActiveInputs = new LinkedList<NonConstantInput>();
		private final Map<EventQuerier,AbstractInput> storeToInput = new HashMap<EventQuerier, AbstractInput>();
		private final Set<EventQuerier> terminationQueryStores = new HashSet<EventQuerier>();
		private EndpointReference notifyTo;
		private AggregationResponseConstructor results;
		private long lastNotifyTime = 0;
		private Set<String> monitoringIDs = new HashSet<String>();
		
		public boolean doNotifyNow() {
			Long time = request.getMinNotifyInterval();
			return time == null || (System.currentTimeMillis() - time) > lastNotifyTime;
		}
	}

	public EventingQueryCoordinator(AggregatorNode owner) {
		this.owner = owner;
		eventManager = new EventStoresManager(owner);
	}

	public String initialize(AggregationRequest request, String topologyType, 
			final String topologyID, boolean blocking) throws Exception {
		if(states.containsKey(topologyID)) {
			logger.debug("Query state for topology ID " + topologyID + " already exists");
			return topologyID;
		}
		QueryState s = new QueryState();
		s.request = request;
		s.topologyType = topologyType;
		FlowManager flow = new FlowManager(request);
		s.flowManager = flow;
		s.notifyTo = request.getMonitor() != null ? request.getMonitor().getEpr() : null;
		s.results = new AggregationResponseConstructor(request, true);
		states.put(topologyID, s);
		
		if(blocking) {
			update(topologyID);
		} else {
			final AtomicInteger temp = new AtomicInteger(0);
			GlobalThreadPool.execute(new Runnable() {
				public void run() {
					try {
						update(topologyID);
						temp.incrementAndGet();
					} catch (Exception e) {
						logger.warn("Unexpected error.", e);
					}
				}
			});
			GlobalThreadPool.execute(new Runnable() {
				public void run() {
					for(int i = 0; i < 15; i ++) {
						try {
							Thread.sleep(10000);
							if(temp.get() > 0)
								break;
							if(temp.get() <= 0)
								logger.warn("No update was possible within " + (i*10) + " seconds..!");
						} catch (Exception e) {
							logger.warn("Unexpected error.", e);
						}
					}
				}
			});
		}
		
		return topologyID;
	}

	public void addMonitoringIDforInput(String monitoringID, AbstractInput input) {
		/* register this monitoring subscription ID for 
		 * later lookup of the corresponding AbstractInput.. */
		if(!monitoringIDsToInputs.containsKey(monitoringID))
			monitoringIDsToInputs.put(monitoringID, new LinkedList<AbstractInput>());
		monitoringIDsToInputs.get(monitoringID).add(input);
		if(logger.isDebugEnabled()) logger.debug("Adding monitoringID " + monitoringID + " to " + owner);
	}

	/** 
	 * called when a new result is received from the input event queue. 
	 * NOTE: 	this method must NEVER return a null result (hence we return a non-null 
	 * 			dummy result Object), because the request queue expects a non-null result 
	 * 			to indicate that the procecssing of this method has finished!
	 **/
	@Override
	public Object handleRequest(ModificationNotification event) {

		try {
			String eventStreamID = event.getEventStreamID();
			synchronized (eventstreamTempBuffers) {
				if(eventstreamTempBuffers.containsKey(eventStreamID)) {
					if(event.getHeader(HEADER_NAME_EVENT_FORWARDED) != null) {
						//System.out.println("adding event to OLD temp buffer: " + event);
						eventstreamTempBuffers.get(eventStreamID).tempEventBufferOld.add(event);				
					} else {
						//System.out.println("adding event to NEW temp buffer: " + event);
						eventstreamTempBuffers.get(eventStreamID).tempEventBufferNew.add(event);				
					}
					return dummyResultObject;
				}
			}
			
			try {
				eventManager.handleEvent(event);
			} catch (Exception e1) {
				//String streamID = event.getEventStreamID() != null ? " for event stream '" + event.getEventStreamID() + "'" : "";
				//logger.info("Unable to handle event" + streamID + ":" + e1);
				return e1;
			}

			WebServiceClient c = getClientForEventForwarding();
			synchronized (eventstreamForwardTo) {
				if(eventstreamForwardTo.containsKey(eventStreamID)) {
					event.addHeader(HEADER_EVENT_FORWARDED);
					for(EndpointReference forwardTo : eventstreamForwardTo.get(eventStreamID)) {
						synchronized (c) {
							try {
								c.changeTargetAddress(forwardTo.getAddress());
								RequestInput req = new RequestInput(util.xml.toElement(event));
								req.addSoapHeader(HEADER_EVENT_FORWARDED);
								c.invoke(req.getRequest());	
							} catch (Exception e) {
								logger.info("Unable to forward event. " + e);
							}
						}
					}
				}
			}
			
		} catch (Exception e) {
			logger.error("Error handling event: " + e);
			return e;
		}
		
		return dummyResultObject;
	}

	/**
	 * This method is called when a new event from an eventing data service is received.
	 * The event will be put to the request queue and handled by handleRequest(...) later.
	 */
	public void handleEvent(ModificationNotification event) throws Exception {
		Object result = null;
		try {
			long eventID = event.getIdentifier();
			eventQueue.putRequest(event);
			result = eventQueue.takeResponse(eventID);
			eventQueue.releaseResources(eventID);
		} catch (Exception e) {
			logger.info("Error when processing event: " + e);
		}
		if(result instanceof Exception) {
			logger.info("Error when handling event: " + result);
			throw (Exception)result;
		}
	}

	/**
	 * This method is called when either:
	 *  - a new result from a partner aggregator is received by the master aggregator, OR
	 *  - the master aggregator itself adds the result (local call from within this class)
	 */
	public void addResultFromInputAndNotifyClient(String topologyID, String inputUID, Element result) throws Exception {
		Topology t = topologies.get(topologyID);
		QueryState s = states.get(topologyID);
		if(t == null || s == null)
			return;
		NonConstantInput input = (NonConstantInput)t.getInputByUID(inputUID);
		synchronized (s.results) {
			AggregationResponseConstructor r = s.results;
			r.addInvocationResponse(input, result, null, false);
		}
		synchronized (s) {
			if(s.doNotifyNow()) {
				notifyListeningClient(topologyID);
			}
		}
	}
	
	/**
	 * This method is called when a new result from an XQuery window query is received.
	 */
	@Override
	public void onResult(EventQuerier querier, Element newResult) {
		
		if(logger.isDebugEnabled()) logger.debug("new window query result: " + newResult);
		//System.out.println("result " + newResult + ", feature " + querier.getInput() + ", querier " + querier + ", input " + querier.getInput() + ", query " + querier.getOriginalQuery().replace("\n", " "));
		//String eventStreamID = eventManager.getIDForEventStore(querier);
		
		ElementWrapper wrapped = new ElementWrapper(newResult);
		queryInputsDataRate.addTransmittedData(querier, wrapped);
		
		String topologyID = eventManager.getTopologyID(querier);
		Topology topology = topologies.get(topologyID);
		
		//check if we are the master aggregator or need to forward the result
		if(topology != null) {
			AggregatorNode master = topology.getMasterAggregator();
			if(!master.equals(owner)) {
				try {
					forwardEventToPartner(master, topologyID, querier.getInput().getUniqueID(), wrapped);
				} catch (Exception e) {
					logger.info("Unable to forward eventing result to partner aggregator.", e);
				}
				return;
			}
		}

		QueryState state = states.get(topologyID);
		if(state == null || topology == null) {
			logger.info("Could not find query state for topologyID " + topologyID);
			return;
		}
		
		/** at this point we know that this node is the master aggregator. */
		
		/* add event to user statistics */
		userEventRate.addTransmittedData(state.request.getCreatorUsername(), wrapped);
		
		/* check if this result arrives from an event store with a termination query */
		if(state.terminationQueryStores.contains(querier)) {
			
			//TODO: possibly we need to forward the termination request to a partner aggregator!!
			
			logger.info("Termination query fulfilled for eventing input. Freeing resources...");
			// now terminate this continuos event query and free all resources
			state.terminationQueryStores.remove(querier);
			state.storeToInput.remove(querier);
			try {
				eventManager.closeAndRemoveStore(querier);
			} catch (Exception e) {
				logger.warn("Termination query yields 'true', but could not close the corresponding event store.", e);
			}
			return;
		}

		AbstractInput input = null;
		synchronized (state.storeToInput) {
			input = state.storeToInput.get(querier);
		}
		if(input == null) {
			logger.warn("Closing event querier, because input not found in map: " + state.storeToInput + " for querier: " + querier + " and topologyID " + topologyID);
			synchronized (states) {
				for(String tID : states.keySet()) {
					QueryState s = states.get(tID);
					if(s.storeToInput.containsKey(querier))
						logger.warn("!! However, querier found in " + s.storeToInput + " for topologyID " + tID);
				}
			}
			try {
				eventManager.closeAndRemoveStore(querier);
			} catch (Exception e) {
				logger.error("Unable to close event store", e);
			}
		}
		
		FlowManager flowMgr = state.flowManager;
		
		try {

			addResultFromInputAndNotifyClient(topologyID, input.getUniqueID(), newResult);
			
			/** notify all aggregators depending on results from this input */
			
			for(InputDataDependency d : state.request.getInputsStaticallyDependentFrom(input)) {
				AggregatorNode responsibleAggr = topology.getResponsibleAggregator(d.to);
				if(responsibleAggr == null) {
					logger.debug("Unable to determine aggregator responsible for a depending input. This MAY be unproblematic, IF the input has not yet been activated.");
				} else {
					if(!responsibleAggr.equals(owner)) {
						forwardEventToPartner(responsibleAggr, topologyID, input.getUniqueID(), wrapped);
					}
				}
			}
			
		} catch (Exception e) {
			logger.warn("Unable to add result and notify listeners.", e);
		}
		
		try {
			
			/** Update flow manager and possibly activate new inputs. */
			
			InvocationResult result = new InvocationResult(newResult);
			List<DependencyUpdatedInfo> updated = flowMgr.update(result, input);
			boolean topologyUpdated = false;
			for(DependencyUpdatedInfo upd : updated) {
				//System.out.println("Updated dependency: " + upd.provider.id + " --> " + upd.receiver.id + " - " + upd.xpath);
				
				if(upd.receiver.getUpdateDependencies() == DependencyUpdateMode.always || 
						!state.finishedOrActiveInputs.contains(upd.receiver)) {
					if(upd.receiver instanceof EventingInput) {
						EventingInput newInput = (EventingInput)upd.receiver;
						//System.out.println("\nisIndependent: " + flowMgr.isIndependent(newInput) + " - " + newInput);
						if(flowMgr.isIndependent(newInput)) {
							List<AbstractInput> newInputsGenerated = flowMgr.getNewInputsWithInjectedValues(newInput);
							//System.out.println("newInputsGenerated" + newInputsGenerated);
							for(AbstractInput newInputGenerated : newInputsGenerated) {
								newInput = (EventingInput)newInputGenerated;
								topologyUpdated = true;
								logger.info("Activating new input " + newInput);
								addEventingInput(topologyID, newInput, state.request);
								state.finishedOrActiveInputs.add(newInput);
								AggregationRequest r = new AggregationRequest(state.request);
								r.getQueries().clear();
								r.getInputs().clearInputs();
								r.getInputs().addInput(newInput);
								r.getQueries().addPreparationQuery(state.request.getQueries().getPreparationQuery(newInput));
								topology.addTargetServiceRequest(owner, 
										(DataServiceNode)InputTargetExtractor.extractDataSourceNode(newInput), r, false);
							}
						}
					}
				}
			}
			if(topologyUpdated) {
				notifyPartnersOfTopologyChange(topology);
			}
			
		} catch (Exception e) {
			logger.warn("Unable to update flow manager and activate new inputs: " + e);
			logger.info("", e);
		}
	}
	
	private void forwardEventToPartner(AggregatorNode target, String topologyID, String inputUID, ElementWrapper newResult) throws Exception{

		new AggregatorNodeProxy(target.getEPR(), true).onResult(topologyID, inputUID, newResult.element);
		partnerAggrTransferRate.addTransmittedData(
				new Pair<AggregatorNode,AggregatorNode>(this.owner,target), newResult);

	}

	private void update(String topologyID) throws Exception {

		
		QueryState state = states.get(topologyID);
		Topology topology = topologies.get(topologyID);

		if(state == null || 
				(topology != null && !owner.equals(topology.getMasterAggregator()))) {
			if(logger.isDebugEnabled()) logger.debug("Only the master aggregator may update the topology. Returning. " + owner);
			return;
		}

		AggregationRequest request = state.request;

		// temporarily set monitor to NULL
		MonitoringSpecification monitorSpec = request.getMonitor();
		request.setMonitor(null);
		
		FlowManager flow = state.flowManager;
		
		
		Map<AbstractNode,List<AggregationRequest>> fixedMappings = new HashMap<AbstractNode, List<AggregationRequest>>();
		List<AbstractInput> independents = null;
		synchronized (flow) {
			independents = flow.filterInputsWithoutDependencies(
					request.getAllInputs(), true, NonConstantInput.class); // TODO: is the insertOnlyNewResults=true parameter correct here?
		}
		if(logger.isDebugEnabled()) logger.debug("Independent requests: " + independents);

		// 1. calculate and update topology
		
		boolean updateTopology = topology == null;
		synchronized (state.finishedOrActiveInputs) {
			updateTopology |= !state.finishedOrActiveInputs.containsAll(independents);			
		}

		if(updateTopology) {
			List<NonConstantInput> inputs = new LinkedList<NonConstantInput>();
			for(AbstractInput i : independents) {
				if(i instanceof NonConstantInput) 
					inputs.add((NonConstantInput)i);
			}
			TopologyInitializer opt = new TopologyInitializer();
			topology = opt.createTopology(state.topologyType, fixedMappings, 
					request, Registry.getRegistryProxy().getAggregatorNodes(), 
					inputs, new LinkedList<InputDataDependency>(), (AggregatorNode)owner);
			topology.setSendNotificationsDirectly(SEND_NOTIFICATIONS_DIRECTLY);
			
			topology.setTopologyID(topologyID);
			topologies.put(topologyID, topology);

			// 1.1. Notify all involved aggregators
			notifyPartnersOfTopologyChange(topology);
			
		}
		if(logger.isDebugEnabled()) logger.debug(topology);
		
		long invokerTaskID = owner.getInvoker().getNewRequestID();
		int invokerTasks = 0;
		
		if(logger.isDebugEnabled()) logger.debug("Target service requests stored in topology: " + topology.getTargetServiceRequests());
		for(AggregatorNode a : topology.getTargetServiceRequests().keySet()) {
			Map<DataServiceNode,LinkedList<AggregationRequest>> requests = topology.getTargetServiceRequests(a);
			for(DataServiceNode n : requests.keySet()) {
				for(AggregationRequest r : new LinkedList<AggregationRequest>(requests.get(n))) {
					AbstractInput input = r.getSingleInput();
					try {
						//logger.debug("Finished or active requests/subscriptions: " + state.finishedOrActiveRequests);
						boolean contained = false;
						synchronized (state.finishedOrActiveInputs) {
							contained = state.finishedOrActiveInputs.contains(input);
						}

						if(!contained) {
							if(input instanceof RequestInput) {
								
								if(!((RequestInput)input).hasMonitoringInterval()) {
									/* no monitoring input 
									 * --> treat as regular input and get result straight away. */
									
									//TODO: check if we are the aggregator responsible for this! 
									// (maybe input needs to be forwarded according to the topology!)
									AbstractNode target = InputTargetExtractor.extractDataSourceNode((RequestInput)input);
									owner.getInvoker().addRequest(new Invoker.InvokerTask(invokerTaskID, target,
											(RequestInput)input, util.test.isNullOrTrue(r.getTimeout())));
									invokerTasks ++;
									
								} else {
	
									/* register subscription with responsible aggregator node */
									
									
									r.setMonitor(null);
									r.setTopologyID(topologyID);

									//TODO: check if we are the aggregator responsible for this! 
									// (maybe input needs to be forwarded according to the topology!)
									
									String monitoringID = null;
									if(a.equals(owner)) {
										String eventStreamID = owner.addEventingInput(topologyID, 
												(NonConstantInput)input, r, true);
										monitoringID = owner.monitorData(r, eventStreamID);
									} else {
										monitoringID = new AggregatorNodeProxy(a).monitorData(r);
									}
									logger.info("Added monitoring input with monitoring ID " + monitoringID);

									state.monitoringIDs.add(monitoringID);
									
									if(!monitoringIDsToInputs.containsKey(monitoringID))
										monitoringIDsToInputs.put(monitoringID, new LinkedList<AbstractInput>());
									monitoringIDsToInputs.get(monitoringID).add((RequestInput)input);
									
								}
	
							} else if(input instanceof EventingInput) {

								//TODO: check if we are the aggregator responsible for this! 
								// (maybe input needs to be forwarded according to the topology!)
								owner.addEventingInput(topologyID, (EventingInput)input, r, true);

							} else if(input instanceof ConstantInput) {
								state.results.addInvocationResponse(input, 
										input.getTheContentAsElement(), null, false);
							} else {
								logger.info("Unexpected input type: " + input);
								continue;
							}
							synchronized (state.finishedOrActiveInputs) {
								state.finishedOrActiveInputs.add((NonConstantInput)input);
							}
						} else {
							if(logger.isTraceEnabled()) logger.trace("Input " + input + " already in list of finished or active requests: " + state.finishedOrActiveInputs);
						}
					} catch (Exception e) {
						ByteArrayOutputStream bos = new ByteArrayOutputStream();
						e.printStackTrace(new PrintStream(bos));
						logger.info("Exception during update of eventing query.", e);
						state.results.addInvocationResponse(input, util.xml.toElement("<error>" + 
								bos.toString() + "</error>"), null, false);
					}
				}
			}
		}

		/* collect all results of request inputs handled by the owner aggregator. */
		owner.collectResults(invokerTaskID, invokerTasks, 
				state.results, true, state.request, state.flowManager);

		/* set monitor to previous value */
		request.setMonitor(monitorSpec);

		if(invokerTasks > 0) {
			/* recurse! */
			update(topologyID);
		}
	}

	public Map<String,Set<NonConstantInput>> getTopologyInputsForEventStream(String eventStreamID) {
		return eventManager.getTopologyInputsForEventStream(eventStreamID);
	}

	private void notifyPartnersOfTopologyChange(Topology topology) throws Exception {
		Element updateRequest = null;
		for(AggregatorNode a : topology.getAllAggregators()) {
			if(!a.equals(owner)) {
				if(updateRequest == null) {
					StringBuilder b = new StringBuilder();
					b.append("<tns:updateTopology xmlns:tns=\"" + Configuration.NAMESPACE + 
							"\"><topologyID>" + topology.getTopologyID() + "</topologyID><updates>");
					b.append(util.xml.toString(topology));
					b.append("</updates></tns:updateTopology>");
					updateRequest = util.xml.toElement(b.toString());
				}
				WebServiceClient client = WebServiceClient.getClient(a.getEPR(), true);
				client.invoke(new RequestInput(updateRequest).getRequest());
			}
		}
	}
	
	private void notifyListeningClient(String topologyID) throws Exception {
		final QueryState state = states.get(topologyID);
		if(state == null) {
			logger.warn("Query state not found for topologyID " + topologyID);
			return;
		}
		final ModificationNotification notification = new ModificationNotification();
		notification.setRequestID(state.request.getRequestID());
		
		Element resultEl = null;
		synchronized (state.results) {
			if(state.request.isIncremental())
				resultEl = state.results.getIncrementResultElement(true);
			else
				resultEl = state.results.getTotalResultElement(true);
		}
		
		if(logger.isDebugEnabled()) logger.debug("Notify listening client of new result: " + resultEl);
		if(resultEl != null) {
			notification.getData().add(resultEl);
			if(state.notifyTo != null) {
				state.lastNotifyTime = System.currentTimeMillis();
				GlobalThreadPool.execute(new Runnable() {
					public void run() {
						try {
							WebServiceClient.getClient(state.notifyTo, true).invoke(
								new RequestInput(util.xml.toElement(notification)).getRequest());
						} catch (Exception e) {
							logger.info("Could not notify client " + state.notifyTo + ". The client will not receive any more updates.");
							state.notifyTo = null;
						}
					}
				});
			}
		}
	}

	public String addEventingInput(String topologyID, NonConstantInput input, 
			AggregationRequest request) throws Exception {
		return addEventingInput(topologyID, input, request, true);
	}
	public String addEventingInput(String topologyID, NonConstantInput input, 
			AggregationRequest request, boolean startQueriers) throws Exception {
		synchronized (eventManager) {
			List<PreparationQuery> prepQueries = request.getQueries().getPreparationQueries(input);
			List<PreparationQuery> termQueries = request.getQueries().getTerminationQueries(input);
			
			/* 
			 * The reason for limiting the buffer size here is: 
			 * Under the condition that no window-based preparation query has been defined (e.g., for 
			 * monitored data sources), a simplified default window-based preparation query is 
			 * created (see below). The query has a tumbling window which simply returns each event 
			 * one-by-one from the input sequence; hence, theoretically the query requires only a 
			 * single buffer slot. However, we provide a bit more that 1 buffer slot 
			 * (namely 20), simply to be on the safe side for the underlying query engine..
			 */
			int maxBufferSize = (prepQueries.isEmpty() || 
					!isWindowQuery(prepQueries.get(0).getValue())) ? 20 : -1;
			
			EventStream eventStream = eventManager.getEventStreamForInput(input, topologyID, maxBufferSize);
			
			QueryState state = states.get(topologyID);
			
			if(startQueriers) {
				synchronized (state.storeToInput) {
					/* add preparation queries for the given input */
					for(PreparationQuery prep : prepQueries) {
						String query = prep.getValue();
						query = ensureWindowQuery(query);
						EventQuerier store = eventManager.addQuerier(topologyID, query, input, eventStream);
						state.storeToInput.put(store, input);
						store.addListener(this);
					}
					/* if the request contains no preparation query for the input,
					 * add an event querier with an empty query string (''). The initializer of the 
					 * event querier will automatically select a standard query in this case. */
					if(prepQueries.isEmpty()) {
						EventQuerier store = eventManager.addQuerier(topologyID, "", input, eventStream);
						state.storeToInput.put(store, input);
						store.addListener(this);
					}
					/* add termination queries for the given input */
					for(PreparationQuery term : termQueries) {
						String query = term.getValue();
						query = ensureWindowQuery(query);
						EventQuerier store = eventManager.addQuerier(topologyID, query, input, eventStream);
						state.storeToInput.put(store, input);
						state.terminationQueryStores.add(store);
						store.addListener(this);
					}
				}
			}

			return eventStream.eventStreamID;
		}
	}
	
	private String ensureWindowQuery(String query) {
		if(!isWindowQuery(query))
			query = wrapQueryInTumblingWindow(query);
		return query;
	}
	private String wrapQueryInTumblingWindow(String query) {
		return "for tumbling window $tmpWin123 in $input\n "+
				"start when true()\n "+
				"end when true()\n " +
				"return $tmpWin123/(\n" + query + "\n)";
	}
	private boolean isWindowQuery(String query) {
		return query.contains("window") && 
				(query.contains("tumbling") || query.contains("sliding"));
	}
	
	public void finalizeAndRemoveTempBuffers(String eventStreamID) throws Exception {
		synchronized (eventstreamTempBuffers) {
			EventStreamTempBuffer tmp = eventstreamTempBuffers.remove(eventStreamID);

			eventstreamTempBuffers.remove(eventStreamID);
			for(ModificationNotification event : tmp.tempEventBufferOld) {
				handleEvent(event);
			}
			tmp.tempEventBufferOld.clear();
			for(ModificationNotification event : tmp.tempEventBufferNew) {
				handleEvent(event);
			}
			tmp.tempEventBufferNew.clear();
		}
	}

	public void updateTopology(String topologyID, Object updates) throws Exception {
		if(!topologies.containsKey(topologyID)) {
			if(logger.isDebugEnabled()) logger.debug("Adding topology with ID " + topologyID + ", which is so far unknown to aggregator " + owner + " - " + updates);
		}
		Topology topology = null;
		if(updates instanceof Element) {
			List<Element> children = util.xml.getChildElements((Element)updates);
			Element firstChild = children.get(0);
			if(children.size() > 0 && firstChild.getLocalName().equalsIgnoreCase("Topology")) {
				topology = util.xml.toJaxbObject(Topology.class, firstChild);
			}
		}
		if(updates instanceof Topology) {
			topology = (Topology)updates;
		}
		if(topology != null) {
			if(topology.getTopologyID() == null || topology.getTopologyID().isEmpty())
				topology.setTopologyID(topologyID);
			topologies.put(topologyID, topology);
			if(topology.getMasterAggregator().equals(owner)) {
				List<AggregatorNode> aggrs = topology.getAllAggregators();
				logger.debug("Notifying all " + aggrs.size() + " partner aggregators of new topology.");
				for(AggregatorNode n : aggrs) {
					if(!n.equals(owner)) {
						AggregatorNodeProxy aggr = new AggregatorNodeProxy(n.getEPR(), true);
						aggr.updateTopology(topologyID, topology);
					}
				}
				AggregatorNodeProxy gateway = new AggregatorNodeProxy(Registry.getRegistryProxy().getGateway().getEPR(), true);
				gateway.updateTopology(topologyID, topology);
				logger.debug("Notified gateway of new topology.");
			}
		}
	}
	
	public void destroyTopology(String topologyID) throws Exception {
		if(logger.isInfoEnabled()) logger.info("Destroying topology " + topologyID);
		Topology t = null;
		try {
			QueryState state = states.remove(topologyID);
			if(state != null) {
				List<NonConstantInput> inputsCopy = new LinkedList<NonConstantInput>();
				for(AbstractInput i : state.request.getAllInputs()) {
					if(i instanceof EventingInput && !inputsCopy.contains(i)) {
						inputsCopy.add((EventingInput)i);
					}
				}
				synchronized (state.storeToInput) {
					for(EventQuerier store : state.storeToInput.keySet()) {
						//queriersToTopologies.remove(store);
						queryInputsDataRate.cleanup(store);
						eventManager.closeAndRemoveStore(store);
					}
					state.storeToInput.clear();
				}
				for(String monitoringID : state.monitoringIDs) {
					owner.getMonitor().terminate(monitoringID);
				}
				state.monitoringIDs.clear();
			}
			eventManager.freeResources(topologyID);
			t = topologies.remove(topologyID);
			eventManager.unsubscribeUnusedStreams();
		} catch (Exception e) {
			logger.warn("Error when trying to destroy topology " + topologyID, e);
		}
		try {
			if(t == null)
				t = topologies.remove(topologyID);
			if(t != null) {
				if(t.getMasterAggregator().equals(owner)) {
					for(AggregatorNode a : t.getAllAggregators()) {
						if(!a.equals(owner))
							new AggregationClient(a.getEPR(), true).destroyTopology(topologyID);
					}
				}
			}
		} catch (Exception e) {
			logger.warn("Error when trying to instruct partners to destroy topology " + topologyID, e);
		}
		
		System.gc();
	}
	
	public List<Topology> getTopology(String topologyID) throws Exception {
		if(topologyID == null) {
			return new LinkedList<Topology>(topologies.values());
		}
		Topology t = topologies.get(topologyID);
		if(t != null) {
			return Arrays.asList(t);
		}
		return null;
	}
	
	public void doForwardEvents(String eventStreamID, EndpointReference receiver) {
		synchronized (eventstreamForwardTo) {
			if(!eventstreamForwardTo.containsKey(eventStreamID)) {
				eventstreamForwardTo.put(eventStreamID, new LinkedList<EndpointReference>());
			}
			eventstreamForwardTo.get(eventStreamID).add(receiver);
		}
	}
	public void stopProcessingAndForwarding(String eventStreamID, 
			EndpointReference receiver, AggregationRequest request) {
		if(eventstreamForwardTo.containsKey(eventStreamID)) {
			synchronized (eventstreamForwardTo) {
				eventstreamForwardTo.get(eventStreamID).remove(receiver);
				if(eventstreamForwardTo.get(eventStreamID).isEmpty()) {
					eventstreamForwardTo.remove(eventStreamID);
				}
			}
		}
		AbstractInput in = request.getAllInputs().get(0);
		if(logger.isDebugEnabled()) logger.debug("Terminating event processing for input " + in);
		String topologyID = request.getTopologyID();
		QueryState state = states.get(topologyID);
		for(PreparationQuery prep : request.getMatchingQueries(in)) {
			EventQuerier q = eventManager.getQuerierByQuery(eventStreamID, prep.getValue());
			state.storeToInput.remove(q);
			if(q == null) {
				logger.info("Could not find event querier for eventStreamID " + eventStreamID + " and query: " + prep.getValue());
				return;
			}
			try {
				eventManager.closeAndRemoveStore(q);
			} catch (Exception e) {
				logger.info("Unable to close eventing querier.", e);
			}
		}
		try {
			eventManager.unsubscribeIfUnused(eventStreamID);
		} catch (Exception e) {
			logger.info("Unable to check and unsubscribe event stream.", e);
		}
	}

	public void initTempBuffer(String eventStreamID) {
		if(!eventstreamTempBuffers.containsKey(eventStreamID))
			eventstreamTempBuffers.put(eventStreamID, new EventStreamTempBuffer());
	}

	public Topology getOrAddTopology(Topology topology) {
		if(topologies.containsKey(topology.getTopologyID())) {
			logger.debug("Topology with ID " + topology.getTopologyID() + " already exists. Will not be overwritten.");
		} else {
			topologies.put(topology.getTopologyID(), topology);
		}
		return topologies.get(topology.getTopologyID());
	}
	
	public EventStoresManager getEventManager() {
		return eventManager;
	}
	
	public EventTransferRateMeasurer<EventQuerier> getQueryInputsDataRate() {
		return queryInputsDataRate;
	}
	public EventTransferRateMeasurer<Pair<AggregatorNode,AggregatorNode>> getPartnerAggrTransferRate() {
		return partnerAggrTransferRate;
	}
	public EventTransferRateMeasurer<String> getUserEventRate() {
		return userEventRate;
	}

	private WebServiceClient getClientForEventForwarding() {
		try {
			if(clientForEventForwarding == null) {
				clientForEventForwarding = WebServiceClient.getClient(owner.getEPR(), false);
			}
			if(clientForEventForwarding.getNumPorts() > 100) {
				// renew the client from time to time to avoid memory leak 
				// (because when the address of a WebServiceClient 
				// is changed, a new port is automatically created)
				clientForEventForwarding = WebServiceClient.getClient(owner.getEPR(), false);
			}
		} catch (Exception e) {
			logger.warn("Unable to create web service client.", e);
		}
		return clientForEventForwarding;
	}

	@XmlRootElement(name="result")
	public static class ActiveQueryResult {
		@XmlElement(name="clientID")
		public String clientID;
		@XmlElement
		public long time = System.currentTimeMillis();
		@XmlElement
		public Object result;
		@XmlElement
		public Object updates;
		@XmlElement
		public EndpointReference epr;
	}
	
	public ActiveQueryResult pollActiveQueryResult(String topologyID, 
			String clientID, Boolean onlyUpdates, Boolean onlyOnce) throws Exception {
		if(!states.containsKey(topologyID))
			return null;
		if(clientID == null) {
			clientID = UUID.randomUUID().toString();
		} else {
			GarbageCollector.recordUsage(clientResults, clientID);
		}
		QueryState s = states.get(topologyID);
		ActiveQueryResult result = new ActiveQueryResult();
		result.clientID = clientID;
		result.result = s.results.getTotalResultElement(true);
		if(result.result != null) {
			result.result = util.xml.clone((Element)result.result);
		}
		result.epr = owner.getEPR();

		ActiveQueryResult toReturn = new ActiveQueryResult();
		toReturn.clientID = clientID;
		toReturn.epr = owner.getEPR();
		if(!clientResults.containsKey(clientID) || onlyUpdates == null || !onlyUpdates) {
			toReturn.result = result.result;
		} else {
			Element oldResult = (Element)clientResults.get(clientID).result;
			toReturn.updates = s.results.getIncrementResultElement(true, oldResult);
		}

		if(result.result != null && (onlyOnce == null || !onlyOnce)) {
			clientResults.put(clientID, result);
			GarbageCollector.deleteWhenUnused(clientResults, clientID, CLIENT_RESULT_CACHE_SECONDS);
		}
		return toReturn;
	}


}
