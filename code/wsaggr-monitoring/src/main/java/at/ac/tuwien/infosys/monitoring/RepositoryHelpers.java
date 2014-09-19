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

package at.ac.tuwien.infosys.monitoring;

import at.ac.tuwien.infosys.events.Event;
import at.ac.tuwien.infosys.events.EventTypeRepository;
import at.ac.tuwien.infosys.monitoring.config.EventTypeRepositoryConfig;
import at.ac.tuwien.infosys.monitoring.config.NodeRepositoryConfig;
import at.ac.tuwien.infosys.monitoring.esper.ChangeConfigEvent;

public class RepositoryHelpers {

	public static EventTypeRepository generateEventTypeRepository(EventTypeRepositoryConfig config)
		throws ClassNotFoundException, InstantiationException,
		IllegalAccessException {
	
		final Class<?> eventRepositoryClazz = Class.forName(config.getImplementationClazz());		
		final EventTypeRepository retVal = (EventTypeRepository)eventRepositoryClazz.newInstance();
		
		retVal.addEventType(ChangeConfigEvent.class);
		
		for (String eventTypeClazz : config.getEventTypes()) {
			@SuppressWarnings("unchecked")
			final Class<? extends Event> eventClazz = 
				(Class<? extends Event>)Class.forName(eventTypeClazz);			
			retVal.addEventType(eventClazz);
			
		}
		return retVal;
	}
	
	public static NodeRepository generateNodeRepository(NodeRepositoryConfig config)
		throws ClassNotFoundException, InstantiationException,
		IllegalAccessException {
		final Class<?> nodeRepositoryClazz = Class.forName(config.getImplementationClazz());		
		final NodeRepository retVal = (NodeRepository)nodeRepositoryClazz.newInstance();		
		return retVal;
	}
}

