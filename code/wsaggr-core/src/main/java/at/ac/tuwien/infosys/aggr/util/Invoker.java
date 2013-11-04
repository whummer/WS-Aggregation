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
package at.ac.tuwien.infosys.aggr.util;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.log4j.Logger;

import at.ac.tuwien.infosys.aggr.request.AggregationResponse;
import at.ac.tuwien.infosys.aggr.request.InputTargetExtractor;
import at.ac.tuwien.infosys.aggr.request.NonConstantInput;
import at.ac.tuwien.infosys.aggr.request.RequestInput;
import at.ac.tuwien.infosys.aggr.request.WebSearchInput;
import at.ac.tuwien.infosys.aggr.util.RequestAndResultQueues.RequestWorker;
import at.ac.tuwien.infosys.util.Identifiable;
import at.ac.tuwien.infosys.util.Util;
import at.ac.tuwien.infosys.util.misc.PerformanceInterceptor;
import at.ac.tuwien.infosys.util.misc.PerformanceInterceptor.EventType;
import at.ac.tuwien.infosys.ws.AbstractNode;
import at.ac.tuwien.infosys.ws.EndpointReference;
import at.ac.tuwien.infosys.ws.WebServiceClient;
import at.ac.tuwien.infosys.ws.request.InvocationRequest;
import at.ac.tuwien.infosys.ws.request.InvocationResult;

public class Invoker implements RequestWorker<Invoker.InvokerTask, AggregationResponse> {
	
	private final RequestAndResultQueues<InvokerTask, AggregationResponse> queues = 
		new RequestAndResultQueues<InvokerTask, AggregationResponse>(this);

	private static final Invoker instance = new Invoker();
	private static final Logger logger = Util.getLogger(Invoker.class);

	public static class InvokerTask implements Identifiable {
		private static AtomicLong idCounter = new AtomicLong(1);
		
		public final long ID;
		public final AbstractNode target;
		public final InvocationRequest request;
		public final RequestInput input;
		
		public InvokerTask(long taskID, AbstractNode target, RequestInput input, boolean timeout) {
			this.ID = taskID;
			this.target = target;
			this.request = input.getRequest();
			this.input = input;
			this.request.cache = input.isCache();
			this.request.timeout = timeout;
		}

		public long getIdentifier() {
			return ID;
		}

		public static synchronized long getNewRequestID() {
			return idCounter.getAndIncrement();
		}
	}
	
	private Invoker() {}
	
	public static Invoker getInstance() {
		return instance;
	}
	
	public long getNewRequestID() {
		return queues.getNewRequestID();
	}

	public void addRequest(InvokerTask request) throws Exception {
		queues.putRequest(request);
	}

	public AggregationResponse getResponse(long requestID) throws Exception {
		return queues.takeResponse(requestID);
	}
	
	public void releaseResources(long requestID) {
		queues.releaseResources(requestID);
	}
	
	public LinkedBlockingQueue<AggregationResponse> getResponseQueue(long requestID) {
		return queues.getResponseQueue(requestID);
	}

	public AggregationResponse handleRequest(InvokerTask request) {
		try {
			//System.out.println("Performing invoker task " + request.ID);
			String tmpID = PerformanceInterceptor.event(EventType.START_INVOCATION);
			InvocationResult result = null;
			if(request.input instanceof WebSearchInput) {
				result = ((WebSearchInput)request.input).getResult();
			} else {
				AtomicReference<Object> resultRef = new AtomicReference<Object>();
				//Parallelization.warnIfNoResultAfter(resultRef, "!! No result in Invoker after 10 seconds for input: " + request.input + " - request: " + request.request, 1000*10);
				WebServiceClient client = WebServiceClient.getClient(request.target.getEPR());
				result = client.invoke(request.request);
				resultRef.set(result);
			}
			AggregationResponse response = new AggregationResponse(request, result);
			PerformanceInterceptor.event(EventType.FINISH_INVOCATION, tmpID);
			return response;
		} catch (Exception e) {
			logger.warn("Error when invoking service.", e);
			AggregationResponse response = new AggregationResponse(request, e);
			return response;
		}
	}
	
	public static InvocationResult getResultForInput(NonConstantInput input) {
		try {
			InvocationResult result = null;
			if(input instanceof WebSearchInput) {
				result = ((WebSearchInput)input).getResult();
			} else if(input instanceof RequestInput) {
				RequestInput reqIn = (RequestInput)input;
				EndpointReference epr = InputTargetExtractor.extractDataSourceNode(reqIn).getEPR();
				WebServiceClient client = WebServiceClient.getClient(epr);
				InvocationRequest req = ((RequestInput) input).getRequest();
				result = client.invoke(req);
			} else {
				logger.warn("Unexpected input type: " + input + (input != null ? (" - " + input.getClass()) : ""));
			}
			return result;

		} catch (Exception e) {
			logger.warn("Error when invoking service.", e);
			InvocationResult result = new InvocationResult(e);
			return result;
		}
	}

}
