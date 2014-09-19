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

import org.apache.log4j.Logger;

import at.ac.tuwien.infosys.events.ws.WebServiceClientUtil;
import at.ac.tuwien.infosys.monitoring.config.SOAPEventOutput;
import io.hummer.util.Util;
import io.hummer.util.ws.EndpointReference;

import com.espertech.esper.client.EventBean;

public class SOAPEventOutputAdapter extends AbstractOutputWSAdapter<SOAPEventOutput> {

	private static final Logger LOGGER = Util.getLogger(SOAPEventOutputAdapter.class);
	
	private EndpointReference endTo;
	
	@Override
	protected void configureInt(SOAPEventOutput output) throws Exception {
		this.endTo = output.endTo;	
	}

	@Override
	public void processEventInt(EventBean event) {
		try {					
			WebServiceClientUtil.execute(endTo, event.getUnderlying(), null);
		} catch (Exception e) {
			LOGGER.error(String.format("Can't notify the client '%s' for event '%s'.", 
					endTo, event.getEventType().getName()), e);
		}
	}

}
