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

import java.net.URL;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

import javax.jws.WebService;

import org.w3c.dom.Element;

import at.ac.tuwien.infosys.aggr.monitor.ModificationNotification;
import at.ac.tuwien.infosys.aggr.node.Registry;
import at.ac.tuwien.infosys.events.AbstractEventProducerTask;
import at.ac.tuwien.infosys.events.EventProducerNode;
import at.ac.tuwien.infosys.events.ws.WSEvent;
import at.ac.tuwien.infosys.util.Configuration;
import at.ac.tuwien.infosys.util.par.GlobalThreadPool;
import at.ac.tuwien.infosys.util.xml.XMLUtil;
import at.ac.tuwien.infosys.ws.EndpointReference;
import at.ac.tuwien.infosys.ws.SoapService;

@WebService(targetNamespace = Configuration.NAMESPACE)
public class EventServiceStockTrade extends EventProducerNode implements SoapService {
	private static final long serialVersionUID = 1L;

	public static class EventProducerTask extends AbstractEventProducerTask {
		private static final long serialVersionUID = 1L;
		private Random r = new Random();
		private String type = "StockTrade"; // StockPrice or StockTrade

		public EventProducerTask(String type) { 
			this.type = type;
		}
		public EventProducerTask(EventProducerTask toCopy) { 
			super(toCopy);
			this.type = toCopy.type;
		}

		protected WSEvent createEvent(WSEvent event) {
			ModificationNotification n = new ModificationNotification();
			try {
				XMLUtil util = new XMLUtil();
				Element container = util.toElement("<data/>");
				int amount = 1000 + r.nextInt(5000);
				String symbol = new String(new byte[]{(byte)(65 + r.nextInt(5))});
				String bidOrAsk = r.nextInt(2) == 0 ? "bid" : "ask";
				Element e = util.toElement("<" + bidOrAsk + " num=\"" + event.getIdentifier() + 
						"\" symbol=\"" + symbol + "\">" + amount + "</" + bidOrAsk + ">");
				util.appendChild(container, e);
				n.getData().add(container);
			} catch (Exception e) {
				e.printStackTrace();
			}
			return n;
		}

		@Override
		public AbstractEventProducerTask copy() {
			return new EventProducerTask(this);
		}
	}

	public void onDeploy() {
		start();
	}
	public void onUndeploy() {
		stop();
	}
	
	public EventServiceStockTrade() {
		super(null);
		addTask(new EventProducerTask("StockTrade"));
	}
	
	public EventServiceStockTrade(EndpointReference self) {
		super(self);
		addTask(new EventProducerTask("StockTrade"));
	}

	public static List<EventServiceStockTrade> start(int num, long eventInterval) throws Exception {
		final String addressTemplate = Configuration.getUrlWithVariableHost("test.eventing.producer.address");
		List<EventServiceStockTrade> result = new LinkedList<EventServiceStockTrade>();
		if(addressTemplate == null)
			return result;
		final URL url = new URL(addressTemplate);
		for(int i = 0; i < num; i ++) {
			final EventServiceStockTrade producer = new EventServiceStockTrade();
			producer.setInterval(eventInterval);
			producer.start();
			String address = new URL(url.getProtocol(), url.getHost(), url.getPort() + i, url.getFile()).toExternalForm();
			System.out.println("Starting event producer: " + address);
			producer.deploy(address);
			producer.setEPR(new EndpointReference(new URL((address + "?wsdl"))));
			result.add(producer);
			/* add to registry in regular intervals. */
			Runnable r = new Runnable() {
				public void run() {
					try {
						Registry.getRegistryProxy().addDataServiceNode("Eventing", producer);
					} catch (Exception e) {
						throw new RuntimeException(e);
					}
				}
			};
			GlobalThreadPool.executePeriodically(r, 60*1000, 5*1000);
		}
		return result;
	}
}
