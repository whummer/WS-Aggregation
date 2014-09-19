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

import java.util.concurrent.Executor;

import org.apache.log4j.Logger;

import at.ac.tuwien.infosys.aggr.request.AbstractInput;
import at.ac.tuwien.infosys.events.EventTypeRepository;
import io.hummer.util.Util;

import com.espertech.esper.adapter.AdapterState;
import com.espertech.esper.adapter.AdapterStateManager;
import com.espertech.esper.client.EPException;
import com.espertech.esper.client.EPServiceProvider;

public abstract class AbstractInputAdapter<T extends AbstractInput> implements EsperMonitoringInputAdapter {

	private static final Logger LOGGER = Util.getLogger(AbstractInputAdapter.class);
	
	private final AdapterStateManager stateManager = new AdapterStateManager();

	private EPServiceProvider spi;
	
	protected Object syncRoot = new Object();

	private long startTime;
	
	private String externalID; 

	private EventTypeRepository repo;

	private Executor executor;
	
	@Override
	public void configure(AbstractInput input) throws Exception{
		@SuppressWarnings("unchecked")		
		T in = (T)input;
		this.externalID = in.getExternalID();
		configureInt(in);
	}
	
	public String getExternalID(){
		return externalID;
	}
	
	
	protected abstract void configureInt(T input) throws Exception;

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
	public void setExecutor(Executor executor){
		this.executor = executor;
	}
	
	@Override
	public Executor getExecutor(){
		return executor;
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
	
}
