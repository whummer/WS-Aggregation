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

package at.ac.tuwien.infosys.aggr.account;

import java.util.HashMap;
import java.util.Map;

import at.ac.tuwien.infosys.util.NotImplementedException;

/**
 * This class is responsible for monitoring user limits, such as
 * maximum event frequency (events/minute), 
 * maximum data rate (KBytes/second), 
 * maximum active queries,
 * etc.
 * 
 * @author Waldemar Hummer
 */
public class UserLimitsMonitor {

	private static final Map<String,Double> maxEventFreqByRole = new HashMap<String, Double>();
	
	static {
		maxEventFreqByRole.put("admin", Double.MAX_VALUE);
		maxEventFreqByRole.put("user", 60.0);
	}
	
	public boolean isUserWithinRange(String username) {
		//TODO
		throw new NotImplementedException();
	}

}
