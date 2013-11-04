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

package net.esper.adapter.ws;

import java.util.concurrent.Executor;

import org.apache.log4j.Logger;

import at.ac.tuwien.infosys.aggr.request.AbstractInput;
import at.ac.tuwien.infosys.events.Event;
import at.ac.tuwien.infosys.events.ws.WSEvent;
import at.ac.tuwien.infosys.monitoring.esper.AbstractInputAdapter;
import at.ac.tuwien.infosys.util.Util;

import com.espertech.esper.adapter.AdapterState;
import com.espertech.esper.client.EPException;

public abstract class AbstractInputWSAdapter<T extends AbstractInput> extends AbstractInputAdapter<T>{
	
	private static final Logger LOGGER = Util.getLogger(AbstractInputWSAdapter.class);
	
	protected final EventingInputNode node;

	
	public AbstractInputWSAdapter(){
		this.node = new EventingInputNode(this);
	}
	
	public void processEvent(WSEvent soapEvent) throws Exception {
		LOGGER.debug(String.format("Received event '%s' @ '%s'.", soapEvent.getIdentifier(), node.getEPR().getAddress()));	
		if (getState() != AdapterState.STARTED) {
			return;
		}
		if (getEPServiceProvider() == null) {
			LOGGER.warn(".onMessage Event message not sent to engine, service provider not set yet, message ack'd");
			return;
		}
		final Event event = WSEvent.fromWSEvent(getEventTypeRepository(), soapEvent);
		if(LOGGER.isDebugEnabled()) {
			LOGGER.debug(String.format("InputAdapter %s received event with id %s", getExternalID(), event.getIdentifier()));
		}
		getExecutor().execute(new SendEventRunnable(event));		
	}
	
	protected abstract void sendEvent(Event event) throws Exception;
	
	
	private class SendEventRunnable implements Runnable {

		private final Event event;
		
		public SendEventRunnable(Event event){
			this.event = event;
		}
		
		public Event getEvent(){
			return event;
		}
		
		@Override
		public void run() {
			try {
				sendEvent(event);
			} catch (EPException ex) {
				LOGGER.error(".onMessage exception", ex);
				if (getState() == AdapterState.STARTED) {
					stop();
				} else {
					destroy();
				}
			} catch (Exception ex){
				LOGGER.error(".onMessage exception", ex);
			}
		}		
	}
}
