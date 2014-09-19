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
package at.ac.tuwien.infosys.aggr.performance;

import io.hummer.util.Util;
import io.hummer.util.par.GlobalThreadPool;
import io.hummer.util.ws.AbstractNode;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import org.apache.log4j.Logger;

import at.ac.tuwien.infosys.aggr.node.AggregatorNode;
import at.ac.tuwien.infosys.aggr.node.Registry;
import at.ac.tuwien.infosys.aggr.proxy.AggregatorNodeProxy;
import at.ac.tuwien.infosys.aggr.proxy.RegistryProxy;
import at.ac.tuwien.infosys.aggr.robust.FailoverManager;

public class SortedAggregatorsList {

	private static final Logger logger = Util.getLogger(SortedAggregatorsList.class);

	boolean gatewayReceivesAllPerformanceInfos = true;
	boolean aggregatorReceivesAllPerformanceInfos = false;

	public static interface PerformanceInfoListener {
		void performanceInfosUpdated(SortedAggregatorsList l);
	}

	private class AggregatorPerformanceInfoSortable extends AggregatorPerformanceInfo implements Comparable<AggregatorPerformanceInfoSortable> {
		private AggregatorPerformanceInfo original;
		public AggregatorPerformanceInfoSortable(AggregatorPerformanceInfo _info) {
			this.original = _info;
			this.setRequestQueueLength(_info.getRequestQueueLength());
			this.aggregator = _info.aggregator;
			this.setUsedMemory(_info.getUsedMemory());
			this.setStreamDataRate(_info.getStreamDataRate());
			this.setInputDataRate(_info.getInputDataRate());
			this.setInterAggregatorDataRate(_info.getInterAggregatorDataRate());
			this.setUserDataRate(_info.getUserDataRate());
			this.setWindowSizeCPU(_info.getWindowSizeCPU());
			this.setCurrentUsedCPU(_info.getCurrentUsedCPU());
			this.setMaxUsedCPUOverWindow(_info.getMaxUsedCPUOverWindow());
		}
		public int compareTo(AggregatorPerformanceInfoSortable o) {
			// TODO: include remaining values...
			Double thisValue = multFactorMemoryUsage * getUsedMemory() + 
					multFactorQueueLength * (double)getRequestQueueLength() + 
					multFactorCpuUsage * (double)getCurrentUsedCPU();
			Double otherValue = multFactorMemoryUsage * o.getUsedMemory() + 
					multFactorQueueLength * (double)o.getRequestQueueLength() + 
					multFactorCpuUsage * (double)o.getCurrentUsedCPU();
			return thisValue.compareTo(otherValue);
		}
		@Override
		public String toString() {
			Double thisValue = multFactorMemoryUsage * getUsedMemory() + 
					multFactorQueueLength * (double)getRequestQueueLength() + 
					multFactorCpuUsage * (double)getCurrentUsedCPU();
			return "(" + thisValue + ")";
		}
	}

	private List<AggregatorPerformanceInfoSortable> performanceInfos = new LinkedList<AggregatorPerformanceInfoSortable>();
	private List<AggregatorNode> sortedAggregators = new LinkedList<AggregatorNode>();
	final private List<PerformanceInfoListener> listeners = new LinkedList<PerformanceInfoListener>();
	private double multFactorMemoryUsage = 1;
	private double multFactorCpuUsage = 1000; // one percent CPU usage is as "bad" as 10MB memory usage
	private double multFactorQueueLength = 50000; // one element in the queue is as "bad" as 50MB memory usage
	private long checkIntervalMilliseconds = 1000*30; // check interval
	private long checkIntervalIfNoAggregatorsMilliseconds = 1000*5; 
	private AbstractNode owner;

	private static SortedAggregatorsList instance;
	public static SortedAggregatorsList getInstance(AbstractNode owner) {
		if(instance == null) {
			instance = new SortedAggregatorsList(owner);
		}
		if(instance.owner == null)
			instance.owner = owner;
		return instance;
	}
	
	private SortedAggregatorsList(AbstractNode owner) {
		this.owner = owner;
		start();
	}
	
	public List<AggregatorNode> getSortedAggregators() {
		return sortedAggregators;
	}
	
	public List<AggregatorPerformanceInfo> getPerformanceInfos() {
		List<AggregatorPerformanceInfo> result = new LinkedList<AggregatorPerformanceInfo>();
		for(AggregatorPerformanceInfoSortable s : performanceInfos) {
			result.add(s.original);
		}
		return result;
	}
	
	public void addListener(PerformanceInfoListener l) {
		synchronized (listeners) {
			listeners.add(l);
		}
	}
	
	private void start() {
		Runnable r = new Runnable() {
			@Override
			public void run() {
				try {
					Thread.sleep(5000);
				} catch (InterruptedException e1) { }
				while(true) {
					try {
						long duration = getAndSortAggregatorsByPerformance();
						synchronized (listeners) {
							for(PerformanceInfoListener l : listeners)
								l.performanceInfosUpdated(SortedAggregatorsList.this);
						}
						Thread.sleep(duration);
					} catch (Exception e) {
						logger.info("Could not retrieve sorted aggregators list: " + e);
						try {
							Thread.sleep(5000);
						} catch (InterruptedException e1) { }
					}
				}
			}
		};
		GlobalThreadPool.execute(r);
	}

	public long getAndSortAggregatorsByPerformance() throws Exception {

		RegistryProxy registry = Registry.getRegistryProxy();
		List<AggregatorNode> aggregators = registry.getAggregatorNodes();
		List<AggregatorPerformanceInfoSortable> newList = new LinkedList<AggregatorPerformanceInfoSortable>();
		if(aggregators.size() <= 0) {
			RegistryProxy.resetCache();
			return checkIntervalIfNoAggregatorsMilliseconds; // check again soon
		}
		for(AggregatorNode a : aggregators) {
			if(owner == null || !a.equals(owner)) {
				try {
					AggregatorNodeProxy p = new AggregatorNodeProxy(a.getEPR());
					AggregatorPerformanceInfo _info = new AggregatorPerformanceInfo();
					_info.aggregator = a;
					if(owner == null || 
							((owner instanceof AggregatorNode) && aggregatorReceivesAllPerformanceInfos) || 
							gatewayReceivesAllPerformanceInfos) {
						_info = p.getPerformanceInfo(true);
					}
					AggregatorPerformanceInfoSortable info = new AggregatorPerformanceInfoSortable(_info);
					info.aggregator = a;
					newList.add(info);
				} catch (Exception e) {
					if(e.toString() != null && e.toString().contains("Invocation to")) // "Invocation to http://... failed"
						FailoverManager.reportDeadAggregator(a.getEPR());
				}
			}
		}
		Collections.sort(newList);
		synchronized (performanceInfos) {
			performanceInfos = newList;
			synchronized (sortedAggregators) {
				sortedAggregators = new LinkedList<AggregatorNode>();
				for(AggregatorPerformanceInfoSortable i : performanceInfos) {
					sortedAggregators.add(i.aggregator);
				}
			}
		}
		return checkIntervalMilliseconds;
	}

	public void setOwner(AbstractNode owner) {
		this.owner = owner;
	}

}
