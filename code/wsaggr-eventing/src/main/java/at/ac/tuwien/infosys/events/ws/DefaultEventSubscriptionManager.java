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

package at.ac.tuwien.infosys.events.ws;

import java.util.Date;

import org.apache.log4j.Logger;

import io.hummer.util.Util;
import io.hummer.util.ws.EndpointReference;

public class DefaultEventSubscriptionManager extends
		AbstractEventSubscriptionManager implements EventSubscriptionManagerProcessor {

	private static final Logger LOGGER = Util.getLogger(DefaultEventSubscriptionManager.class);
	
	public DefaultEventSubscriptionManager(EndpointReference epr) {
		super(epr);
	}

	@Override
	public void onSubscribe(String endpointId, EventSubscribeFilter filter)
			throws Exception {
		// TODO Auto-generated method stub

	}

	@Override
	protected void onUnsubscribe(String endpointId) {
		// TODO Auto-generated method stub

	}
	

	@Override
	public void processEvent(WSEvent event) {
		LOGGER.debug("Sending event '" +  "'.");	
		Date now = new Date();
		for (EventSubscriptionInfo info : getSubscriptions()) {
			if(info.getExpires() == null || info.getExpires().getTime() > now.getTime()){
				try {
					WebServiceClientUtil.execute(info.getEndpointReference(), event);
				} catch (Exception e) {
					LOGGER.error(String.format("Can't notify the client '%s' for event '%s'.", 
							info.getEndpointReference(), event.getIdentifier()), e);
				}
			}
		}	
	}

}
