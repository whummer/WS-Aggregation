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

import io.hummer.util.Util;
import io.hummer.util.par.GlobalThreadPool;
import io.hummer.util.persist.Identifiable;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;

import javax.jws.WebMethod;

import org.apache.log4j.Logger;

import at.ac.tuwien.infosys.aggr.util.Invoker.InvokerTask;

public class RequestAndResultQueues<RequestType extends Identifiable,ResultType> {

	private static final Logger logger = Util.getLogger(RequestAndResultQueues.class);
	
	public final LinkedBlockingQueue<RequestType> requests = 
		new LinkedBlockingQueue<RequestType>();
	public final Map<Long,LinkedBlockingQueue<ResultType>> results = 
		new HashMap<Long, LinkedBlockingQueue<ResultType>>();
	//private final WorkQueue workQueue;
	private RequestWorker<RequestType, ResultType> worker;
	
	public static interface RequestWorker<RequestType, ResultType> {
		@WebMethod(exclude=true)
		ResultType handleRequest(RequestType request);
	}
	
	private Runnable queueRunnable = new Runnable() {
		public void run() {
			try {
				RequestType request = requests.take();
				long id = request.getIdentifier();
				ResultType result = worker.handleRequest(request);
				if(result == null || result.getClass() == Void.class) {
					// do not store results where no result is expected (return type Void.class)
					results.remove(id);
				} else {
					putResult(id, result);
				}
			} catch (Throwable e) {
				logger.error("Exception in queue runner. This should not happen!", e);
			}
		}
	};

	public RequestAndResultQueues(final RequestWorker<RequestType, ResultType> worker/*, int coreWorkerThreads, int numMaxWorkerThreads*/) {
		this.worker = worker;
		//this.workQueue = new WorkQueue(coreWorkerThreads, numMaxWorkerThreads);
	}
	
	public void putRequest(RequestType request) throws Exception {
		synchronized (results) {
			if(!results.containsKey(request.getIdentifier()))
				results.put(request.getIdentifier(), new LinkedBlockingQueue<ResultType>());
		}
		GlobalThreadPool.execute(queueRunnable);
		requests.put(request);
	}

	public ResultType takeResponse(long requestID) throws Exception {
		LinkedBlockingQueue<ResultType> resultQueue = null;
		synchronized(results) {
			if(!results.containsKey(requestID))
				return null;
			resultQueue = results.get(requestID);
		}
		return resultQueue.take();
	}

	public int getQueueLength() {
		return requests.size();
	}

	public void releaseResources(long requestID) {
		synchronized (results) {
			results.remove(requestID);			
		}
	}

	public long getNewRequestID() {
		synchronized (results) {
			long ID = InvokerTask.getNewRequestID();
			LinkedBlockingQueue<ResultType> resultList = new LinkedBlockingQueue<ResultType>();
			results.put(ID, resultList);
			return ID;
		}
	}

	public LinkedBlockingQueue<ResultType> getResponseQueue(long requestID) {
		synchronized (results) {
			return results.get(requestID);
		}
	}

	public RequestType takeRequest() throws Exception {
		return requests.take();
	}

	private void putResult(long requestID, ResultType result) throws Exception {
		synchronized (results) {
			if(result != null) {
				if(!results.containsKey(requestID))
					results.put(requestID, new LinkedBlockingQueue<ResultType>());
				results.get(requestID).put(result);
			}
		}
	}

}
