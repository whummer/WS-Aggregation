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

import org.apache.log4j.Logger;

import at.ac.tuwien.infosys.monitoring.config.NodeConfigChangeOutput;
import io.hummer.util.Util;
import io.hummer.util.par.GlobalThreadPool;

import com.espertech.esper.client.EventBean;

public class NodeConfigChangeOutputAdapter extends AbstractOutputAdapter<NodeConfigChangeOutput> implements AdapterNodeEngineAware{

	private static final Logger LOGGER = Util.getLogger(NodeConfigChangeOutputAdapter.class);
	
	private EsperMonitoringEngine engine;
	
	@Override
	public void setEngine(EsperMonitoringEngine engine) {
		this.engine = engine;
	}

	@Override
	public EsperMonitoringEngine getEngine() {
		return engine;
	}
	
	@Override
	protected void configureInt(NodeConfigChangeOutput output)
			throws Exception {
		// TODO Auto-generated method stub
		
	}
	
	@Override
	public void processEvent(EventBean event) {
		final ChangeConfigEvent ev = (ChangeConfigEvent)event.getUnderlying();
		LOGGER.debug(String.format("Received event for activating config '%s'.", ev.getMonitoringSet()));
		final EsperMonitoringNodeSetEngine engine = this.getEngine().getSetEngine(ev.getMonitoringSet());		
		if(engine != null){
			//need to run it asynchron because otherwise we will run in a dead lock
			GlobalThreadPool.execute(new Runnable() {
				@Override
				public void run() {
					if(ChangeConfigEvent.ACTION_ACTIVATE.equals(ev.getAction())){
						engine.activate();	
					} else if(ChangeConfigEvent.ACTION_DEACTIVATE.equals(ev.getAction())){
						engine.deactivate();	
					} else {
						LOGGER.error(String.format("Action %s can't be executed for configSet %s.", 
								ev.getAction(), engine.getMonitoringConfigSet().getIdentifier()));
					}
				}
			});			
		} else {
			LOGGER.error(String.format("Can't find config set '%s' for activating.", ev.getMonitoringSet()));
		}
	}	

}
