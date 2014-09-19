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

import javax.xml.bind.annotation.XmlRootElement;

import at.ac.tuwien.infosys.events.AbstractEventProducerTask;
import at.ac.tuwien.infosys.events.Event;
import at.ac.tuwien.infosys.events.EventConsumerNode;
import at.ac.tuwien.infosys.events.EventDistributorNode;
import at.ac.tuwien.infosys.events.EventProducerNode;
import at.ac.tuwien.infosys.events.ws.WSEvent;
import io.hummer.util.Configuration;
import io.hummer.util.ws.EndpointReference;

@org.junit.Ignore
public class EventProducer_ConsumerTest {	
	
	@XmlRootElement(name="ProdConEvent")
	public static class ProdConEvent extends Event {

		private String info;

		public String getInfo() {
			return info;
		}

		public void setInfo(String info) {
			this.info = info;
		}
			
	}
	
	public static class SimpleEventProducerTask extends AbstractEventProducerTask {

		private static final long serialVersionUID = 1L;

		public SimpleEventProducerTask(){			
		}
		
		public SimpleEventProducerTask(SimpleEventProducerTask toCopy){
			super(toCopy);
		}
		
		@Override
		protected WSEvent createEvent(WSEvent aggrEvent) {
			ProdConEvent e = new ProdConEvent();
			e.setInfo("Test for ProdConEvent");
			aggrEvent.setContent(e);
			return aggrEvent;
		}
		
		@Override
		public AbstractEventProducerTask copy() {
			return new SimpleEventProducerTask(this);
		}
	}
	
	public static void main(String[] args) throws Exception {
		
		String diURL = "http://127.0.0.1:7071/eventDistributor";
		EndpointReference diEPR = new EndpointReference(
				"<wsa:EndpointReference xmlns:wsa=\"http://schemas.xmlsoap.org/ws/2004/08/addressing\" " +
				"xmlns:tns=\"" + Configuration.NAMESPACE + "\">" +
					"<wsa:Address>" + diURL + "</wsa:Address>" +
					"<wsa:ServiceName PortName=\"EventDistributorNodePort\">" +
						"tns:EventDistributorNodeService" +
					"</wsa:ServiceName>" +
				"</wsa:EndpointReference>");
		final EventDistributorNode distributor = new EventDistributorNode(diEPR);			
		
		
		String ecURL = "http://127.0.0.1:7072/eventConsumer";
		EndpointReference ecEPR = new EndpointReference(
				"<wsa:EndpointReference xmlns:wsa=\"http://schemas.xmlsoap.org/ws/2004/08/addressing\" " +
				"xmlns:tns=\"" + Configuration.NAMESPACE + "\">" +
					"<wsa:Address>" + ecURL + "</wsa:Address>" +
					"<wsa:ServiceName PortName=\"EventConsumerNodePort\">" +
						"tns:EventConsumerNodeService" +
					"</wsa:ServiceName>" +
				"</wsa:EndpointReference>");
		final EventConsumerNode consumer = new EventConsumerNode(ecEPR);
		consumer.setEventSourceEPR(diEPR);			
		
		String epURL = "http://127.0.0.1:7072/eventProducer";
		EndpointReference epEPR = new EndpointReference(
				"<wsa:EndpointReference xmlns:wsa=\"http://schemas.xmlsoap.org/ws/2004/08/addressing\" " +
				"xmlns:tns=\"" + Configuration.NAMESPACE + "\">" +
					"<wsa:Address>" + epURL + "</wsa:Address>" +
					"<wsa:ServiceName PortName=\"EventProducerNodePort\">" +
						"tns:EventProducerNodeService" +
					"</wsa:ServiceName>" +
				"</wsa:EndpointReference>");
		final EventProducerNode producer = new EventProducerNode(epEPR);
		
		producer.addTask(new SimpleEventProducerTask());
		producer.addEventConsumer(diEPR, null);
		
		producer.setInterval(1000);
		distributor.deploy(diURL);
		producer.deploy(epURL);
		consumer.deploy(ecURL);
		
		consumer.start();		
		producer.start();
		
		Thread.sleep(10000);
						
		producer.stop();
		consumer.stop();
		
		System.out.println("Close");
	}
}
