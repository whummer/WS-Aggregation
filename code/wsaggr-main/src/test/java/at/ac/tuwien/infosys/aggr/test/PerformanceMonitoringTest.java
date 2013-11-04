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

import at.ac.tuwien.infosys.aggr.node.Registry;
import at.ac.tuwien.infosys.aggr.performance.AggregatorPerformanceInfo;
import at.ac.tuwien.infosys.aggr.proxy.AggregatorNodeProxy;
import at.ac.tuwien.infosys.aggr.util.ServiceStarter;

@org.junit.Ignore
public class PerformanceMonitoringTest {

	public static void main(String[] args) throws Exception {
		ServiceStarter.setupDefault(3);
		
		Thread.sleep(3000);
		
		for(int i = 0; i < 30; i ++) {
			AggregatorNodeProxy p = new AggregatorNodeProxy(
					Registry.getRegistryProxy().getRandomAggregatorNode());
			AggregatorPerformanceInfo pi = p.getPerformanceInfo(null);
			System.out.println(pi);
			Thread.sleep(1000);
		}
		
		System.exit(0);
	}
	
}
