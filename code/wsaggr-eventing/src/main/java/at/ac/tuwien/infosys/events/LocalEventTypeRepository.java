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

import java.beans.PropertyDescriptor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.beanutils.PropertyUtils;


public class LocalEventTypeRepository implements EventTypeRepository {

	private Map<String, EventType> eventTypes = new HashMap<String, EventType>();
	
	@Override
	public void addEventType(EventType eventType) {
		eventTypes.put(eventType.getType(), eventType);
	}

	@Override
	public EventType getEventType(String eventType) {
		return eventTypes.get(eventType);
	}

	@Override
	public Map<String, EventType> getEventTypes() {
		return Collections.unmodifiableMap(eventTypes);
	}

	@Override
	public <T extends Event> void addEventType(Class<T> eventClazz) {
		final EventType eventType = new EventType();
		eventType.setType(Event.getEventType(eventClazz));
		eventType.setEventClass(eventClazz.getName());
		
		@SuppressWarnings("unchecked")
		final Class<? extends Event> superClazz = (Class<? extends Event>)eventClazz.getSuperclass();
		final List<String> superProperties = new ArrayList<String>();
		for(PropertyDescriptor propDesc : PropertyUtils.getPropertyDescriptors(superClazz)){
			superProperties.add(propDesc.getName());
		}
		if(Event.class.equals(superClazz) == false){
			eventType.setParentType(Event.getEventType(superClazz));			
		}
		
		for(PropertyDescriptor propDesc : PropertyUtils.getPropertyDescriptors(eventClazz)){
			final String propDescName = propDesc.getName();
			if(superProperties.contains(propDescName) == false){
				eventType.getProperties().put(propDescName, propDesc.getPropertyType().getName());
			}
		}
		
		addEventType(eventType);
	}

}
