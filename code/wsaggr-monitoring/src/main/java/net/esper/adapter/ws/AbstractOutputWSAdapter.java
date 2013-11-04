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

import com.espertech.esper.client.EventBean;

import at.ac.tuwien.infosys.aggr.request.AbstractOutput;
import at.ac.tuwien.infosys.monitoring.esper.AbstractOutputAdapter;

public abstract class AbstractOutputWSAdapter<T extends AbstractOutput> extends AbstractOutputAdapter<T>{
	
	
	@Override
	public final void processEvent(EventBean event){
		if(LOGGER.isDebugEnabled()){
			LOGGER.debug(String.format("OutputAdapter %s send event with id %s", getExternalID(), 
					"TODO"));
		}
		processEventInt(event);
	}
	
	public abstract void processEventInt(EventBean event);
}
