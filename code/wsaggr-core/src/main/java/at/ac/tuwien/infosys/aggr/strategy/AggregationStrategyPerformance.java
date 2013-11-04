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
package at.ac.tuwien.infosys.aggr.strategy;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import at.ac.tuwien.infosys.util.Util;
import at.ac.tuwien.infosys.ws.AbstractNode;
import at.ac.tuwien.infosys.aggr.node.AggregatorNode;
import at.ac.tuwien.infosys.aggr.performance.SortedAggregatorsList;
import at.ac.tuwien.infosys.aggr.request.AbstractInput;
import at.ac.tuwien.infosys.aggr.request.AggregationRequest;
import at.ac.tuwien.infosys.aggr.request.RequestInput;
import at.ac.tuwien.infosys.aggr.request.RequestInput.TargetType;

@XmlRootElement(name="strategy")
public class AggregationStrategyPerformance extends AggregationStrategy {
	
	@XmlTransient
	private final SortedAggregatorsList aggregators;
	@XmlElement
	private int maxBranchFactor = 13;
	@XmlElement
	private int maxRequestsPerAggregator = 10;
	@XmlElement
	private int maxRequestsToDelegatePerPartner = 50;
	@XmlElement
	private int masterSelectionRandomnessPercent = 40;
	@XmlElement
	private boolean doAvoidCircles = false;
	@XmlTransient
	private List<Double> thresholds;
	@XmlTransient
	private Random random = new Random();
	
	/**
	 * default constructor needed by JAXB, should not be used by the programmer
	 */
	@Deprecated
	public AggregationStrategyPerformance() {
		aggregators = SortedAggregatorsList.getInstance(null);
		calculateThresholds();
	}
	
	public AggregationStrategyPerformance(AbstractNode owner) {
		super(owner);
		aggregators = SortedAggregatorsList.getInstance(owner);
		aggregators.setOwner(owner);
		calculateThresholds();
	}
	
	@Override
	public void suggestMasterAggregator(String topologyID, AggregationRequest request, List<AggregatorNode> masterSuggestions) throws Exception {
		masterSuggestions.add(0, getRandomAggregatorNode());
	}
	
	protected AggregatorNode getPerformanceBasedRandomAggregatorNode() throws Exception {
		double rand = 0.5;
		synchronized (random) {
			rand = random.nextDouble();
		}
		int index = getThresholdIndex(rand);
		while(aggregators.getSortedAggregators().size() <= 0)
			Thread.sleep(500);
		//System.out.println("Selecting index " + index + " by " + rand + " from " + thresholds + " -- " + aggregators.getSortedAggregators().size());
		synchronized (aggregators.getSortedAggregators()) {
			if(index >= aggregators.getSortedAggregators().size()) {
				index = aggregators.getSortedAggregators().size() - 1;
			}
			return aggregators.getSortedAggregators().get(index);
		}
	} 
	
	@Override
	public void generateRequests(String topologyID, List<AbstractInput> inInputs,
			Map<AbstractNode,List<RequestInput>> outRequests, AggregationRequest originalRequest) throws Exception {

		int totalSize = getTotalNumberOfTargetServiceRequests(inInputs);
		Util util = new Util();
		
		double requestCount = totalSize;
		double requestsPerAggregator = requestCount;
		double responsibleAggregators = 1;
		while(requestsPerAggregator > maxRequestsPerAggregator) {
			responsibleAggregators ++;
			requestsPerAggregator = requestCount / responsibleAggregators;
		}
		if(responsibleAggregators > (maxBranchFactor + 1)) {
			responsibleAggregators = maxBranchFactor + 1;
			requestsPerAggregator = requestCount / responsibleAggregators;
		}
		
		List<AggregatorNode> partnerAggregatorNodes = aggregators.getSortedAggregators();
		
		if(doAvoidCircles) {
			if(topologyID == null)
				topologyID = "AggregationStrategyPerformance:";
			
			partnerAggregatorNodes = new LinkedList<AggregatorNode>();
			if(responsibleAggregators > 1) {
				List<String> previousAggregators = extractPreviousAggregatorURLs(topologyID);
	
				String thisAggregatorAddress = owner.getEPR().getAddress();
				topologyID += thisAggregatorAddress + " , ";
				
				for(AbstractInput i : inInputs) {
					if(i instanceof RequestInput)
						((RequestInput)i).topologyID = topologyID;
				}
				
				for(AggregatorNode partner : aggregators.getSortedAggregators()) {
					if(partnerAggregatorNodes.size() < responsibleAggregators) {
						// add only partners that have NOT already occurred previously in the aggregation path
						if(!previousAggregators.contains(partner.getEPR().getAddress())) {
							partnerAggregatorNodes.add(partner);
						}
					}
				}
			}
		}
		
		if(responsibleAggregators > (partnerAggregatorNodes.size() + 1)) {
			responsibleAggregators = partnerAggregatorNodes.size() + 1;
			requestsPerAggregator = requestCount / responsibleAggregators;
		}
		
		if(requestsPerAggregator > maxRequestsToDelegatePerPartner) {
			requestsPerAggregator = maxRequestsToDelegatePerPartner;
		}
		
		
		requestsPerAggregator = (int)requestsPerAggregator;
		for(int i = 0; i < responsibleAggregators - 1; i ++) {
			AggregatorNode a = null;
			a = partnerAggregatorNodes.get(i);
			List<RequestInput> partnerRequestList = outRequests.get(a);
			if(partnerRequestList == null) {
				partnerRequestList = new LinkedList<RequestInput>();
				outRequests.put(a, partnerRequestList);
			}
			int j = 0;
			while(j < (int)requestsPerAggregator && inInputs.size() > 0) {
				AbstractInput input = inInputs.remove(0);
				if(!(input instanceof RequestInput))
					continue;
				RequestInput in = (RequestInput)input;
				if(in.getTo() == TargetType.ONE)
					j ++;
				else if(in.getTo() == TargetType.ALL) {
					int featServFirst = util.test.isNullOrNegative(in.getFeatureServiceFirst()) ? -1 : in.getFeatureServiceFirst();
					int featServLast = util.test.isNullOrNegative(in.getFeatureServiceLast()) ? -1 : in.getFeatureServiceLast();
					
					int diff = featServLast - featServFirst + 1;
					if(diff > 0) {
						if((j + diff) > (int)requestsPerAggregator) {
							RequestInput copy = new RequestInput(in);
							in.setFeatureServiceLast(featServFirst + ((int)requestsPerAggregator - j) - 1);
							copy.setFeatureServiceFirst(featServLast + 1);
							inInputs.add(copy);
							partnerRequestList.add(in);
							break;
						} else {
							j += diff;
						}
					}

				} else
					throw new Exception("Unexpected input target type: " + in.getTo());
				
				partnerRequestList.add(in);
			}

		}
		
		extractRequestsThatTargetAllServices(inInputs);
		
		assignAllInputsToDataServices(inInputs, outRequests);

	}
	
	@Override
	public void resetCache() throws Exception {
		rebuildAggregatorList();
	}
	
	private void calculateThresholds() {
		//int unRandomness = 100 - masterSelectionRandomnessPercent;
		//double unRandPercent = (double)unRandomness / 100.0;
		double randPercent = (double)masterSelectionRandomnessPercent / 100.0;
		
		double temp = 1.0;
		List<Double> list = new LinkedList<Double>();
		for(int i = 0; i < 50; i ++) {
			temp *= randPercent;
			list.add(temp);
		}
		thresholds = list;
	}
	
	private int getThresholdIndex(double rand) {
		if(thresholds == null || thresholds.isEmpty())
			calculateThresholds();
		List<Double> list = thresholds;
		for(int i = 0; i < list.size(); i ++) {
			if(rand > list.get(i))
				return i;
		}
		return list.size() - 1;
	}
	
	private List<String> extractPreviousAggregatorURLs(String topologyID) {
		if(!topologyID.startsWith("AggregationStrategyPerformance:")) {
			return new LinkedList<String>();
		}
		topologyID = topologyID.substring("AggregationStrategyPerformance:".length());
		String[] hops = topologyID.split(" , ");
		List<String> result = Arrays.asList(hops);
		return result;
	}
	
	public void rebuildAggregatorList() throws Exception {
		aggregators.getAndSortAggregatorsByPerformance();
	}
	
	/* GETTERS / SETTERS */

	@Override
	public void setOwner(AbstractNode owner) {
		super.setOwner(owner);
		aggregators.setOwner(owner);
	}
	@XmlTransient
	public int getMaxBranchFactor() {
		return maxBranchFactor;
	}
	public void setMaxBranchFactor(int maxBranchFactor) {
		this.maxBranchFactor = maxBranchFactor;
	}
	@XmlTransient
	public int getMaxRequestsPerAggregator() {
		return maxRequestsPerAggregator;
	}
	public void setMaxRequestsPerAggregator(int maxRequestsPerAggregator) {
		this.maxRequestsPerAggregator = maxRequestsPerAggregator;
	}
	@XmlTransient
	public int getMaxRequestsToDelegatePerPartner() {
		return maxRequestsToDelegatePerPartner;
	}
	public void setMaxRequestsToDelegatePerPartner(
			int maxRequestsToDelegatePerPartner) {
		this.maxRequestsToDelegatePerPartner = maxRequestsToDelegatePerPartner;
	}
	@XmlTransient
	public int getMasterSelectionRandomnessPercent() {
		return masterSelectionRandomnessPercent;
	}
	public void setMasterSelectionRandomnessPercent(
			int masterSelectionRandomnessPercent) {
		this.masterSelectionRandomnessPercent = masterSelectionRandomnessPercent;
		calculateThresholds();
	}
	@XmlTransient
	public boolean getDoAvoidCircles() {
		return doAvoidCircles;
	}
	public void setDoAvoidCircles(boolean doAvoidCircles) {
		this.doAvoidCircles = doAvoidCircles;
	}
}
