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
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class TopologySolution {

	public int[][] aggregatorToAggregators;
	public int[] inputToAggregator;

	public interface SolutionListener {
		void onSolution(TopologySolution s, double value);
	}

	public TopologySolution() { }
	public TopologySolution(TopologySolution toCopy) {
		if(toCopy == null)
			return;
		if(toCopy.aggregatorToAggregators != null) {
			aggregatorToAggregators = new int[toCopy.aggregatorToAggregators.length][];
			for(int i = 0; i < aggregatorToAggregators.length; i ++)
				aggregatorToAggregators[i] = Arrays.copyOf(toCopy.aggregatorToAggregators[i], toCopy.aggregatorToAggregators[i].length);
		}
		inputToAggregator = Arrays.copyOf(toCopy.inputToAggregator, toCopy.inputToAggregator.length);
	}

	public int getNumMigrationsFrom(TopologySolution old) {
		int[] ass1 = old.inputToAggregator;
		int[] ass2 = inputToAggregator;
		int val = 0;
		for(int i = 0; i < ass1.length; i ++) {
			if(ass1[i] >= 0 && ass1[i] != ass2[i]) {
				val ++;
			}
		}
		return val;
	}

	public Map<Integer,List<Integer>> getInputsByAggr() {
		Map<Integer,List<Integer>> result = new HashMap<Integer, List<Integer>>();
		for(int i = 0; i < inputToAggregator.length; i ++) {
			if(!result.containsKey(inputToAggregator[i]))
				result.put(inputToAggregator[i], new LinkedList<Integer>());
		}
		return result;
	}
	public Map<Integer,Map<Integer,Integer>> getInputsByAggr(Map<Integer, Integer> inputToDataSource) {
		Map<Integer,Map<Integer,Integer>> result = new HashMap<Integer,Map<Integer,Integer>>();
		for(int i = 0; i < inputToAggregator.length; i ++) {
			if(!result.containsKey(inputToAggregator[i])) {
				result.put(inputToAggregator[i], new HashMap<Integer, Integer>());
			}
			int dataSource = inputToDataSource.get(i);
			if(!result.get(inputToAggregator[i]).containsKey(dataSource)) {
				result.get(inputToAggregator[i]).put(dataSource, 0);
			}
			result.get(inputToAggregator[i]).put(dataSource, result.get(inputToAggregator[i]).get(dataSource) + 1);
		}
		return result;
	}
	public List<Integer> getRequestsToMigrate(int[] oldRequestToAggregator) {
		List<Integer> result = new LinkedList<Integer>();
		for(int i = 0; i < oldRequestToAggregator.length; i ++) {
			if(oldRequestToAggregator[i] != inputToAggregator[i]) {
				result.add(i);
			}
		}
		return result;
	}
	public void clearTopology() {
		for(int i = 0; i < aggregatorToAggregators.length; i ++)
			for(int j = 0; j < aggregatorToAggregators[i].length; j ++)
				aggregatorToAggregators[i][j] = 0;
	}
}