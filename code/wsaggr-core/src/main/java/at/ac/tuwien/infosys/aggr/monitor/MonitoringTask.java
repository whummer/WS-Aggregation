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
import io.hummer.util.persist.Identifiable;
import io.hummer.util.ws.AbstractNode;
import io.hummer.util.ws.request.InvocationResult;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.log4j.Logger;

import at.ac.tuwien.infosys.aggr.flow.FlowManager;
import at.ac.tuwien.infosys.aggr.flow.FlowNode.DependencyUpdatedInfo;
import at.ac.tuwien.infosys.aggr.request.AbstractInput;
import at.ac.tuwien.infosys.aggr.request.AggregationRequest;

public class MonitoringTask implements Identifiable {

	private static final Logger logger = Util.getLogger(MonitoringTask.class);
	private static final AtomicLong idCounter = new AtomicLong(1);

	private final Util util = new Util();
	private final String uuid = UUID.randomUUID().toString();

	protected final AggregationRequest originalRequest;
	/** maps subscription IDs to list of received data elements which match this subscription. */
	protected Map<String,List<InvocationResult>> receivedDataToInsertIntoRequest = new HashMap<String, List<InvocationResult>>();
	protected FlowManager manager;

	protected AbstractNode inputTo;
	protected AbstractNode reportTo;
	protected long expirationTime;
	protected long lastCheckTime;
	protected long interval;
	protected String eventStreamID;
	protected long ID = idCounter.addAndGet(1); 

	public MonitoringTask(AggregationRequest request) {
		originalRequest = request;
		manager = new FlowManager(request);
	}
	
	@Override
	public long getIdentifier() {
		return ID;
	}
	public String getUUID() {
		return uuid;
	}

	public AggregationRequest getRequest() throws Exception {
		List<AbstractInput> inputs = null;
		synchronized (manager) {
			inputs = manager.filterInputsWithoutDependencies(false);
		}
		AggregationRequest copy = new AggregationRequest(originalRequest);
		copy.getInputs().clearInputs();
		copy.getInputs().addAllInputs(inputs);
		return copy;
	}

	public void update(String subscriptionID, InvocationResult data) throws Exception {
		if(!receivedDataToInsertIntoRequest.containsKey(subscriptionID))
			receivedDataToInsertIntoRequest.put(subscriptionID, new LinkedList<InvocationResult>());
		logger.debug("MonitoringTask existing list size: " + receivedDataToInsertIntoRequest.get(subscriptionID).size());
		
		//TODO: check if we APPEND new elements to old ones or if we REPLACE existing elements..
		boolean replace = true;
		if(replace) {
			receivedDataToInsertIntoRequest.get(subscriptionID).clear();
		}
		receivedDataToInsertIntoRequest.get(subscriptionID).add(data);
		
		AbstractInput input = originalRequest.getAllInputs().get(0);
		logger.debug("Update: " + subscriptionID + " - " + data);
		manager = new FlowManager(originalRequest);
		synchronized (manager) {
			logger.debug("MonitoringTask: " + util.xml.toString(originalRequest));
			for(String id : receivedDataToInsertIntoRequest.keySet()) {
				for(InvocationResult r : receivedDataToInsertIntoRequest.get(id)) {
					List<DependencyUpdatedInfo> updated = manager.update(r, input);
					logger.debug("Updated elements: " + updated.size());
					if(updated.size() > 0)
						logger.debug("Updated request: " + getRequest());
				}
			}
		}
	}
	
	public void setEventStreamID(String eventStreamID) {
		this.eventStreamID = eventStreamID;
	}
	public String getEventStreamID() {
		return eventStreamID;
	}
	
}
