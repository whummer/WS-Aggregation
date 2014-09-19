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

import io.hummer.util.Util;
import io.hummer.util.ws.AbstractNode;

import org.apache.log4j.Logger;

import at.ac.tuwien.infosys.events.ws.WSEvent;
import at.ac.tuwien.infosys.events.ws.WebServiceClientUtil;

public class EventForwarderNode extends AbstractNode {

	private static final Logger LOGGER = Util.getLogger(EventForwarderNode.class);

	private EventTypeRepository eventTypeRepository;

	public EventTypeRepository getEventTypeRepository() {
		return eventTypeRepository;
	}
	public void setEventTypeRepository(EventTypeRepository eventTypeRepository) {
		this.eventTypeRepository = eventTypeRepository;
	}

	public void sendEvent(Event event) throws Exception {
		LOGGER.debug("Sending event '" + event.getIdentifier() + "'.");
		final WSEvent wsEvent = WSEvent.toWSEvent(eventTypeRepository, event);
		WebServiceClientUtil.execute(getEPR(), wsEvent);
	}
	
}
