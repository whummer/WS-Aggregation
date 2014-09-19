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

package at.ac.tuwien.infosys.aggr.cloud;

import org.apache.log4j.Logger;

import at.ac.tuwien.infosys.aggr.node.Registry;
import at.ac.tuwien.infosys.aggr.proxy.RegistryProxy;
import io.hummer.util.Configuration;
import io.hummer.util.Util;

public class Deployment {

	public static final Logger logger = Util.getLogger(Deployment.class);
	public static final Util util = new Util();
	
	public static void ensureAggregatorCount(int count) throws Exception {
		
		int countNow = Registry.getRegistryProxy().getAggregatorNodes().size();
		if(countNow >= count)
			return;

		logger.info("Attempting to deploy " + (count - countNow)  + " additional aggregator node(s).");
		
		for(int i = 0; i < (count - countNow); i++) {
			util.cloud.startNewInstance(Configuration.getValue(Configuration.PROP_CLOUD_IMAGE_ID), false);
			Thread.sleep(500); 	// some Eucalyptus installations restrict 
								// subsequent requests without sleep interval.
		}
		
		long lastPrintTime = 0;
		do {
			RegistryProxy.resetCache();
			countNow = Registry.getRegistryProxy().getAggregatorNodes().size();
			if((System.currentTimeMillis() - lastPrintTime) >= 1000*15) {
				logger.info("Waiting for " + (count - countNow) + " instance(s) to launch.");
				lastPrintTime = System.currentTimeMillis();
			}
			Thread.sleep(3000);
		} while(countNow < count);
	}
	
}
