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

package at.ac.tuwien.infosys.monitoring.esper;

import at.ac.tuwien.infosys.events.Event;

public class ChangeConfigEvent extends Event {

	public static final String ACTION_ACTIVATE = "activate";
	public static final String ACTION_DEACTIVATE = "deactivate";
	
	private long monitoringSet;
	
	private String action;

	public long getMonitoringSet() {
		return monitoringSet;
	}

	public void setMonitoringSet(long monitoringSet) {
		this.monitoringSet = monitoringSet;
	}

	public String getAction() {
		return action;
	}

	public void setAction(String action) {
		this.action = action;
	}	
	
	
	
}
