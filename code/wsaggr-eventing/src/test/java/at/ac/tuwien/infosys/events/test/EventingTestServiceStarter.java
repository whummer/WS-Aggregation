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

package at.ac.tuwien.infosys.events.test;

import java.net.BindException;
import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;

import at.ac.tuwien.infosys.aggr.node.Registry;
import at.ac.tuwien.infosys.events.test.EventServiceStockTrade;
import io.hummer.util.Configuration;
import io.hummer.util.ws.EndpointReference;

public class EventingTestServiceStarter {

	public static void setupDefault() throws Exception {
		try {
			startEventProducers(3);
		} catch (BindException e) {}
	}

	public static void startEventProducers(int num) throws Exception {
		startEventProducers(num, 1000);
	}
	public static void startEventProducers(int num, long interval) throws Exception {
		Logger.getAnonymousLogger().getParent().setLevel(Level.OFF);
		final String addressTemplate = Configuration.getUrlWithVariableHost("test.eventing.producer.address");
		final URL url = new URL(addressTemplate);
		for(int i = 0; i < num; i ++) {
			EventServiceStockTrade producer = new EventServiceStockTrade();
			producer.setInterval(interval);
			producer.start();
			String address = new URL(url.getProtocol(), url.getHost(), url.getPort() + i, url.getFile()).toExternalForm();
			producer.deploy(address);
			producer.setEPR(new EndpointReference(new URL((address + "?wsdl"))));
			Registry.getRegistryProxy().addDataServiceNode("Eventing", producer);
		}
	}
	
}
