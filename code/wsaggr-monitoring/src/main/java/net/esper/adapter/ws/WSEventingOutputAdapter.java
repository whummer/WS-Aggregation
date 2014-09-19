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

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.apache.log4j.Logger;

import at.ac.tuwien.infosys.aggr.request.EventingOutput;
import at.ac.tuwien.infosys.events.ws.WSEvent;
import io.hummer.util.Util;

import com.espertech.esper.adapter.Subscription;
import com.espertech.esper.client.EPException;
import com.espertech.esper.client.EventBean;


public class WSEventingOutputAdapter extends AbstractOutputWSAdapter<EventingOutput> {

	private static final Logger LOGGER = Util.getLogger(WSEventingOutputAdapter.class);

	private static final long serialVersionUID = 5250432339824272116L;

	private Map<String, Subscription> subscriptionMap = new HashMap<String, Subscription>();


	private EventingOutputNode node;

	private EventingOutput output;
	
	public WSEventingOutputAdapter()
	{
		this.node = new EventingOutputNode(null);
	}

	@Override
	public void processEventInt(EventBean eventBean) {	
		try{
			final WSEvent wsEvent = WSEvent.toWSEvent(eventBean);			
			LOGGER.debug(String.format("Received event '%s'.", wsEvent.getIdentifier()));
			node.processEvent(wsEvent);
		} catch (Exception e) {
			LOGGER.error("Error while sending the event.", e);
		}

	}
	
	@Override
	public Map<String, Subscription> getSubscriptionMap() {
		return subscriptionMap;
	}

	@Override
	public void setSubscriptionMap(Map<String, Subscription> subscriptionMap) {
		this.subscriptionMap = subscriptionMap;
		// In case an name has not been set for each subscription
		Iterator<Map.Entry<String, Subscription>> it = subscriptionMap
				.entrySet().iterator();
		for (String name : subscriptionMap.keySet()) {
			Subscription subscription = subscriptionMap.get(name);
			subscription.setSubscriptionName(name);
		}
	}

	@Override
	public Subscription getSubscription(String subscriptionName) {
		if (subscriptionName == null) {
			return null;
		}
		return subscriptionMap.get(subscriptionName);
	}


	@Override
	protected void configureInt(EventingOutput output) throws Exception {
		node.configure(output);
	}	

	
	@Override
	public void start() throws EPException {
		synchronized (syncRoot) {			
			if (LOGGER.isDebugEnabled()) {
				LOGGER.debug(".start");
			}
			if (getEPServiceProvider().getEPRuntime() == null) {
				throw new EPException(
						"Attempting to start an Adapter that hasn't had the epService provided");
			}
			try {
				LOGGER.info(String.format("OutputAdapter %s started @ %s.",
						this.getExternalID(),
						node.getEPR().getAddress()));
				
				super.start();
				
				Iterator<Map.Entry<String, Subscription>> it = subscriptionMap
						.entrySet().iterator();
				while (it.hasNext()) {
					it.next().getValue().registerAdapter(this);
				}
			} catch (Exception e) {
				throw new EPException(e);
			}
		}
	}

	@Override
	public void pause() throws EPException {
		synchronized (syncRoot) {
			super.pause();
		}
	}

	@Override
	public void resume() throws EPException {
		synchronized (syncRoot) {
			super.resume();
		}
	}

	@Override
	public void stop() throws EPException {
		synchronized (syncRoot) {
			super.stop();
		}
	}

	@Override
	public void destroy() throws EPException {
		synchronized (syncRoot) {
			super.destroy();
		}
	}
}
