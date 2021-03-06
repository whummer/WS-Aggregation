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

package at.ac.tuwien.infosys.monitoring;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import at.ac.tuwien.infosys.events.EventConsumerNode;
import at.ac.tuwien.infosys.events.EventForwarderNode;
import at.ac.tuwien.infosys.events.EventTypeRepository;
import at.ac.tuwien.infosys.events.LoggerEventConsumerNode;
import at.ac.tuwien.infosys.monitoring.config.MonitoringStartupConfig;
import at.ac.tuwien.infosys.monitoring.events.stock.StockTickSendEvent;
import io.hummer.util.Configuration;
import io.hummer.util.ws.EndpointReference;

@org.junit.Ignore
public class MonitoringServiceTest extends LocalMonitoringService {

	
	public MonitoringServiceTest(MonitoringEngineInternal engine) {
		super(engine);
	}

	
	public void configure(MonitoringStartupConfig config) throws Exception{
		//super.configure(config);
		//Starting
		final List<EventForwarderNode> producer = setUpProducer();
		final AbstractMonitoringService engine = null;//startService(config);
		final EventConsumerNode consumer = setUpConsumer(getEventTypeRepository());
		
		for (EventForwarderNode eventingNode : producer) {
			eventingNode.setEventTypeRepository(getEventTypeRepository());
		}
		
	
		Random r = new Random();
		String[] symbols = {"IBM", "MS", "APPL"};
		int i = 0;
		while (i < 1000) {
			StockTickSendEvent priceEvent = new StockTickSendEvent();
			priceEvent.setSymbol(symbols[r.nextInt(symbols.length)]);
			priceEvent.setPrice(r.nextDouble() * 1000);
			priceEvent.setVolume(r.nextInt(1000));
			EventForwarderNode eventForwarderNode = producer.get(r.nextInt(producer.size()));
			eventForwarderNode.sendEvent(priceEvent);
			
			Thread.sleep(r.nextInt(10));
			i++;
		}
				
		Thread.sleep(10000);
		//engine.stop();
		//engine.destroy();
	}
	
//	public static void main(String[] args) throws Exception {
//		
//		final MonitoringStartupConfig config = getConfig();
//				
//		final List<MonitoringServiceNode> startNodes = startNodes(config);
//		
//		final EventTypeRepository eventTypeRepository = 
//			RepositoryHelpers.generateEventTypeRepository(config.getEventTypeConfig());		
//		
//		
//	}

	
	private static List<EventForwarderNode> setUpProducer() throws Exception {
		List<EventForwarderNode> retVal = new ArrayList<EventForwarderNode>();
		EventForwarderNode node1 = new EventForwarderNode();
		String url1 = "http://127.0.0.1:8081/events?wsdl";
		node1.setEPR(new EndpointReference(new URL(url1)));		
		retVal.add(node1);
		
		EventForwarderNode node2 = new EventForwarderNode();
		String url2 = "http://127.0.0.1:8082/events?wsdl";
		node2.setEPR(new EndpointReference(new URL(url2)));
		retVal.add(node2);
		
		return retVal;
		
	}
	
	private static EventConsumerNode setUpConsumer(EventTypeRepository eventTypeRepo) throws Exception {
		String ecURL = "http://localhost:6061/eventConsumer";
		EndpointReference ecEPR = new EndpointReference(
				"<wsa:EndpointReference xmlns:wsa=\"http://schemas.xmlsoap.org/ws/2004/08/addressing\" " +
				"xmlns:tns=\"" + Configuration.NAMESPACE + "\">" +
					"<wsa:Address>" + ecURL + "</wsa:Address>" +
					"<wsa:ServiceName PortName=\"EventConsumerNodePort\">" +
						"tns:EventConsumerNodeService" +
					"</wsa:ServiceName>" +
				"</wsa:EndpointReference>");				
		EventConsumerNode retVal = new LoggerEventConsumerNode(ecEPR, eventTypeRepo);	
		
		// Event Source Stuff
		String outAdapterURL = "http://127.0.0.1:9091/events";
		EndpointReference outAdapterEPR = new EndpointReference(
				"<wsa:EndpointReference xmlns:wsa=\"http://schemas.xmlsoap.org/ws/2004/08/addressing\" " +
				"xmlns:tns=\"" + Configuration.NAMESPACE + "\">" +
					"<wsa:Address>" + outAdapterURL + "</wsa:Address>" +
					"<wsa:ServiceName PortName=\"ClientPort\">" +
						"tns:Client" +
					"</wsa:ServiceName>" +
				"</wsa:EndpointReference>");
		
		retVal.setEventSourceEPR(outAdapterEPR);
		retVal.deploy(ecURL);
		retVal.start();
		return retVal;
	}
	
}
