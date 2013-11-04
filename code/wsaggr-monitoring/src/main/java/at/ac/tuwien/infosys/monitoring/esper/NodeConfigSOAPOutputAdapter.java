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

import java.util.List;

import net.esper.adapter.ws.AbstractOutputWSAdapter;

import org.apache.log4j.Logger;

import at.ac.tuwien.infosys.events.ws.WSEvent;
import at.ac.tuwien.infosys.events.ws.WebServiceClientUtil;
import at.ac.tuwien.infosys.monitoring.config.NodeConfigOutput;
import at.ac.tuwien.infosys.util.Util;
import at.ac.tuwien.infosys.ws.EndpointReference;

import com.espertech.esper.client.EventBean;

public class NodeConfigSOAPOutputAdapter extends AbstractOutputWSAdapter<NodeConfigOutput> {

	private static final Logger LOGGER = Util
			.getLogger(NodeConfigSOAPOutputAdapter.class);

	private NodeConfigOutput output;

	@Override
	protected void configureInt(NodeConfigOutput output) throws Exception {
		this.output = output;
	}

	@Override
	public void processEventInt(EventBean event) {
		List<EndpointReference> mappings = output.getEventTypeRecipients(event
				.getEventType().getName());
		for (EndpointReference endTo : mappings) {
			try {
				LOGGER.debug(String.format("NodeConfigOutputAdapter tries to send internal event to %s.", endTo.getAddress()));
				WSEvent toSend = WSEvent.toWSEvent(event);
				WebServiceClientUtil.execute(endTo, toSend, null);
			} catch (Exception e) {
				LOGGER.error(String.format(
						"Can't notify the client '%s' for event '%s'.", endTo,
						event.getEventType().getName()), e);
			}
		}
	}

}
