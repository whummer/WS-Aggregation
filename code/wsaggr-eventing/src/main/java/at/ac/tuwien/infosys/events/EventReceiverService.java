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

package at.ac.tuwien.infosys.events;

import io.hummer.util.Configuration;
import io.hummer.util.Util;
import io.hummer.util.ws.AbstractNode;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import javax.jws.WebMethod;
import javax.jws.WebParam;
import javax.jws.WebService;
import javax.jws.soap.SOAPBinding;
import javax.jws.soap.SOAPBinding.ParameterStyle;
import javax.jws.soap.SOAPBinding.Style;
import javax.jws.soap.SOAPBinding.Use;

import org.w3c.dom.Element;

import at.ac.tuwien.infosys.aggr.monitor.ModificationNotification;

@WebService(targetNamespace=Configuration.NAMESPACE)
public class EventReceiverService extends AbstractNode {

	private static final AtomicInteger globalEventCounter = new AtomicInteger();

	private static int PRINT_ALL_N_EVENTS = -1;
	
	protected int eventCounter = 0;
	private final Util util = new Util();
	private boolean printEvents;
	private String delimiter = ", ";
	private List<EventReceiverListener> listeners = new LinkedList<EventReceiverListener>();
	
	public EventReceiverService(boolean print) {
		this(print, ", ");
	}
	public EventReceiverService(boolean print, String delimiter) {
		this.printEvents = print;
		this.delimiter = delimiter;
	}
	
	@SOAPBinding(style=Style.DOCUMENT, use=Use.LITERAL, parameterStyle=ParameterStyle.BARE)
	@WebMethod(operationName="Event")
	public void onEvent(/*@WebParam(header=true, name=ModificationNotification.LOCALNAME_EVENTSTREAM_ID,
						targetNamespace=Configuration.NAMESPACE) EventStreamIdSOAPHeader header,*/
			@WebParam ModificationNotification event) throws Exception {
		try {
			synchronized (util) {
				eventCounter++;
				if(delimiter != null) {
					System.out.print("event" + (eventCounter) + delimiter);
					if(eventCounter % 100 == 0)
						System.out.println();
					System.out.flush();
				} else {
					if(eventCounter % 100 == 0)
						System.out.println("event" + eventCounter);
				}
			}
			Element result = (Element)event.getData().get(0);
			String subscriptionID = event.getRequestID();
			for(EventReceiverListener l : listeners) {
				l.handleNewEvent(subscriptionID, result);
			}
			String resultString = util.xml.toString(util.xml.cloneCanonical(result), true);
			if(printEvents || (PRINT_ALL_N_EVENTS > 0 && (globalEventCounter.getAndIncrement() % PRINT_ALL_N_EVENTS == 0)))
				System.out.println(resultString);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	@WebMethod(exclude=true)
	public void addListener(EventReceiverListener l) {
		this.listeners.add(l);
	}
	
	public void setPrintEvents(boolean printEvents) {
		this.printEvents = printEvents;
	}
	@WebMethod(exclude=true)
	public boolean isPrintEvents() {
		return printEvents;
	}
	
}
