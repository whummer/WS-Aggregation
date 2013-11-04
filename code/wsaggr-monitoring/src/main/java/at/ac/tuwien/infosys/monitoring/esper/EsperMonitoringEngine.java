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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import net.esper.adapter.ws.SOAPEventInputAdapter;
import net.esper.adapter.ws.SOAPEventOutputAdapter;
import net.esper.adapter.ws.WSEventingInputAdapter;
import net.esper.adapter.ws.WSEventingOutputAdapter;

import org.apache.log4j.Logger;

import at.ac.tuwien.infosys.aggr.request.AbstractInput;
import at.ac.tuwien.infosys.aggr.request.AbstractOutput;
import at.ac.tuwien.infosys.aggr.request.EventingInput;
import at.ac.tuwien.infosys.aggr.request.EventingOutput;
import at.ac.tuwien.infosys.events.Event;
import at.ac.tuwien.infosys.events.EventTypeRepository;
import at.ac.tuwien.infosys.monitoring.DeploymentNodeConfig;
import at.ac.tuwien.infosys.monitoring.MonitoringEngine;
import at.ac.tuwien.infosys.monitoring.MonitoringEngineInternal;
import at.ac.tuwien.infosys.monitoring.config.LoggerOutput;
import at.ac.tuwien.infosys.monitoring.config.MonitoringConfigSet;
import at.ac.tuwien.infosys.monitoring.config.MonitoringConfigSubscription;
import at.ac.tuwien.infosys.monitoring.config.NodeConfigChangeOutput;
import at.ac.tuwien.infosys.monitoring.config.NodeConfigInput;
import at.ac.tuwien.infosys.monitoring.config.NodeConfigOutput;
import at.ac.tuwien.infosys.monitoring.config.SOAPEventInput;
import at.ac.tuwien.infosys.monitoring.config.SOAPEventOutput;
import at.ac.tuwien.infosys.util.Util;

import com.espertech.esper.adapter.Adapter;
import com.espertech.esper.adapter.AdapterSPI;
import com.espertech.esper.adapter.AdapterState;
import com.espertech.esper.adapter.AdapterStateManager;
import com.espertech.esper.adapter.InputAdapter;
import com.espertech.esper.adapter.OutputAdapter;
import com.espertech.esper.client.Configuration;
import com.espertech.esper.client.EPException;
import com.espertech.esper.client.EPRuntime;
import com.espertech.esper.client.EPServiceProvider;
import com.espertech.esper.client.EPServiceProviderManager;

public class EsperMonitoringEngine implements MonitoringEngineInternal{

	private static final Logger LOGGER = Util.getLogger(EsperMonitoringEngine.class);
	
	private DeploymentNodeConfig config;
	
	private EventTypeRepository eventTypeRepository;
	
	private List<EsperMonitoringNodeSetEngine> sets = new ArrayList<EsperMonitoringNodeSetEngine>();
	
	private List<InputAdapter> inputAdaptes = new ArrayList<InputAdapter>();
	
	private List<OutputAdapter> outputAdaptes = new ArrayList<OutputAdapter>();

	private Executor executor; 
	private EPServiceProvider epService; 
	private EPRuntime runtime;  	
	
	private final AdapterStateManager stateManager = new AdapterStateManager();

	public EsperMonitoringEngine(){
		this.executor = new ThreadPoolExecutor(1, 1, 1, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());
	}
	
	public Executor getExecutor(){
		return executor;
	}
	
	@Override
	public void setExecutor(Executor executor){
		this.executor = executor;
	}
	
	public EventTypeRepository getEventTypeRepository() {
		return eventTypeRepository;
	}
	
	@Override
	public void setEventTypeRepository(EventTypeRepository eventTypeRepository){
		this.eventTypeRepository = eventTypeRepository;
	}
	
	public DeploymentNodeConfig getConfig(){
		return config;
	}
	
	EPServiceProvider getEPServiceProvider(){
		return epService;
	}
	
	@Override
	public void configure(DeploymentNodeConfig config) throws Exception{
		//TODO check if not started!!!!
		LOGGER.info(String.format("Configuring node %s.", config.getIdentifier()));
		
		this.config = config;					
		
		//Esper configuration
		final Configuration configuration = new Configuration();				
		for (String eventTypeName : eventTypeRepository.getEventTypes().keySet()) {
			configuration.addEventType(eventTypeName, Class.forName(
					eventTypeRepository.getEventType(eventTypeName).getEventClass()));
		}		
		
		final String providerId = "MonitoringEngine" + config.getIdentifier();		
		epService = EPServiceProviderManager.getProvider(providerId, configuration);
		runtime = epService.getEPRuntime();
		
		createOutputAdapters(this.config, getEPServiceProvider());
		
		for (MonitoringConfigSet configSet : this.config.getMonitoringSets()) {
			EsperMonitoringNodeSetEngine nodeSet = new EsperMonitoringNodeSetEngine(this);
			nodeSet.configure(configSet);
			sets.add(nodeSet);
		}
		
		createInputAdapters(this.config, getEPServiceProvider());		
		
	}
	
	@Override
	public void sendEvent(final Event event){		
		LOGGER.debug(String.format("Received event '%s' @ '%s'.", event.getIdentifier(), this.config.getEndTo().getAddress()));
		this.executor.execute(new SendEventRunnable(event));
	}
	
	//@Override
	public void deactivate() {
		LOGGER.info(String.format("Deactivate node with id '%s'.", config.getIdentifier()));
		for (EsperMonitoringNodeSetEngine set : sets) {
			set.deactivate();
		}
	}	
	
	//@Override
	public void activate() {
		LOGGER.info(String.format("Activate node with id '%s'.", config.getIdentifier()));
		for (EsperMonitoringNodeSetEngine set : sets) {
			set.activate();
		}
	}	
	
	@Override
	public void start() {
		LOGGER.info(String.format("Starting node with id '%s'.", config.getIdentifier()));
		for (Adapter adapter : outputAdaptes) {
			LOGGER.debug(String.format("Starting Output Adapter '%s'.", getAdapterId(adapter)));
			adapter.start();
		}
		for (EsperMonitoringNodeSetEngine set : sets) {
			set.start();
		}
		for (Adapter adapter : inputAdaptes) {
			LOGGER.debug(String.format("Starting Input Adapter '%s'.", getAdapterId(adapter)));
			adapter.start();
		}
		stateManager.start();
	}

	@Override
	public void stop() {
		if(config != null) {
			LOGGER.info(String.format("Stopping node with id '%s'.", config.getIdentifier()));
			stateManager.stop();
			for (Adapter adapter : inputAdaptes) {
				LOGGER.debug(String.format("Stopping Input Adapter '%s'.", getAdapterId(adapter)));
				adapter.stop();
			}
			for (EsperMonitoringNodeSetEngine set : sets) {
				set.stop();
			}
			for (Adapter adapter : outputAdaptes) {
				LOGGER.debug(String.format("Stopping Output Adapter '%s'.", getAdapterId(adapter)));
				adapter.stop();
			}	
		}
	}
	
	@Override
	public void destroy() {
		if(config != null){
			LOGGER.info(String.format("Destroying node with id '%s'.", config.getIdentifier()));
			for (Adapter adapter : inputAdaptes) {
				LOGGER.debug(String.format("Destroying Input Adapter '%s'.", getAdapterId(adapter)));
				adapter.destroy();
			}
			for (EsperMonitoringNodeSetEngine set : sets) {
				set.destroy();
			}
			for (Adapter adapter : outputAdaptes) {
				LOGGER.debug(String.format("Destroying Output Adapter '%s'.", getAdapterId(adapter)));
				adapter.destroy();
			}	
		}
		stateManager.destroy();
	}	
	
	
	private String getAdapterId(Adapter adapter) {
		String retVal = "UNDEF";
		if(adapter instanceof AbstractInputAdapter<?>){
			retVal = ((AbstractInputAdapter<?>)adapter).getExternalID();
		}
		else if(adapter instanceof AbstractOutputAdapter<?>){
			retVal = ((AbstractOutputAdapter<?>)adapter).getExternalID();
		}		
		return retVal;
	}

	private void createInputAdapters(DeploymentNodeConfig config,
			EPServiceProvider epService) throws Exception {
	
		
		Map<Class<? extends AbstractInput>, Class<? extends EsperMonitoringInputAdapter>> mapping = 
			new HashMap<Class<? extends AbstractInput>, Class<? extends EsperMonitoringInputAdapter>>();
		mapping.put(EventingInput.class, WSEventingInputAdapter.class);
		mapping.put(SOAPEventInput.class, SOAPEventInputAdapter.class);
		mapping.put(NodeConfigInput.class, NodeConfigSOAPInputAdapter.class);
		
		
		for (AbstractInput input : config.getInputs().getInputsCopy()) {
			Class<? extends EsperMonitoringInputAdapter> adapterClazz = mapping.get(input.getClass());						
			if(adapterClazz != null) {
				EsperMonitoringInputAdapter inputAdapter = adapterClazz.newInstance();
				LOGGER.debug(String.format("Configuring Input Adapter %s.", input.getExternalID()));
				inputAdapter.configure(input);				
				setAdapterServices(inputAdapter);				
				inputAdaptes.add(inputAdapter);
						
			} else {
				LOGGER.warn(input.getClass() + " not supported.");
			}
		}

//				ConstantInput constInput = (ConstantInput)input;
//				String url = (String)constInput.getTheContent();
//				inputAdapter = new CSVInputAdapter(epService, new AdapterInputSource(url), constInput.getEventType());				

	}
	
	private void createOutputAdapters(DeploymentNodeConfig config,
			EPServiceProvider epService) throws Exception {
		
		Map<Class<? extends AbstractOutput>, Class<? extends EsperMonitoringOutputAdapter>> mapping = 
			new HashMap<Class<? extends AbstractOutput>, Class<? extends EsperMonitoringOutputAdapter>>();
		mapping.put(LoggerOutput.class, LoggerOutputAdapter.class);
		mapping.put(EventingOutput.class, WSEventingOutputAdapter.class);
		mapping.put(SOAPEventOutput.class, SOAPEventOutputAdapter.class);
		mapping.put(NodeConfigOutput.class, NodeConfigSOAPOutputAdapter.class);
		mapping.put(NodeConfigChangeOutput.class, NodeConfigChangeOutputAdapter.class);
		
		
		for (AbstractOutput output : config.getOutputs().getOutputsCopy()) {
			Class<? extends EsperMonitoringOutputAdapter> adapterClazz = mapping.get(output.getClass());						
			if(adapterClazz != null) {
				EsperMonitoringOutputAdapter outputAdapter = adapterClazz.newInstance();
				LOGGER.debug(String.format("Configuring OutputAdapter %s.", output.getExternalID()));
				outputAdapter.configure(output);				
				setAdapterServices(outputAdapter);
				outputAdaptes.add(outputAdapter);
				
				for (MonitoringConfigSubscription subscription : output.getSubscriptions().getSubscriptions()) {
					EsperMonitoringSubscription sub = new EsperMonitoringSubscription();
					sub.seteventTypeName(subscription.getEventType());
					sub.setSubscriptionName(UUID.randomUUID() + "");
					sub.registerAdapter(outputAdapter);					
				}				
			} else {
				LOGGER.warn(output.getClass() + " not supported.");
			}
		}		
	}
	
	private void setAdapterServices(Object adapter){
		if(adapter instanceof AdapterSPI) {
			((AdapterSPI)adapter).setEPServiceProvider(epService);
		}
		if(adapter instanceof AdapterEventTypeRepository) {
			((AdapterEventTypeRepository)adapter).setEventTypeRepository(eventTypeRepository);
		}
		if(adapter instanceof AdapterNodeEngineAware) {
			((AdapterNodeEngineAware)adapter).setEngine(this);
		}
		if(adapter instanceof AdapterExecutor) {
			((AdapterExecutor)adapter).setExecutor(executor);
		}
	}

	public EsperMonitoringNodeSetEngine getSetEngine(long monitoringSet) {
		for (EsperMonitoringNodeSetEngine engine : sets) {
			if(engine.getMonitoringConfigSet().getIdentifier() == monitoringSet){
				return engine;
			}
		}
		return null;
	}
	
	
	private class SendEventRunnable implements Runnable{

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
				if (stateManager.getState() != AdapterState.STARTED) {
					return;
				}
				if (getEPServiceProvider() == null) {
					LOGGER.warn(".onMessage Event message not sent to engine, service provider not set yet, message ack'd");
					return;
				}
				//Check for system commands!!
				getEPServiceProvider().getEPRuntime().sendEvent(event);
			} catch (EPException ex) {
				LOGGER.error(".onMessage exception", ex);
				/*if (stateManager.getState() == AdapterState.STARTED) {
					stop();
				} else {
					destroy();
				}*/
			}
		}	
	}
	
}
