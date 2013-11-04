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

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.apache.log4j.Logger;

import at.ac.tuwien.infosys.aggr.request.AbstractOutput;
import at.ac.tuwien.infosys.events.EventTypeRepository;
import at.ac.tuwien.infosys.util.Util;

import com.espertech.esper.adapter.AdapterState;
import com.espertech.esper.adapter.AdapterStateManager;
import com.espertech.esper.adapter.Subscription;
import com.espertech.esper.client.EPException;
import com.espertech.esper.client.EPServiceProvider;
import com.espertech.esper.client.EventBean;

public abstract class AbstractOutputAdapter<T extends AbstractOutput> implements
		EsperMonitoringOutputAdapter {

	protected static final Logger LOGGER = Util.getLogger(AbstractOutputAdapter.class);

	/**
	 * Manages adapter state.
	 */
	private final AdapterStateManager stateManager = new AdapterStateManager();

	private Map<String, Subscription> subscriptionMap = new HashMap<String, Subscription>();

	private EPServiceProvider spi;

	protected Object syncRoot = new Object();

	private long startTime;
	
	private String externalID;

	private EventTypeRepository repo;
		
	public String getExternalID(){
		return externalID;
	}

	public Map<String, Subscription> getSubscriptionMap() {
		return subscriptionMap;
	}

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

	public Subscription getSubscription(String subscriptionName) {
		if (subscriptionName == null) {
			return null;
		}
		return subscriptionMap.get(subscriptionName);
	}

	@Override
	public EPServiceProvider getEPServiceProvider() {
		return spi;
	}

	@Override
	public void setEPServiceProvider(EPServiceProvider epService) {
		if (epService == null) {
			throw new IllegalArgumentException("Null service provider");
		}		
		spi = epService;
	}
	
	@Override
	public void setEventTypeRepository(EventTypeRepository repo)
	{
		this.repo = repo;
	}
	
	@Override
	public EventTypeRepository getEventTypeRepository()
	{
		return repo;
	}

	@Override
	public void start() throws EPException {
		synchronized (syncRoot) {
			if (LOGGER.isDebugEnabled()) {
				LOGGER.debug(".start");
			}
			if (spi.getEPRuntime() == null) {
				throw new EPException(
						"Attempting to start an Adapter that hasn't had the epService provided");
			}

			startTime = System.currentTimeMillis();
			if (LOGGER.isDebugEnabled()) {
				LOGGER.debug(".start startTime==" + startTime);
			}

			stateManager.start();
			Iterator<Map.Entry<String, Subscription>> it = subscriptionMap
					.entrySet().iterator();
			while (it.hasNext()) {
				it.next().getValue().registerAdapter(this);
			}
		}
	}

	@Override
	public void pause() throws EPException {
		synchronized (syncRoot) {
			if (LOGGER.isDebugEnabled()) {
				LOGGER.debug(".pause");
			}
			stateManager.pause();
		}
	}

	@Override
	public void resume() throws EPException {
		synchronized (syncRoot) {
			if (LOGGER.isDebugEnabled()) {
				LOGGER.debug(".resume");
			}
			stateManager.resume();
		}
	}

	@Override
	public void stop() throws EPException {
		synchronized (syncRoot) {
			if (LOGGER.isDebugEnabled()) {
				LOGGER.debug(".stop");
			}
			stateManager.stop();
		}
	}

	@Override
	public void destroy() throws EPException {
		synchronized (syncRoot) {
			if (LOGGER.isDebugEnabled()) {
				LOGGER.debug(".destroy");
			}
			stateManager.destroy();
		}
	}

	@Override
	public AdapterState getState() {
		synchronized (syncRoot) {
			return stateManager.getState();
		}
	}

	@Override
	public void configure(AbstractOutput output) throws Exception{
		@SuppressWarnings("unchecked")
		T out = (T)output;
		externalID = output.getExternalID();
		configureInt(out);
	}
	
	protected abstract void configureInt(T output) throws Exception;
	
	
	public abstract void processEvent(EventBean event);
}
