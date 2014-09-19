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

import javax.jws.WebService;

import org.apache.log4j.Logger;

import at.ac.tuwien.infosys.events.Event;
import at.ac.tuwien.infosys.events.EventConsumerNode;
import at.ac.tuwien.infosys.events.EventTypeRepository;
import at.ac.tuwien.infosys.events.ws.WSEvent;
import io.hummer.util.Util;
import io.hummer.util.ws.EndpointReference;

@WebService(targetNamespace = WSEvent.NAMESPACE)
public class LoggerEventConsumerNode extends EventConsumerNode {

	private EventTypeRepository eventTypeRep;
	
	private static final Logger LOGGER = Util.getLogger(LoggerEventConsumerNode.class);
	
	private static Util UTIL = new Util();
	
	public LoggerEventConsumerNode(EndpointReference epr, EventTypeRepository eventTypeRep) {
		super(epr);
		this.eventTypeRep = eventTypeRep;
	}

	@Override
	protected void onEvent(WSEvent event) throws Exception {
		Event ev = WSEvent.fromWSEvent(eventTypeRep, event);
		if(LOGGER.isInfoEnabled()){
			String message = UTIL.xml.toString(ev);	
			LOGGER.info(message);
		}
	}
}
