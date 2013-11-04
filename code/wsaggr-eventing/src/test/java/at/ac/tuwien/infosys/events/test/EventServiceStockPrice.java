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

import java.util.Random;

import javax.jws.WebService;

import org.w3c.dom.Element;

import at.ac.tuwien.infosys.aggr.monitor.ModificationNotification;
import at.ac.tuwien.infosys.ws.SoapService;
import at.ac.tuwien.infosys.util.Configuration;
import at.ac.tuwien.infosys.util.xml.XMLUtil;
import at.ac.tuwien.infosys.ws.EndpointReference;
import at.ac.tuwien.infosys.events.AbstractEventProducerTask;
import at.ac.tuwien.infosys.events.EventProducerNode;
import at.ac.tuwien.infosys.events.ws.WSEvent;

@WebService(targetNamespace = Configuration.NAMESPACE)
public class EventServiceStockPrice extends EventProducerNode implements SoapService {
	private static final long serialVersionUID = 6142754742110946325L;
	
	public static class EventProducerTask extends AbstractEventProducerTask {
		private static final long serialVersionUID = 1819094697133936872L;
		private Random r = new Random();
		private String type = "StockPrice"; // StockPrice or StockTrade

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
				String symbol = new String(new byte[]{(byte)(65 + r.nextInt(5))});
				Element e = util.toElement("<stock num=\"" + event.getIdentifier() + 
						"\" symbol=\"" + symbol + "\">" + r.nextInt(20) + "</stock>");
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

	@Deprecated
	public EventServiceStockPrice() {
		super(null);
		addTask(new EventProducerTask("StockPrice"));
	}
	
	public EventServiceStockPrice(EndpointReference self) {
		super(self);
		addTask(new EventProducerTask("StockPrice"));
	}
}
