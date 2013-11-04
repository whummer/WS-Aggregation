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

import net.esper.adapter.ws.AbstractInputWSAdapter;
import net.esper.adapter.ws.SOAPEventInputAdapter;

import org.apache.log4j.Logger;

import at.ac.tuwien.infosys.events.Event;
import at.ac.tuwien.infosys.monitoring.config.NodeConfigInput;
import at.ac.tuwien.infosys.util.Util;

import com.espertech.esper.client.EPException;

public class NodeConfigSOAPInputAdapter extends AbstractInputWSAdapter<NodeConfigInput>{

	private static final Logger LOGGER = Util.getLogger(SOAPEventInputAdapter.class);
	
	@Override
	protected void sendEvent(Event event) throws Exception
	{
		//Check for system commands!!
		getEPServiceProvider().getEPRuntime().sendEvent(event);
	}

	@Override
	protected void configureInt(NodeConfigInput input) throws Exception {
		node.setEPR(input.getEndTo());
	}
	
	public void start() throws EPException {
		synchronized (syncRoot) {
			LOGGER.debug(".start");
			try {
				node.start();
				LOGGER.info("NodeConfigInputAdapter started @ " + node.getEPR().getAddress());		
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
			super.stop();
		}
	}

	public void destroy() throws EPException {
		LOGGER.debug(".destroy");
		try {
			node.destroy();
			LOGGER.info("NodeConfigInputAdapter destroyed @ " + node.getEPR().getAddress());	
			super.destroy();
		} catch (Exception e) {
			throw new EPException(e);
		}
	}
}
