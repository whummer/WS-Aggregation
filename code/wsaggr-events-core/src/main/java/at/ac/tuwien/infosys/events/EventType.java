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

package at.ac.tuwien.infosys.events;

import java.util.HashMap;
import java.util.Map;


public class EventType {

	private String type;
	
	private String parentType;
	
	private String eventClass = Event.class.getName();
	
	private Map<String, String> properties = new HashMap<String, String>();
	
	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public String getParentType() {
		return parentType;
	}
	public void setParentType(String parentType) {
		this.parentType = parentType;
	}

	public String getEventClass() {
		return eventClass;
	}
	public void setEventClass(String eventClass) {
		this.eventClass = eventClass;
	}

	public Map<String, String> getProperties() {
		return properties;
	}
	public void setProperties(Map<String, String> properties) {
		this.properties = properties;
	}
	
}

