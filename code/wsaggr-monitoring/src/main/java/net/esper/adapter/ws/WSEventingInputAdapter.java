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

import javax.help.UnsupportedOperationException;
import javax.jws.WebService;

import org.apache.log4j.Logger;

import at.ac.tuwien.infosys.aggr.request.EventingInput;
import at.ac.tuwien.infosys.events.Event;
import at.ac.tuwien.infosys.events.ws.EventSubscribeFilter;
import at.ac.tuwien.infosys.events.ws.EventSubscribeRequest;
import at.ac.tuwien.infosys.events.ws.WSEvent;
import io.hummer.util.Util;
import io.hummer.util.ws.EndpointReference;

import com.espertech.esper.client.EPException;

@WebService(targetNamespace = WSEvent.NAMESPACE)
public class WSEventingInputAdapter extends AbstractInputWSAdapter<EventingInput> {

	private static final Logger LOGGER = Util.getLogger(WSEventingInputAdapter.class);

	private EventingInput input;

	private Object subscribeTicket;
	
	@Override
	protected void sendEvent(Event event) throws Exception
	{
		getEPServiceProvider().getEPRuntime().sendEvent(event);
	}

	@Override
	protected void configureInt(EventingInput input) throws Exception {
		this.input = input;
		node.setEPR(input.getEndTo());
	}
	
	public void start() throws EPException {
		synchronized (syncRoot) {
			LOGGER.debug(".start");
			try {
				node.start();
				LOGGER.info("InputAdapter started @ " + node.getEPR().getAddress());	
				
				if (subscribeTicket == null) {
					final EventSubscribeRequest request = new EventSubscribeRequest();
					final EndpointReference epr = new EndpointReference(node.getEPR());
					request.setEndTo(epr);

					if(input.getFilter() != null){
						final EventSubscribeFilter filter = new EventSubscribeFilter(input.getFilter());
						request.setFilter(filter);
					}

					throw new UnsupportedOperationException("TODO must be implemented!");
					//EndpointReference serviceEPR = new EndpointReference(new URL(input.serviceURL));
					//final EventSubscribeResponse response = WebServiceClientUtil.execute(serviceEPR, request, EventSubscribeResponse.class);
					
					//subscribeTicket = response.getSubscriptionManager().getReferenceParameters().getByName("Identifier").getTextContent();
					//LOGGER.debug(String.format(" '%s' started with ticket '%s'.", node.getEPR().getAddress(), subscribeTicket));
				}

				super.start();
			} catch (Exception e) {
				throw new EPException(e);
			}
		}

	}

	public void pause() throws EPException {
		synchronized (syncRoot) {
			super.pause();
		}
	}

	public void resume() throws EPException {
		synchronized (syncRoot) {
			super.resume();
		}
	}

	public void stop() throws EPException {
		synchronized (syncRoot) {
			if (subscribeTicket != null) {
				//TODO unsubscribe
			}
			super.stop();
		}
	}

	public void destroy() throws EPException {
		LOGGER.debug(".destroy");
		try {
			node.destroy();
			LOGGER.info("InputAdapter destroyed @ " + node.getEPR().getAddress());	
			super.destroy();
		} catch (Exception e) {
			throw new EPException(e);
		}
	}
	
}
