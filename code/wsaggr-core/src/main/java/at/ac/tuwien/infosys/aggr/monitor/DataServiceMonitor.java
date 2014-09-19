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
package at.ac.tuwien.infosys.aggr.monitor;

import io.hummer.util.Util;
import io.hummer.util.test.TestUtil;
import io.hummer.util.ws.EndpointReference;
import io.hummer.util.ws.WebServiceClient;
import io.hummer.util.ws.request.InvocationResult;

import java.net.ConnectException;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

import org.apache.log4j.Logger;
import org.w3c.dom.Element;

import at.ac.tuwien.infosys.aggr.node.AggregatorNode;
import at.ac.tuwien.infosys.aggr.node.DataServiceNode;
import at.ac.tuwien.infosys.aggr.node.EventReceiverNode;
import at.ac.tuwien.infosys.aggr.proxy.AggregatorNodeProxy;
import at.ac.tuwien.infosys.aggr.request.AbstractInput;
import at.ac.tuwien.infosys.aggr.request.AggregationRequest;
import at.ac.tuwien.infosys.aggr.request.AggregationResponse;
import at.ac.tuwien.infosys.aggr.request.InputTargetExtractor;
import at.ac.tuwien.infosys.aggr.request.RequestInput;
import at.ac.tuwien.infosys.aggr.util.Invoker;
import at.ac.tuwien.infosys.aggr.util.Invoker.InvokerTask;
import at.ac.tuwien.infosys.aggr.util.RequestAndResultQueues;
import at.ac.tuwien.infosys.aggr.util.RequestAndResultQueues.RequestWorker;
import at.ac.tuwien.infosys.aggr.waql.UnresolvedDependencyException;
import at.ac.tuwien.infosys.aggr.xml.XPathProcessor;

public class DataServiceMonitor extends TimerTask implements RequestWorker<MonitoringTask, Void> {
	
	private static final long DEFAULT_EXPIRATION_TIMEOUT = 1000*60*60*24;
	private static final long DEFAULT_MONITORING_INTERVAL = 1000*3;
	private static final long CHECK_INTERVAL = 1000*2;
	private static final boolean UNSUBSCRIBE_ON_CONNECT_ERROR = true;
	
	private static final Logger logger = Util.getLogger(DataServiceMonitor.class);
	
	protected AggregatorNode owner;
	private Util util = new Util();
	private TestUtil testUtil = new TestUtil();
	private Timer timer;
	private Invoker invoker = Invoker.getInstance();
	private RequestAndResultQueues<MonitoringTask, Void> queues = new RequestAndResultQueues<MonitoringTask,Void>(this/*, 10, 100*/);
	/** per-request tasks (key: requestID) */
	private Map<String,List<MonitoringTask>> tasks = new HashMap<String, List<MonitoringTask>>(); 
	/** per-subscription listeners (key: subscriptionID) */
	private Map<String,List<NotificationTask>> listeners = new HashMap<String, List<NotificationTask>>();
	/** per-subscription pending notifications (key: subscriptionID) */
	private Map<String,ModificationNotification> pendingNotifications = new HashMap<String, ModificationNotification>(); 
	/** used to compare old values with new values of monitored data sources */
	private Map<MonitoringTask,List<AggregationResponse>> previousResponses = new HashMap<MonitoringTask,List<AggregationResponse>>(); 

	public DataServiceMonitor(AggregatorNode owner) {
		this.owner = owner;
		this.timer = new Timer(true);
		this.timer.scheduleAtFixedRate(this, new Date(
				System.currentTimeMillis()), CHECK_INTERVAL);
	}

	public void run() {
		for(String requestID : tasks.keySet()) {
			List<MonitoringTask> list = tasks.get(requestID);
			for(int i = 0; i < list.size(); i ++) {
				try {
					MonitoringTask t = list.get(i);
					if(System.currentTimeMillis() > (t.lastCheckTime + t.interval)) {
						t.lastCheckTime = System.currentTimeMillis();
						queues.putRequest(t);
					}
				} catch (Exception e) {
					logger.error(this.getClass().getName() + ".run", e);
					e.printStackTrace();
				}
			}
		}
	}

	public String monitor(AggregationRequest request) {

		if(request.getRequestID() == null) {
			request.setRequestID(UUID.randomUUID().toString());
			throw new RuntimeException("Every request object needs to carry a unique identifier (requestID).");
		}

		MonitoringTask task = new MonitoringTask(request);
		RequestInput input = (RequestInput)request.getSingleInput();
		task.expirationTime = System.currentTimeMillis() + DEFAULT_EXPIRATION_TIMEOUT;
		
		try {
			EndpointReference epr = InputTargetExtractor.extractDataSourceNode(input).getEPR();
			task.inputTo =  new DataServiceNode(epr);
		} catch (Exception e) {
			logger.error("Could not set target service EPR in data service monitor.", e);
		}
		MonitoringSpecification mon = request.getMonitor();
		if(mon != null)
			task.reportTo = new EventReceiverNode(mon.getEpr());
		task.interval = DEFAULT_MONITORING_INTERVAL;
		if(input.getInterval() != null)
			task.interval = (int)(input.getInterval() * 1000);

		String requestID = request.getRequestID();
		if(!tasks.containsKey(requestID)) {
			tasks.put(requestID, new LinkedList<MonitoringTask>());
		}
		tasks.get(requestID).add(task);
		logger.info("Monitoring data source " + task.inputTo);

		return task.getUUID();
	}

	public void handleEvent(ModificationNotification event) throws Exception {

		List<NotificationTask> notifs = listeners.get(event.getMonitoringID());
		if(notifs == null || !tasks.containsKey(event.getRequestID()))
			return;

		if(logger.isDebugEnabled()) logger.debug("Handle Event " + event.getMonitoringID() + " - " + notifs + " - " + owner);
		
		Element e = null;
		Object eventData = event.getData();
		
		if(eventData instanceof List<?> && ((List<?>)eventData).size() == 1) {
			Object o = ((List<?>)eventData).get(0);
			if(o instanceof InvocationResult)
				eventData = (InvocationResult)o;
			else if(o instanceof Element)
				eventData = (Element)o;
		}
		
		if(eventData instanceof Element)
			e = (Element)eventData;
		else if(eventData instanceof InvocationResult)
			e = ((InvocationResult)eventData).getResultAsElement();
		else {
			logger.warn("Unexpected type of event data: " + eventData.getClass());
			return;
		}
		
		for(NotificationTask n : notifs) {
			
			if(n.filterXPath == null || XPathProcessor.matches(n.filterXPath, e)) {
				logger.debug("XPath " + n.filterXPath + " matches changed data " + e + ", notifying to EPR " + n.listener);
				try {
					if(n.listener.equals(owner.getEPR())) {
						
						// re-configure and re-execute dependent requests
						logger.debug("monitoring tasks: " + tasks + ", event.requestID: " + event.getRequestID());
						logger.debug("Re-executing all requests dependent from " + e + " (" + tasks.get(event.getRequestID()).size() + " monitoring tasks): " + tasks);
						for(MonitoringTask t : tasks.get(event.getRequestID())) {
							logger.debug("Request IDs: " + t.originalRequest.getRequestID() + " - " + event.getRequestID());
							if(t.originalRequest.getRequestID().equals(event.getRequestID())) {
								boolean updated = false;
								if(eventData instanceof InvocationResult)
									t.update(event.getMonitoringID(), (InvocationResult)eventData);
								if(updated) {
									// TODO! 
								}
							}
						}
						
					} else {
						AggregatorNodeProxy proxy = new AggregatorNodeProxy(n.listener);
						proxy.onEvent(event);
					}
				} catch (Exception e2) {
					logger.error(this.getClass().getName() + ".handleEvent", e2);
					pendingNotifications.put(event.getMonitoringID(), event);
				}
			}
		}
	}

	public void addListeners(List<NotificationTask> listenersList) {
		for(NotificationTask task : listenersList) {
			String subscriptionID = task.subscriptionID;
			List<NotificationTask> listenerTasks = listeners.get(subscriptionID);
			if(listenerTasks == null) {
				listeners.put(subscriptionID, listenerTasks = new LinkedList<NotificationTask>());
			}
			NotificationTask t = new NotificationTask(subscriptionID);
			t.listener = task.listener;
			t.filterXPath = task.filterXPath;
			logger.debug(this + " Adding listener for " + subscriptionID + ", filter " + t.filterXPath + ", EPR: " + t.listener);
			listenerTasks.add(t);
		}
	}

	public ModificationNotification getMonitoredData(String subscriptionID) {
		return pendingNotifications.remove(subscriptionID);
	}
	

	@Override
	public Void handleRequest(MonitoringTask task) {

		try {
			if(task.getRequest().getAllInputs().isEmpty())
				return null;
			AbstractInput inp = task.getRequest().getSingleInput();
			if(!(inp instanceof RequestInput))
				throw new RuntimeException("Expected type: RequestInput, got: " + inp + " - " + inp.getClass());

			long requestID = invoker.getNewRequestID();
			RequestInput input = (RequestInput)inp;
			List<AbstractInput> generated = null;
			try {
				generated = input.generateInputs();
			} catch (UnresolvedDependencyException e) {
				generated = new LinkedList<AbstractInput>();
				//generated.add(inputOriginal);
			}
			List<AggregationResponse> responses = new LinkedList<AggregationResponse>();
			for(AbstractInput ai : generated) {
				RequestInput in = (RequestInput)ai;
				InvokerTask request = new InvokerTask(requestID, task.inputTo, in,
						testUtil.isNullOrTrue(task.getRequest().getTimeout()));
				invoker.addRequest(request);
				AggregationResponse response = invoker.getResponse(requestID);
				response = new AggregationResponse(response, task.originalRequest);
				if(logger.isDebugEnabled()) logger.debug("Queried data source " + task.inputTo + ", input: " + in + ", result: " + response.getResult());
				responses.add(response);
			}

			boolean changed = false;
			if(!previousResponses.containsKey(task)) {
				previousResponses.put(task, responses);
				changed = true; // the first received value is also considered a "change"
			} else {
				List<AggregationResponse> previous = previousResponses.get(task);
				changed = detectChanges(responses, previous);
			}
			previousResponses.put(task, responses);
			if(changed) {
				notifyModification(task, responses);
			}
			
		} catch (Exception e) {
			e.printStackTrace();
		}

		return null;
	}

	private void notifyModification(MonitoringTask task, List<AggregationResponse> responses) {
		try {

			ModificationNotification n = new ModificationNotification(task, responses);
			n.setMonitoringID(task.getUUID());
			owner.onEvent(n, task.eventStreamID);
			
			if(task.reportTo != null) {
				
				WebServiceClient client = WebServiceClient.getClient(task.reportTo.getEPR());
				
				RequestInput in = new RequestInput(util.xml.toElement(n));
				in.getSoapHeaders().add(util.xml.toElement("<wsa:To>" + task.reportTo.getEPR().getAddress() + "</wsa:To>"));
				logger.debug("Notifying of modification: to: " + task.reportTo.getEPR() + ", mod: " + task);
				client.invoke(in.getRequest());
			}

		} catch (ConnectException e) {
			if(UNSUBSCRIBE_ON_CONNECT_ERROR) {
				logger.info("Removing monitoring subscription " + task.inputTo);
				tasks.remove(task);
			}
		} catch (Exception e) {
			logger.warn("Could not send notification to endpoint: " + task.reportTo);
			logger.error(this.getClass().getName() + ".notifyModification", e);
		}
	}

	public MonitoringTask getTask(String requestID, String taskUUID) {
		return getTaskByUUID(tasks.get(requestID), taskUUID);
	}

	private MonitoringTask getTaskByUUID(List<MonitoringTask> tasks, String id) {
		for(MonitoringTask t : tasks) {
			if(t.getUUID().equals(id)) {
				return t;
			}
		}
		return null;
	}
	
	private boolean detectChanges(List<AggregationResponse> result, List<AggregationResponse> previous) {
		if(result.size() != previous.size())
			return true;
		for(int i = 0; i < result.size(); i ++) {
			boolean contained = false;
			for(int j = 0; j < previous.size(); j ++) {
				if(!detectChanges(result.get(i), previous.get(j))) {
					contained = true;
					break;
				}
			}
			if(!contained) {
				return true;
			}
		}
		return false;
	}
	private boolean detectChanges(Object result, Object previous) {
		if(result.getClass() != previous.getClass())
			return true;
		if(result instanceof AggregationResponse) {
			result = ((AggregationResponse)result).getResult();
			previous = ((AggregationResponse)previous).getResult();
		}
		Object r1 = null;
		Object r2 = null;
		if(result instanceof InvocationResult) {
			InvocationResult ir1 = (InvocationResult)result;
			InvocationResult ir2 = (InvocationResult)previous;
			r1 = ir1.getResult();
			r2 = ir2.getResult();
		} else {
			throw new RuntimeException("Unexpected type of invocation result: " + result.getClass());
		}
		if(r1 != null && r1 instanceof Element) {
			boolean equals = util.xml.equals((Element)r1, (Element)r2);
			return !equals;
		}
		return false;
	}

	public void terminate(String monitoringID) {
		//tasks.remove(key)
		// TODO
	}
}
