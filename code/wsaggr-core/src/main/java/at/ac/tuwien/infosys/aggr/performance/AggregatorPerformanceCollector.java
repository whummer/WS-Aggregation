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

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;

import at.ac.tuwien.infosys.aggr.node.AggregatorNode;
import at.ac.tuwien.infosys.aggr.performance.AggregatorPerformanceInfo.InterAggregatorsDataRate;
import at.ac.tuwien.infosys.aggr.performance.AggregatorPerformanceInfo.StreamDataRate;
import at.ac.tuwien.infosys.aggr.proxy.AggregatorNodeProxy;
import at.ac.tuwien.infosys.aggr.request.AbstractInput.InputWrapper;
import at.ac.tuwien.infosys.util.Util;
import at.ac.tuwien.infosys.util.par.GlobalThreadPool;

public class AggregatorPerformanceCollector extends Thread {
	
	private static final Logger logger = Util.getLogger(AggregatorPerformanceCollector.class);
	private final Map<AggregatorNode, AggregatorPerformanceInfo> performanceInfos = new HashMap<AggregatorNode, AggregatorPerformanceInfo>();
	private final List<AggregatorNode> activeAggrs = new LinkedList<AggregatorNode>();
	private boolean loop = false;
	private boolean detailed = false;
	private Util util = new Util();

	public AggregatorPerformanceCollector(boolean start) {
		this.loop = start;
		if(start)
			start();
	}
	public void addAggregator(AggregatorNode a) {
		if(!activeAggrs.contains(a))
			activeAggrs.add(a);
	}
	public boolean removeAggregator(AggregatorNode a) {
		return activeAggrs.remove(a);
	}
	public void addAllAggregators(List<AggregatorNode> list) {
		for(AggregatorNode a : list)
			if(!activeAggrs.contains(a))
				activeAggrs.add(a);
	}
	public boolean hasAggregator(AggregatorNode a) {
		return activeAggrs.contains(a);
	}
	public boolean hasResults() {
		return !performanceInfos.isEmpty();
	}

	public double getMaxCPU() {
		return max(getCPU(), 0);
	}
	public double getMaxMemory() {
		return max(getMemory(), 0);
	}
	public double getMinCPU() {
		return min(getCPU(), 0);
	}
	public double getMinMemory() {
		return min(getMemory(), 0);
	}
	
	public List<AggregatorNode> getActiveAggegators() {
		return activeAggrs;
	}

	private List<Double> getCPU() {
		List<Double> list = new LinkedList<Double>();
		synchronized (performanceInfos) {
			for(AggregatorPerformanceInfo i : performanceInfos.values())
				list.add(i.getCurrentUsedCPU());
		}
		return list;
	}
	private List<Double> getMemory() {
		List<Double> list = new LinkedList<Double>();
		synchronized (performanceInfos) {
			for(AggregatorPerformanceInfo i : performanceInfos.values())
				list.add(i.getUsedMemory());
		}
		return list;
	}

	public double getMemory(AggregatorNode a) {
		if(performanceInfos.get(a) == null)
			return -1;
		return performanceInfos.get(a).getUsedMemory();
	}
	public double getCPU(AggregatorNode a) {
		if(performanceInfos.get(a) == null)
			return -1;
		return performanceInfos.get(a).getCurrentUsedCPU();
	}
	public double getDataTransfer(AggregatorNode a) {
		if(performanceInfos.get(a) == null)
			return -1;
		double total = 0;
		List<StreamDataRate> list = performanceInfos.get(a).getStreamDataRate();
		for(StreamDataRate s : list)
			total += s.getDataRate();
		return total;
	}
	private List<Double> getDataTransfers() {
		List<Double> result = new LinkedList<Double>();
		for(AggregatorNode a : performanceInfos.keySet()) {
			double t = getDataTransfer(a);
			if(t > 0) result.add(t);
		}
		return result;
	}
	public double getMaxDataTransfer() {
		return max(getDataTransfers(), 0);
	}
	public double getMinDataTransfer() {
		return min(getDataTransfers(), 0);
	}
	private double max(List<Double> list, double dflt) {
		if(list.size() <= 0)
			return dflt;
		return Collections.max(list);
	}
	private double min(List<Double> list, double dflt) {
		if(list.size() <= 0)
			return dflt;
		return Collections.min(list);
	}
	public double getAvgDataTransfer() {
		return util.math.average(getDataTransfers());
	}
	public double getMaxMinusMinDataTransfer() {
		return getMaxDataTransfer() - getMinDataTransfer();
	}
	public double getDataTransfer() {
		double result = 0.0;
		for(Double t : getDataTransfers()) {
			result += t;
		}
		return result;
	}

	public double getDataTransfer(AggregatorNode a, InputWrapper w) {
		if(performanceInfos.get(a) == null)
			return -1;
		double total = 0;
		List<StreamDataRate> list = performanceInfos.get(a).getStreamDataRate();
		for(StreamDataRate s : list) {
			try {
				if(s.getStream().equals(w)) {
					total += s.getDataRate();
				}
			} catch (Exception e) {
				logger.warn("Unable to compute aggregator performance (data transfer).", e);
			}
		}
		return total;
	}

	public double getDataFrequency(AggregatorNode a) {
		if(performanceInfos.get(a) == null)
			return -1;
		double total = 0;
		List<StreamDataRate> list = performanceInfos.get(a).getStreamDataRate();
		for(StreamDataRate s : list)
			total += s.getEventFreq();
		return total;
	}
	private List<Double> getDataFrequencies() {
		List<Double> result = new LinkedList<Double>();
		for(AggregatorNode a : performanceInfos.keySet()) {
			double t = getDataFrequency(a);
			if(t > 0) result.add(t);
		}
		return result;
	}
	public double getDataFrequency() {
		return util.math.sum(getDataFrequencies());
	}

	public double getInterAggregatorTransfer() {
		double sum = 0;
		for(AggregatorNode from : performanceInfos.keySet()) {
			for(AggregatorNode to : performanceInfos.keySet()) {
				double toAdd = getInterAggregatorTransfer(from, to);
				if(toAdd >= 0)
					sum += toAdd;
			}
		}
		return sum;
	}
	
	public double getInterAggregatorTransfer(AggregatorNode fromAggr,
			AggregatorNode toAggr) {
		if(performanceInfos.get(fromAggr) == null)
			return -1;
		double result = 0;
		for(InterAggregatorsDataRate r : performanceInfos.get(fromAggr).getInterAggregatorDataRate()) {
			if(fromAggr.getEPR().getAddress().equals(r.getAggregatorURL1()) && 
					toAggr.getEPR().getAddress().equals(r.getAggregatorURL2())) {
				result += r.getDataRate();
			}
		}
		return result;
	}

	public double getBufferMemory(AggregatorNode a) {
		AggregatorPerformanceInfo i = performanceInfos.get(a);
		double sum = 0;
		for(StreamDataRate r : i.getStreamDataRate()) {
			if(r.getBufferSize() != null)
				sum += r.getBufferSize();
		}
		return sum;
	}
	public double getBufferMemoryTotal() {
		double sum = 0;
		for(AggregatorNode a : performanceInfos.keySet()) {
			sum += getBufferMemory(a);
		}
		return sum;
	}
	
	public void setDetailed(boolean detailed) {
		this.detailed = detailed;
	}

	@Override
	public void run() {
		do {
			try {
				if(activeAggrs.size() > 0) {
					final List<Integer> list = new LinkedList<Integer>();
					for(int i = 0; i < activeAggrs.size(); i ++) {
						final AggregatorNode a = activeAggrs.get(i);
						Runnable r = new Runnable() {
							@Override
							public void run() {
								synchronized (list) {
									list.add(0, 0);									
								}
								try {
									if(a != null) {
										AggregatorPerformanceInfo perf = 
											new AggregatorNodeProxy(a).getPerformanceInfo(true, detailed);
										synchronized (performanceInfos) {
											performanceInfos.put(a, perf);
										}
									}
								} catch (Exception e) { logger.info("", e); }
								synchronized (list) {
									list.remove(0);
								}
							}
						};
						GlobalThreadPool.execute(r);
					}
					while(list.size() > 0)
						Thread.sleep(1000);
				}
				Thread.sleep(1000);
			} catch (Exception e) {
				logger.warn("Unexpected error.", e);
			}
		} while(loop);
	}
}
