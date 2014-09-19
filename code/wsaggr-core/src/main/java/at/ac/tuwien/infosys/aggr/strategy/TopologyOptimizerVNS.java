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

import io.hummer.util.Util;
import io.hummer.util.coll.Pair;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import javax.xml.bind.annotation.XmlRootElement;

import org.apache.log4j.Logger;
import org.w3c.dom.Element;

import at.ac.tuwien.infosys.aggr.node.AggregatorNode;
import at.ac.tuwien.infosys.aggr.node.DataServiceNode;
import at.ac.tuwien.infosys.aggr.node.Registry;
import at.ac.tuwien.infosys.aggr.performance.AggregatorPerformanceInfo;
import at.ac.tuwien.infosys.aggr.performance.AggregatorPerformanceInfo.StreamDataRate;
import at.ac.tuwien.infosys.aggr.performance.SortedAggregatorsList;
import at.ac.tuwien.infosys.aggr.performance.SortedAggregatorsList.PerformanceInfoListener;
import at.ac.tuwien.infosys.aggr.proxy.AggregatorNodeProxy;
import at.ac.tuwien.infosys.aggr.request.AbstractInput;
import at.ac.tuwien.infosys.aggr.request.AbstractInput.InputWrapper;
import at.ac.tuwien.infosys.aggr.request.AggregationRequest;
import at.ac.tuwien.infosys.aggr.request.EventingInput;
import at.ac.tuwien.infosys.aggr.request.WAQLQuery.PreparationQuery;
import at.ac.tuwien.infosys.aggr.strategy.TopologySolution.SolutionListener;
import at.ac.tuwien.infosys.aggr.waql.DataDependency;

public class TopologyOptimizerVNS extends Thread implements PerformanceInfoListener {

	private static final Logger logger = Util.getLogger(TopologyOptimizerVNS.class);
	private static Util util = new Util();
	
	private boolean running = false;
	private TopologySolution originalSolution;
	private TopologySolution bestSolution;
	private TopologySolution currentSolution;
	private AtomicLong bestSolutionTime;
	private static List<AggregatorNode> aggregators = new LinkedList<AggregatorNode>();
	private double bestValue = Double.MAX_VALUE;
	private List<List<Integer>> dependingInputToProvidingInputs;
	private Map<Integer,Integer> inputToDataSource;
	private Map<Integer,List<Integer>> dataSourceToInputs;
	private Map<Integer,Double> dataSourceToTransferRate = new HashMap<Integer, Double>();
	private List<SolutionListener> listeners = new LinkedList<SolutionListener>();
	private List<Pair<InputWrapper, PreparationQuery>> eventInputs;
	private List<InputWrapper> eventDataSources = new LinkedList<InputWrapper>();
	private Timer timer = new Timer();
	private List<AggregationRequest> inputToRequests;
	private Map<String,Topology> topologyMap;
	private final AtomicInteger activeNeighborhoods = new AtomicInteger();
	private ExecutorService executor = Executors.newFixedThreadPool(20);
	private boolean isActive = false;
	private static final Object lock = new Object();
	private SolutionEvaluator eval = new SolutionEvaluator();
	private boolean printDebugOutput = false;

	private OptimizationParameters params = new OptimizationParameters();
	
	private static TopologyOptimizerVNS instance = null;
	public static boolean silent = false;
	
	private TopologyOptimizerVNS() {
		SortedAggregatorsList.getInstance(null).addListener(this);
		start();
	}

	@XmlRootElement(name="params")
	public static class OptimizationParameters {
		public double penaltyForMigration = 2;
		public double penaltyForDataTransmission = 1/2;
		public double penaltyForAggrCardinalityDiff = 3; // 1
		public double penaltyForRedundantDataSources = 2; // 15
		public double limitForCpuUsage = 0.9;
		public long loopDurationMS = 20 * 1000;
		public boolean automaticallyRestartLoop = false;
		public boolean doApplyMigrations = false;
	}

	public static void doRun(OptimizationParameters params) {
		if(instance == null) {
			instance = new TopologyOptimizerVNS();
		}
		if(params == null) {
			params = new OptimizationParameters();
		}

		synchronized (lock) {
			instance.params = params;
			try {
				instance.running = false;
				instance.setup(TopologyUtil.collectAllTopologies());
				instance.startOptimization(params.loopDurationMS);
			} catch (Exception e) {
				logger.warn(e);
				return;
			}
			instance.isActive = true;
			return;
		}
		//instance.setup(Gateway.getGatewayProxy().collectAllTopologies());
		//instance.startOptimization(params.loopDurationMS);
		//instance.params = params;
	}
	public static void doPause() {
		if(instance != null) {
			synchronized (lock) {
				instance.isActive = false;
			}
		}
	}
	
	public class SwapAggregatorNH implements Iterator<TopologySolution> {
		private Integer[] affectedRequestIDs;
		private int[] crtAggr;
		private TopologySolution solution;
		public SwapAggregatorNH(TopologySolution s, Integer ... requestIDs) {
			this.solution = s;
			this.affectedRequestIDs = requestIDs;
			crtAggr = new int[requestIDs.length];
			
		}
		public boolean hasNext() {
			for(int i = 0; i < crtAggr.length; i ++) {
				if(crtAggr[i] < aggregators.size())
					return true;
			}
			return false;
		}
		public TopologySolution next() {
			increaseAt(0);
			solution = new TopologySolution(solution);
			for(int i = 0; i < affectedRequestIDs.length; i ++) {
				solution.inputToAggregator[affectedRequestIDs[i]] = crtAggr[i];
			}
			return solution;
		}
		private boolean increaseAt(int index) {
			if(index > crtAggr.length - 1)
				return false;
			crtAggr[index]++;
			if(crtAggr[index] >= aggregators.size()) {
				crtAggr[index] = 1;
				return increaseAt(index + 1);
			}
			return true;
		}
		public void remove() { }
	}
	
	public static class SolutionEvaluationException extends RuntimeException {
		private static final long serialVersionUID = 1L;
	}

	public class BundleInputsNH implements Iterator<TopologySolution> {
		private List<Integer> affectedInputIDs;
		private int[] crtAggr;
		private int crtAggrIndex;
		private TopologySolution solution;
		private List<Integer> aggrToChooseFrom;
		public BundleInputsNH(TopologySolution s, Integer dataSourceID) {
			solution = s;
			affectedInputIDs = dataSourceToInputs.get(dataSourceID);
			crtAggr = new int[dataSourceToInputs.get(dataSourceID).size()];
			aggrToChooseFrom = new LinkedList<Integer>();
			for(int i : affectedInputIDs) {
				if(!aggrToChooseFrom.contains(s.inputToAggregator[i]))
					aggrToChooseFrom.add(s.inputToAggregator[i]);
			}
		}
		public boolean hasNext() {
			for(int i = 0; i < crtAggr.length; i ++) {
				if(crtAggr[i] >= aggrToChooseFrom.size())
					return false;
			}
			return true;
		}
		public TopologySolution next() {
			solution = new TopologySolution(solution);
			for(int i = 0; i < affectedInputIDs.size(); i ++) {
				int inputID = affectedInputIDs.get(i);
				solution.inputToAggregator[inputID] = aggrToChooseFrom.get(crtAggr[i]);
			}
			crtAggrIndex++;
			if(crtAggrIndex >= crtAggr.length)
				crtAggrIndex = 0;
			increaseAt(crtAggrIndex);
			return solution;
		}
		private boolean increaseAt(int index) {
			if(index > crtAggr.length - 1)
				return false;
			crtAggr[index]++;
			return true;
		}
		public void remove() { }
	}

	// TODO
	public class AvoidMigrationNH implements Iterator<TopologySolution> {
		private List<Integer> affectedInputIDs;
		private int[] crtAggr;
		private int crtAggrIndex;
		private TopologySolution solution;
		private List<Integer> aggrToChooseFrom;
		public AvoidMigrationNH(TopologySolution s, Integer dataSourceID) {
			solution = s;
			affectedInputIDs = dataSourceToInputs.get(dataSourceID);
			crtAggr = new int[dataSourceToInputs.get(dataSourceID).size()];
			aggrToChooseFrom = new LinkedList<Integer>();
			for(int i : affectedInputIDs) {
				if(!aggrToChooseFrom.contains(s.inputToAggregator[i]))
					aggrToChooseFrom.add(s.inputToAggregator[i]);
			}
		}
		public boolean hasNext() {
			for(int i = 0; i < crtAggr.length; i ++) {
				if(crtAggr[i] >= aggrToChooseFrom.size())
					return false;
			}
			return true;
		}
		public TopologySolution next() {
			solution = new TopologySolution(solution);
			for(int i = 0; i < affectedInputIDs.size(); i ++) {
				int inputID = affectedInputIDs.get(i);
				solution.inputToAggregator[inputID] = aggrToChooseFrom.get(crtAggr[i]);
			}
			crtAggrIndex++;
			if(crtAggrIndex >= crtAggr.length)
				crtAggrIndex = 0;
			increaseAt(crtAggrIndex);
			return solution;
		}
		private boolean increaseAt(int index) {
			if(index > crtAggr.length - 1)
				return false;
			crtAggr[index]++;
			return true;
		}
		public void remove() { }
	}
	
	public class EqualInputsPerAggrNH implements Iterator<TopologySolution> {
		private int crtInputIndex;
		private TopologySolution solution;
		private Map<Integer,AtomicInteger> aggrCardinality;
		private int maxCard = 0;
		private int minCard = Integer.MAX_VALUE;
		private int maxCardAggr = -1;
		private int minCardAggr = -1;
		public EqualInputsPerAggrNH(TopologySolution s) {
			solution = s;
			aggrCardinality = new HashMap<Integer, AtomicInteger>();
			for(int i = 0; i < aggregators.size(); i ++) {
				aggrCardinality.put(i, new AtomicInteger());
			}
			for(int i = 0; i < s.inputToAggregator.length; i ++) {
				if(!aggrCardinality.containsKey(s.inputToAggregator[i])) {
					aggrCardinality.put(s.inputToAggregator[i], new AtomicInteger());
					logger.warn("aggr key not present: " + s.inputToAggregator[i]);
				}
				aggrCardinality.get(s.inputToAggregator[i]).incrementAndGet();
			}
			setMinMax();
		}
		public boolean hasNext() {
			return getCardinalityDiff() > 5;
		}
		private void setMinMax() {
			maxCard = 0;
			minCard = Integer.MAX_VALUE;
			maxCardAggr = -1;
			minCardAggr = -1;
			for(int i : aggrCardinality.keySet()) {
				AtomicInteger card = aggrCardinality.get(i);
				if(card.get() > maxCard) {
					maxCard = card.get();
					maxCardAggr = i;
				}
				if(card.get() < minCard) {
					minCard = card.get();
					minCardAggr = i;
				}
			}
		}
		public int getCardinalityDiff() {
			return maxCard - minCard;
		}
		public TopologySolution next() {
			int length = solution.inputToAggregator.length;
			while(crtInputIndex < length) {
				if(solution.inputToAggregator[crtInputIndex] == maxCardAggr) {
					solution = new TopologySolution(solution);
					solution.inputToAggregator[crtInputIndex] = minCardAggr;
					aggrCardinality.get(maxCardAggr).decrementAndGet();
					aggrCardinality.get(minCardAggr).incrementAndGet();
					setMinMax();
					return solution;
				}
				crtInputIndex++;
				if(crtInputIndex > length)
					crtInputIndex = 0;
			}
			return solution;
		}
		public void remove() { }
	}
	
	public class EqualDataLoadPerAggrNH implements Iterator<TopologySolution> {
		private int crtInputIndex;
		private TopologySolution solution;
		private Map<Integer,Double> aggrDataLoad;
		private double max = 0;
		private double min = Double.MAX_VALUE;
		private int maxAggr = -1;
		private int minAggr = -1;
		public EqualDataLoadPerAggrNH(TopologySolution s) {
			solution = s;
			aggrDataLoad = new HashMap<Integer, Double>();
			for(int i = 0; i < aggregators.size(); i ++) {
				aggrDataLoad.put(i, 0.0);
			}
			for(int i = 0; i < s.inputToAggregator.length; i ++) {
				if(!aggrDataLoad.containsKey(s.inputToAggregator[i])) {
					aggrDataLoad.put(s.inputToAggregator[i], 0.0);
					logger.warn("aggr key not present: " + s.inputToAggregator[i]);
				}
				aggrDataLoad.put(s.inputToAggregator[i], getLoad(s.inputToAggregator[i], s));
			}
			setMinMax();
		}
		public double getLoad(int aggregator, TopologySolution s) {
			double total = 0;
			List<Integer> sources = new LinkedList<Integer>();
			for(int i = 0; i < s.inputToAggregator.length; i ++) {
				int source = inputToDataSource.get(s);
				if(!sources.contains(source)) {
					total += dataSourceToTransferRate.get(source);
					sources.add(source);
				}
			}
			return total;
		}
		public void setAggregatorLoads() {
			Set<Integer> aggregators = new HashSet<Integer>();
			for(int a : solution.inputToAggregator) {
				aggregators.add(a);
			}
			for(int a : aggregators) {
				aggrDataLoad.put(a, getLoad(a, solution));
			}
		}
		public boolean hasNext() {
			return getLoadDiff() > 5;
		}
		private void setMinMax() {
			max = 0;
			min = Integer.MAX_VALUE;
			maxAggr = -1;
			minAggr = -1;
			for(int i : aggrDataLoad.keySet()) {
				Double val = aggrDataLoad.get(i);
				if(val > max) {
					max = val;
					maxAggr = i;
				}
				if(val < min) {
					min = val;
					minAggr = i;
				}
			}
		}
		public double getLoadDiff() {
			return max - min;
		}
		public TopologySolution next() {
			int length = solution.inputToAggregator.length;
			while(crtInputIndex < length) {
				if(solution.inputToAggregator[crtInputIndex] == maxAggr) {
					solution = new TopologySolution(solution);
					solution.inputToAggregator[crtInputIndex] = minAggr;
					setAggregatorLoads();
					setMinMax();
					return solution;
				}
				crtInputIndex++;
				if(crtInputIndex > length)
					crtInputIndex = 0;
			}
			return solution;
		}
		public void remove() { }
	}

	public class SolutionEvaluator {
		public double evaluate(TopologySolution s) {
			if(originalSolution.inputToAggregator.length != s.inputToAggregator.length) {
				logger.debug("length of current solution != length of previous solution");
				throw new SolutionEvaluationException();
			}
			try {
				double val = 0;
				val += (double)s.getNumMigrationsFrom(originalSolution) * params.penaltyForMigration;
				double numDataFlowBetweenAggregators = getDependenciesBetweenAggrs(s);
				val += params.penaltyForDataTransmission * numDataFlowBetweenAggregators;
				double numDuplicateDataSources = getDataSourcesOnMultipleAggregators(s);
				val += params.penaltyForRedundantDataSources * numDuplicateDataSources;
				double numCardinalityDifferences = getAggrCardinalityDifferences(s);
				val += params.penaltyForAggrCardinalityDiff * numCardinalityDifferences;
				return val;
			} catch (ArrayIndexOutOfBoundsException e) {
				throw new SolutionEvaluationException();
			} catch (Exception e) {
				logger.warn("error in evaluator", e);
				throw new SolutionEvaluationException();
			}
		}
		protected double getDependenciesBetweenAggrs(TopologySolution s) {
			double numDataFlowBetweenAggregators = 0;
			for(int i = 0; i < dependingInputToProvidingInputs.size(); i ++) {
				for(int j = 0; j < dependingInputToProvidingInputs.get(i).size(); j ++) {
					int aggr1 = s.inputToAggregator[i];
					int aggr2 = s.inputToAggregator[j];
					if(aggr1 != aggr2)
						numDataFlowBetweenAggregators ++;
				}
			}
			return numDataFlowBetweenAggregators;
		}
		protected double getDataSourcesOnMultipleAggregators(TopologySolution s) {
			double result = 0;
			synchronized (lock) {
				for(int dataSource : dataSourceToInputs.keySet()) {
					Set<Integer> aggrs = new HashSet<Integer>();
					for(int input : dataSourceToInputs.get(dataSource)) {
						aggrs.add(s.inputToAggregator[input]);
					}
					if(aggrs.size() > 1)
						result += aggrs.size() - 1;
				}
			}
			return result;
		}
		protected double getAggrCardinalityDifferences(TopologySolution s) {
			return new EqualInputsPerAggrNH(s).getCardinalityDiff();
		}
	}

	public void performanceInfosUpdated(SortedAggregatorsList l) {
		synchronized (lock) {
			if(eventDataSources.isEmpty() || !isActive || !running) return;
			Map<Integer,Double> dsToTransferRate = new HashMap<Integer, Double>();
			for(AggregatorPerformanceInfo i : l.getPerformanceInfos()) {
				for(StreamDataRate r : i.getStreamDataRate()) {
					int dataSource = eventDataSources.indexOf(r.getStream());
					if(dataSource < 0) {
						logger.warn("Input " + r.getStream().input + " not found in " + eventDataSources);
//						try {
//							System.out.println(util.toString(r.input.input));
//							for(EventingInput ei : eventDataSources)
//								System.out.println(" --> " + util.toString(ei));
//						} catch (Exception e) {
//							e.printStackTrace();
//						}
					} else {
						Double old = dsToTransferRate.get(dataSource);
						if(old != null && old.doubleValue() != r.getDataRate().doubleValue()) {
							if(logger.isDebugEnabled()) logger.debug("Data rate for event stream " + 
									dataSource + " already exists, but has different value: " + old + 
									" != " + r.getDataRate() + ". Taking the bigger value.");
							if(r.getDataRate().doubleValue() > r.getDataRate().doubleValue())
								dsToTransferRate.put(dataSource, r.getDataRate());
						} else {
							dsToTransferRate.put(dataSource, r.getDataRate());
						}
					}
				}
			}
			for(Integer ds : dsToTransferRate.keySet()) {
				this.dataSourceToTransferRate.put(ds, dsToTransferRate.get(ds));
			}
		}
	}

	public synchronized void setup(List<Topology> topologies) throws Exception {
		aggregators = Registry.getRegistryProxy().getAggregatorNodes();

		bestSolution = new TopologySolution();
		bestValue = Double.MAX_VALUE;
		eventDataSources = new LinkedList<InputWrapper>();
		eventInputs = new LinkedList<Pair<InputWrapper,PreparationQuery>>();
		dependingInputToProvidingInputs = new ArrayList<List<Integer>>();
		inputToDataSource = new HashMap<Integer,Integer>();
		synchronized (lock) {
			dataSourceToInputs = new HashMap<Integer, List<Integer>>();			
		}
		inputToRequests = new LinkedList<AggregationRequest>();
		topologyMap = new HashMap<String, Topology>();

		List<Integer> inputToAggrIDs = new LinkedList<Integer>();

		int numInputs = 0;
		for(Topology t : topologies) {
			
			topologyMap.put(t.getTopologyID(), t);

			for(AggregatorNode a : t.getTargetServiceRequests().keySet()) {
				HashMap<DataServiceNode,LinkedList<AggregationRequest>> aggrRequests = t.getTargetServiceRequests().get(a);
				if(!aggregators.contains(a)) {
					aggregators.add(a);
				}
				int aggrIndex = aggregators.indexOf(a);
				if(aggrIndex < 0) 
					logger.warn("Aggregator " + a + " not found in " + aggregators);
				
				
				for(DataServiceNode d : aggrRequests.keySet()) {
					List<AggregationRequest> requests = aggrRequests.get(d);
					for(int i = 0; i < requests.size(); i ++) {

						AggregationRequest req = requests.get(i);
						req.setTopologyID(t.getTopologyID());

						for(AbstractInput input : req.getAllInputs()) {

							synchronized (lock) {
								if(input instanceof EventingInput) {

									numInputs ++;

									int sourceIndex = eventDataSources.indexOf(new InputWrapper(input));
									
									if(sourceIndex < 0) {
										eventDataSources.add(new InputWrapper(input));
										sourceIndex = eventDataSources.size() - 1;
										dataSourceToInputs.put(sourceIndex, new LinkedList<Integer>());
									}
	
									List<PreparationQuery> queries = req.getQueries().getPreparationQueries(input);
									if(queries.size() != 1)
										logger.info("Eventing input has " + queries.size() + " preparation queries. Expected: 1.");
									
									PreparationQuery q = queries.size() > 0 ? queries.get(0) : null;
									Pair<InputWrapper,PreparationQuery> pair = 
										new Pair<InputWrapper, PreparationQuery>(new InputWrapper(input), q);
									eventInputs.add(pair);
									dependingInputToProvidingInputs.add(new LinkedList<Integer>());
									inputToRequests.add(req);
									inputToAggrIDs.add(aggrIndex);
	
									int inputIndex = eventInputs.size() - 1;
									inputToDataSource.put(inputIndex, sourceIndex);
									dataSourceToInputs.get(sourceIndex).add(inputIndex);
								}
							}
						}
					}
				}
			}
		}
		if(!silent) System.out.println("numInputs: " + numInputs);

		for(Topology t : topologies) {
			for(AggregatorNode a : t.getTargetServiceRequests().keySet()) {
				HashMap<DataServiceNode,LinkedList<AggregationRequest>> aggrRequests = t.getTargetServiceRequests().get(a);
				for(DataServiceNode d : aggrRequests.keySet()) {
					List<AggregationRequest> requests = aggrRequests.get(d);
					for(int i = 0; i < requests.size(); i ++) {
						AggregationRequest req = requests.get(0);
						//int numDep = 0;
						for(AbstractInput depending : req.getAllInputs()) {
							if(depending instanceof EventingInput) {
								
								List<PreparationQuery> queries = req.getQueries().getPreparationQueries(depending);
								if(queries.size() != 1)
									logger.info("Eventing input has " + queries.size() + " preparation queries. Expected: 1.");
								PreparationQuery q = queries.size() > 0 ? queries.get(0) : null;
								
								//int dependInpID = getInputIndex(new Pair<InputWrapper, 
								//		PreparationQuery>(new InputWrapper(depending), q));
								int dependInpID = eventInputs.indexOf(new Pair<InputWrapper, 
										PreparationQuery>(new InputWrapper(depending), q));
								
								for(DataDependency dep : req.getAllDataDependencies(depending)) {
									//numDep++;
									for(int providInpID = 0; providInpID < eventInputs.size(); providInpID ++) {
										AbstractInput providing = eventInputs.get(providInpID).getFirst().input;
										if(providing.request != null && providing.request.equals(depending.request)) {
											if(providing.getExternalID() != null && !providing.getExternalID().trim().isEmpty() && 
													providing.getExternalID().trim().equals("" + dep.getIdentifier())) {
												dependingInputToProvidingInputs.get(dependInpID).add(providInpID);
											}
										}
									}
								}
							}
						}
					}
				}
			}
		}
		
		
		bestSolution.inputToAggregator = new int[inputToAggrIDs.size()];
		for(int i = 0; i < inputToAggrIDs.size(); i ++) {
			bestSolution.inputToAggregator[i] = inputToAggrIDs.get(i);
		}
		originalSolution = new TopologySolution(bestSolution);
		currentSolution = new TopologySolution(bestSolution);
		
		if(!silent) System.out.println("initialization finished. initial solution: " + Arrays.toString(currentSolution.inputToAggregator));
	}
	
	/**
	 * cannot use inputs.indexOf(..) function because equals(..) method
	 * of AbstractInput and subclasses is overridden and has different semantics.
	 * @param in
	 * @return
	 */
	/*private int getInputIndex(Pair<InputWrapper,PreparationQuery> in) {
		for(int i = 0; i < eventInputs.size(); i ++)
			if(eventInputs.get(i).equals(in))
				return i;
		return -1;
	}*/
	
	public List<SolutionListener> getListeners() {
		return listeners;
	}

	public void startOptimization(long maxDurationMillis, OptimizationParameters params) {
		this.params = params;
		startOptimization(maxDurationMillis);
	}
	public void startOptimization(long maxDurationMillis) {
		synchronized (lock) {
			//evaluatedSolutions.clear();
			//try { generateNewSolutionsRequestQueue.put(lock); } catch (InterruptedException e) { e.printStackTrace(); }
			bestSolutionTime = new AtomicLong(System.currentTimeMillis());
			running = true;
			isActive = true;
			timer.schedule(new TimerTask() {
						public void run() {
							running = false;
						}
					}, maxDurationMillis);
		}
	}
	public void stopOptimization() {
		running = false;
		isActive = false;
	}

	public void run() {
		try {
			while(true) {
				try {
					if(isActive) {
						
						if(!running) {
							if(params.automaticallyRestartLoop) {
								if(!silent) System.out.println("(Re-)starting optimization loop...");
								setup(TopologyUtil.collectAllTopologies());
								startOptimization(params.loopDurationMS);
							}
						}

						while(running) {
							//generateNewSolutionsRequestQueue.take();

							final List<Iterator<TopologySolution>> neighborhoods = new LinkedList<Iterator<TopologySolution>>();

							for(int hoodSize : new Integer[]{}) {
								Set<Integer> inputIDs = new HashSet<Integer>();
								while(inputIDs.size() < hoodSize)
									inputIDs.add((int)(Math.random() * (double)eventInputs.size()));
								SwapAggregatorNH hood = new SwapAggregatorNH(currentSolution, inputIDs.toArray(new Integer[0]));
								neighborhoods.add(hood);
							}
							if(params.penaltyForRedundantDataSources > 0) {
								for(int dataSourceID : dataSourceToInputs.keySet()) {
									if(dataSourceToInputs.get(dataSourceID).size() > 1)
										neighborhoods.add(new BundleInputsNH(currentSolution, dataSourceID));
								}
							}
							if(params.penaltyForAggrCardinalityDiff > 0) {
								neighborhoods.add(new EqualInputsPerAggrNH(currentSolution));
							}

							for(final Iterator<TopologySolution> hood : neighborhoods) {

								Runnable task = new Runnable() {
									@Override
									public void run() {
										activeNeighborhoods.incrementAndGet();
										//System.out.println("active hoods: " + activeNeighborhoods.get());
										try {
											if(running && isActive) {
												//int solutionCount = 0;
												while(hood.hasNext()) {
													synchronized (lock) {
														if(!running || !isActive)
															break;
													}
													//solutionCount++;
													TopologySolution s = new TopologySolution(hood.next());
													synchronized(lock) {
														double value = eval.evaluate(s);

														if(value >= 0) {
															if(value < bestValue) {
																if(!silent) System.out.println("Best Value: " + value + " - " + Arrays.toString(s.inputToAggregator));
																if(!silent) System.out.println(s.getInputsByAggr(inputToDataSource));
																if(printDebugOutput && !silent) {
																	System.out.println("Num migrations: " + s.getNumMigrationsFrom(originalSolution));
																	System.out.println("Num dependencies: " + eval.getDependenciesBetweenAggrs(s));
																	System.out.println("Num redundancies: " + eval.getDataSourcesOnMultipleAggregators(s));
																	System.out.println("Num cardinality diff: " + eval.getAggrCardinalityDifferences(s));
																	System.out.println("Inputs: " + s.getInputsByAggr(inputToDataSource));
																	System.out.println("aggrs : " + Arrays.toString(s.inputToAggregator));
																}
																bestValue = value;
																bestSolution = new TopologySolution(s);
																currentSolution = new TopologySolution(s);
																bestSolutionTime.set(System.currentTimeMillis());
																for(SolutionListener l : listeners)
																	l.onSolution(bestSolution, value);
															}
														}
													}
												}
											}
										} catch (SolutionEvaluationException e) {
											// swallow
										}catch (Exception e) {
											logger.warn("Exception in optimization (neighborhood generator loop.)", e);
										}
										activeNeighborhoods.decrementAndGet();
										//System.out.println("active hoods now: " + activeNeighborhoods.get());
									}
								};
								executor.execute(task);
							}

							Thread.sleep(1000);
							while(activeNeighborhoods.get() > 3) {
								Thread.sleep(500);
							}
							
							// do some random flips
							if((System.currentTimeMillis() - bestSolutionTime.get()) > 10*1000) {
								int numFlips = 0;
								for(int i = 0; i < eventInputs.size(); i ++) {
									if(Math.random() < 0.3) {
										numFlips ++;
										currentSolution.inputToAggregator[i] = (int)(Math.random() * (double)aggregators.size());
									}
								}
								if(!silent) System.out.println("done " + numFlips + " random flips of " + eventInputs.size() + " inputs");
							}
						}

						if(bestSolution.inputToAggregator != null) {
							if(!params.automaticallyRestartLoop)
								isActive = false;
							if(!silent) System.out.println("best solution: aggrs : " + Arrays.toString(bestSolution.inputToAggregator));
							if(params.doApplyMigrations ) {
								List<Integer> migrations = bestSolution.getRequestsToMigrate(originalSolution.inputToAggregator);
								System.out.println("Performing migrations of inputs: " + migrations);
								synchronized (lock) {
									for(int inputID : migrations) {
										AggregatorNode oldAggr = aggregators.get(originalSolution.inputToAggregator[inputID]);
										AggregatorNode newAggr = aggregators.get(bestSolution.inputToAggregator[inputID]);
										AggregatorNodeProxy proxy = new AggregatorNodeProxy(newAggr);
										AggregationRequest request = inputToRequests.get(inputID);
										long before = System.currentTimeMillis();
										Topology t = topologyMap.get(request.getTopologyID());
										int bufferSize = proxy.inheritInput(request, t, oldAggr);
										t.moveTargetServiceRequest(request, oldAggr, newAggr);
										long after = System.currentTimeMillis();
										logger.info("Event stream migration took " + (after - before) + "ms, buffer size was " + bufferSize);
									}
								}
							}
						}
						Thread.sleep(1000);

					} else {
						Thread.sleep(3000);
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public double getBestValue() {
		return bestValue;
	}
	
	@SuppressWarnings("all")
	public static Topology getDefaultTopology(int numInputs, int inputPoolSize, int numDependencies){
		Topology t = new Topology();
		try {
			List<AggregatorNode> aggregators = Registry.getRegistryProxy().getAggregatorNodes();

			String requestID = UUID.randomUUID().toString();
			List<DataServiceNode> dataServices = Registry.getRegistryProxy().getDataServiceNodes("Eventing");
			
			List<AggregationRequest> requests = new LinkedList<AggregationRequest>();
			for(int i = 0; i < inputPoolSize; i ++) {
				AggregationRequest r1 = new AggregationRequest(-1, requestID, null, new AbstractInput.RequestInputs(), null);
				EventingInput in = new EventingInput();
				in.setServiceURL(dataServices.get((int)(Math.random() * (double)dataServices.size())).getEPR().getAddress());
				in.setServiceURL(in.getServiceURL()
						+ ("#foo" + i));
				in.setTheContent(util.xml.toElement(
						"<config><wse:Filter xmlns:wse=\"http://schemas.xmlsoap.org/ws/2004/08/eventing\">" +
						i + "</wse:Filter></config>"));
				r1.getAllInputs().add(in);
				requests.add(r1);
			}
			
			List<DataServiceNode> serviceNodes = Registry.getRegistryProxy().getDataServiceNodes("Eventing");
			
			Map<AggregatorNode, HashMap<DataServiceNode,LinkedList<AggregationRequest>>> aggregatorServices = t.getTargetServiceRequests();
			int aggr = (int)(Math.random() * (double)aggregators.size());
			for(int i = 0; i < numInputs; i ++) {
				int serv = (int)(Math.random() * (double)serviceNodes.size());
				int req = (int)(Math.random() * (double)requests.size());
				AggregationRequest request = util.xml.toJaxbObject(AggregationRequest.class, util.xml.toElement(requests.get(req)));
				request.setRequestID(requestID);
				request.getAllInputs().get(0).setExternalID("" + i);
				request.getAllInputs().get(0).request = request;
				request.getQueries().addPreparationQuery("" + i, "$input");
				if(i < numDependencies) {
					Element content = util.xml.toElement("<el>$" + (i + 1) + "{//foo/bar}</el>");
					request.getAllInputs().get(0).setTheContent(content);
				}
				AggregatorNode a = aggregators.get(aggr);
				if(!aggregatorServices.containsKey(a))
					aggregatorServices.put(a, new HashMap<DataServiceNode,LinkedList<AggregationRequest>>());
				if(!aggregatorServices.get(a).containsKey(serviceNodes.get(serv)))
					aggregatorServices.get(a).put(serviceNodes.get(serv), new LinkedList<AggregationRequest>());
				aggregatorServices.get(a).get(serviceNodes.get(serv)).add(request);
			}
			
		} catch (Exception e) {
			e.printStackTrace();
		}
		return t;
	}
	
	public static List<Topology> getDefaultTopologies(int num) {
		List<Topology> topologies = new LinkedList<Topology>();
		for(int i = 0; i < num; i ++) {
			Topology t = getDefaultTopology(5, 20, 0);
			t.setTopologyID(UUID.randomUUID().toString());
			topologies.add(t);
		}
		return topologies;
	}
	
	public static TopologyOptimizerVNS getInstance() {
		return instance;
	}

}
