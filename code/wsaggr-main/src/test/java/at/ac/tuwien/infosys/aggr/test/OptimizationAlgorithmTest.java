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

package at.ac.tuwien.infosys.aggr.test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.Ignore;

import at.ac.tuwien.infosys.aggr.node.AggregatorNode;
import at.ac.tuwien.infosys.aggr.node.DataServiceNode;
import at.ac.tuwien.infosys.aggr.node.Registry;
import at.ac.tuwien.infosys.aggr.strategy.Topology;
import at.ac.tuwien.infosys.aggr.strategy.TopologyOptimizerVNS;
import at.ac.tuwien.infosys.aggr.strategy.TopologySolution;
import at.ac.tuwien.infosys.aggr.strategy.TopologySolution.SolutionListener;
import at.ac.tuwien.infosys.aggr.strategy.TopologyOptimizerVNS.OptimizationParameters;
import at.ac.tuwien.infosys.aggr.util.ServiceStarter;
import at.ac.tuwien.infosys.util.test.GenericTestResult;
import at.ac.tuwien.infosys.util.test.GenericTestResult.IterationResult;
import at.ac.tuwien.infosys.util.test.GenericTestResult.ResultType;

@Ignore
public class OptimizationAlgorithmTest implements SolutionListener {

	private TopologyOptimizerVNS opt = TopologyOptimizerVNS.getInstance();
	private IterationResult test = new IterationResult();
	private int numIterations = 10;
	private int iterationDuration = 1000*20;
	private LinkedBlockingQueue<Map<TopologySolution,Double>> solutions = new LinkedBlockingQueue<Map<TopologySolution,Double>>();

	public void onSolution(TopologySolution s, double value) {
		try {
			solutions.put(Collections.singletonMap(s, value));
		} catch (InterruptedException e) { e.printStackTrace(); }
	}
	
	public List<Topology> initTopologies(int numAggregators, int numQueries, int numInputsPerQuery, 
			int numDataServices) throws Exception {
		while(Registry.getRegistryProxy().getAggregatorNodes().size() > numAggregators)
			Registry.getRegistryProxy().removeAggregatorNode(Registry.getRegistryProxy().getAggregatorNodes().get(0));
		while(Registry.getRegistryProxy().getAggregatorNodes().size() < numAggregators) {
			AggregatorNode a = new AggregatorNode(Registry.getRegistryProxy().getAggregatorNodes().get(0).getEPR(), false);
			a.getEPR().setAddress(a.getEPR().getAddress() + "#" + UUID.randomUUID().toString());
			Registry.getRegistryProxy().addAggregatorNode(a);
		}
		List<Topology> list = new LinkedList<Topology>();
		for(int i = 0; i < numQueries; i ++) {
			Topology t = TopologyOptimizerVNS.getDefaultTopology(
					numInputsPerQuery, numDataServices, 0);
			list.add(t);
		}
		return list;
	}
	
	public void runTest() throws Exception {
		
		opt.getListeners().add(this);
		
		List<List<Integer>> setups = new LinkedList<List<Integer>>();
		//setups.add(Arrays.asList(10, 10, 3, 10));
		setups.add(Arrays.asList(10, 20, 5, 20, 5));
//		setups.add(Arrays.asList(20, 1000, 5, 50));
		
		for(int setupsID = 0; setupsID < setups.size(); setupsID ++) {
			List<Integer> setup = setups.get(setupsID);
			
			for(int j = 0; j < numIterations; j ++) {
				
				int numSteps = setup.get(4);
				
				List<Topology> tops = new LinkedList<Topology>();
				
				for(int i = 0; i < numSteps; i ++) {
					
					List<Topology> newTops = initTopologies(setup.get(0), setup.get(1), setup.get(2), setup.get(3));
					tops.addAll(newTops);
					
					opt.setup(tops);
					OptimizationParameters params = new OptimizationParameters();
					// TODO: set parameters
					opt.startOptimization(100*1000, params);
					long start = System.currentTimeMillis();
					double bestValue = Double.MAX_VALUE;
					double bestValueTime = 0;
				
					while(true) {
						long diff = System.currentTimeMillis() - start;
						if(diff > iterationDuration)
							break;
						Map<TopologySolution,Double> s = solutions.poll(500, TimeUnit.MILLISECONDS);
						if(s != null) {
							double value = s.values().iterator().next();
							if(value < bestValue) {
								bestValue = value;
								bestValueTime = System.currentTimeMillis();
							}
							if(value < Double.MAX_VALUE)
								test.addEntry("s" + setupsID + "t" + i + "value", value);
						}
					}
					opt.stopOptimization();
					if(bestValueTime > 0)
						test.addEntry("s" + setupsID + "time", (bestValueTime-start));
					if(bestValue < Double.MAX_VALUE)
						test.addEntry("s" + setupsID + "value", bestValue);
				}
			}
		}
	}
	
	public static void main(String[] args) throws Exception {

		boolean doCreateGraphs = true;

		if(doCreateGraphs) {
			GenericTestResult result = GenericTestResult.load("etc/results/algorithmTestResults.xml");
			List<String> levels = result.getAllLevelIDsByPattern("s0t([0-9]+)value",1);
			result.createGnuplot(levels, new String[]{"s0t<level>value"}, new String[]{"Small Setup"},
					ResultType.MEAN, "Time (seconds)", "Target Value", "doc/doa2011/img/testAlgorithms.pdf",
					"set yrange [0:*]");
			System.exit(0);
		}

		ServiceStarter.startRegistry();
		Registry.getRegistryProxy().addAggregatorNode(new AggregatorNode(
				Registry.getRegistryProxy().getEPR(), false));
		Registry.getRegistryProxy().addDataServiceNode("Eventing", 
				new DataServiceNode(Registry.getRegistryProxy().getEPR()));
		
		OptimizationAlgorithmTest test = new OptimizationAlgorithmTest();
		final GenericTestResult result = new GenericTestResult();
		Runtime.getRuntime().addShutdownHook(new Thread() {
			public void run() {
				System.out.println("Saving result file...");
				result.save("etc/results/algorithmTestResults.xml");
			}
		});
		test.test = result.newIteration();
		test.runTest();
		
	}

}
