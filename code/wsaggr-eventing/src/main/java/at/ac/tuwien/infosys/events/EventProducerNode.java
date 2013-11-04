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

import java.io.Serializable;
import java.util.LinkedList;
import java.util.List;
import java.util.Timer;
import java.util.UUID;

import javax.annotation.Resource;
import javax.jws.WebMethod;
import javax.jws.WebParam;
import javax.jws.WebResult;
import javax.jws.WebService;
import javax.jws.soap.SOAPBinding;
import javax.jws.soap.SOAPBinding.ParameterStyle;
import javax.jws.soap.SOAPBinding.Style;
import javax.jws.soap.SOAPBinding.Use;
import javax.xml.namespace.QName;
import javax.xml.ws.WebServiceContext;

import org.apache.log4j.Logger;
import org.w3c.dom.Element;

import at.ac.tuwien.infosys.aggr.node.DataServiceNode;
import at.ac.tuwien.infosys.util.Configuration;
import at.ac.tuwien.infosys.util.NotImplementedException;
import at.ac.tuwien.infosys.ws.EndpointReference;
import at.ac.tuwien.infosys.util.Util;
import at.ac.tuwien.infosys.util.xml.XMLUtil;
import at.ac.tuwien.infosys.events.ws.EventSubscribeFilter;
import at.ac.tuwien.infosys.events.ws.WSEvent;
import at.ac.tuwien.infosys.events.ws.EventGetStatusRequest;
import at.ac.tuwien.infosys.events.ws.EventGetStatusResponse;
import at.ac.tuwien.infosys.events.ws.EventRenewRequest;
import at.ac.tuwien.infosys.events.ws.EventRenewResponse;
import at.ac.tuwien.infosys.events.ws.EventSubscribeRequest;
import at.ac.tuwien.infosys.events.ws.EventSubscribeResponse;
import at.ac.tuwien.infosys.events.ws.EventSubscriptionManager;
import at.ac.tuwien.infosys.events.ws.EventUnsubscribeRequest;
import at.ac.tuwien.infosys.events.ws.EventUnsubscribeResponse;

@WebService(targetNamespace = Configuration.NAMESPACE)
public class EventProducerNode extends DataServiceNode implements EventSubscriptionManager, NodeState {

	@Resource
	private WebServiceContext wsContext;
	
	private static final long serialVersionUID = 5250432339824272116L;

	private static final Logger LOGGER = Util.getLogger(EventProducerNode.class);
	
	private long interval = 2000;
	
	private transient Timer timer = new Timer();
	
	protected List<AbstractEventProducerTask> tasks = new LinkedList<AbstractEventProducerTask>();
	
	private transient Util util = new Util();
	
	private final Lock lock = new Lock();
	
	private static final class Lock implements Serializable {
		private static final long serialVersionUID = 7635786755400149287L;
	}
	
	@WebMethod
	public String addEventConsumer(EndpointReference consumer, EventSubscribeFilter filter) throws Exception {
		String id = UUID.randomUUID().toString();
		synchronized (lock) {
			for(AbstractEventProducerTask task : tasks) {
				task.addEventConsumer(id, consumer, filter);
			}
		}
		return id;
	}
	
	private int getNumConsumers() {
		int count = 0;
		synchronized (lock) {
			for(AbstractEventProducerTask task : tasks) {
				count += task.eventConsumers.size();
			}
		}
		return count;
	}

	private void removeEventConsumer(String identifier) {
		synchronized (lock) {
			for(AbstractEventProducerTask task : tasks) {
				task.removeEventConsumer(identifier);
				LOGGER.info(task.getEventConsumers().size() + " remaining consumers: " + task.getEventConsumers());
			}
		}
	}

	public EventProducerNode(EndpointReference epr) {
		super(epr);
	}

	public long getInterval() {
		return interval;
	}

	public void setInterval(long milliseconds) {
		this.interval = milliseconds;
	}

	@WebMethod
	public void setNewInterval(@WebParam(name="milliseconds") long milliseconds) {
		this.interval = milliseconds;
		start();
	}

	@WebMethod
	@Override
	public void start() {
		if(timer != null) {
			timer.cancel();
			synchronized (lock) {
				for(int i = 0; i < tasks.size(); i ++) {
					AbstractEventProducerTask task = tasks.remove(i);
					task = task.copy();
					tasks.add(i, task);
				}
			}
		}
		timer = new Timer();

		synchronized (lock) {
			if(tasks.size() <= 0){
				LOGGER.debug("Creating default event producer task..");
				tasks.add(new DefaultEventProducerTask());
			}
			for(AbstractEventProducerTask task : tasks) {
				task.setCount(0);
				timer.scheduleAtFixedRate(task, 0, getInterval());
				task.run();
				LOGGER.debug(String.format("Producer '%s' started.", getEPR() == null ? "" : getEPR().getAddress()));
			}
		}
	}

	@WebMethod
	@Override
	public void stop() {
		synchronized (lock) {
			for(AbstractEventProducerTask task : tasks) {
				if(task != null){
					task.cancel();
				}
			}
			tasks.clear();
			timer.purge();
			LOGGER.debug(String.format("Producer '%s' stopped.", getEPR() == null ? "" : getEPR().getAddress()));
		}
	}

	
	public List<AbstractEventProducerTask> getTasks() {
		return tasks;
	}
	public void setTasks(List<AbstractEventProducerTask> tasks) {
		this.tasks = tasks;
	}
	public void addTask(AbstractEventProducerTask task) {
		synchronized (lock) {
			this.tasks.add(task);
		}
	}

	@WebMethod(operationName = "Subscribe")
	@WebResult(name = "SubscribeResponse", targetNamespace=EventSubscribeResponse.NAMESPACE)
	@SOAPBinding(style = Style.DOCUMENT, use = Use.LITERAL, parameterStyle = ParameterStyle.BARE)
	public EventSubscribeResponse subscribe(
			@WebParam(name="Subscribe", targetNamespace=WSEvent.NAMESPACE) 
			EventSubscribeRequest request){
		try {
			Element delivery = (Element)request.getDelivery();
			Element notifyTo = null;
			if(delivery != null) {
				notifyTo = XMLUtil.getInstance().getChildElements(delivery).get(0);
			} else {
				notifyTo = request.getEndTo().toElement();
			}
			try {
				EndpointReference consumer = new EndpointReference(notifyTo);
				String id = addEventConsumer(consumer, request.getFilter());
				if(getNumConsumers() ==1 || getNumConsumers() % 10 == 0) {
					LOGGER.info("EventProducer: " + getEpr().getAddress() + " - subscribe: " + 
							consumer.getAddress() + ", current consumers: " + getNumConsumers());
				}
				EventSubscribeResponse response = new EventSubscribeResponse();
				EndpointReference epr = new EndpointReference(getEPR());
				if(!epr.getAddress().endsWith("?wsdl"))
					epr.setAddress(epr.getAddress() + "?wsdl");
				epr.addReferenceParameter(XMLUtil.getInstance().toElement("<wse:Identifier xmlns:wse=\"" + 
						WSEvent.NAMESPACE + "\">" + id + "</wse:Identifier>"));
				response.setSubscriptionManager(epr);
				return response;
			} catch (Exception e) {
				e.printStackTrace();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	@WebMethod(operationName = "Unsubscribe")
	@WebResult(name = "UnsubscribeResponse")
	@SOAPBinding(style = Style.DOCUMENT, use = Use.LITERAL, parameterStyle = ParameterStyle.BARE)
	public EventUnsubscribeResponse unsubscribe(
			@WebParam(name="Unsubscribe", targetNamespace=WSEvent.NAMESPACE) EventUnsubscribeRequest request) {
		try {
			LOGGER.debug("'Unsubscribe' method of event producer service: ");
			List<Element> headers = getUtil().ws.getSoapHeaders(wsContext);
			for(Element e : headers) {
				if(new QName(e.getNamespaceURI(), e.getLocalName()).equals(WSEvent.NAME_IDENTIFIER)) {
					String identifier = e.getTextContent().trim();
					LOGGER.info("Removing event consumer with subscription ID " + identifier);
					removeEventConsumer(identifier);
				}
			}
		} catch (Exception e) {
			LOGGER.warn("Cannot unsubscribe..." , e);
		}
		return new EventUnsubscribeResponse();
	}

	@WebMethod(operationName = "Renew")
	@WebResult(name = "RenewResponse")
	@SOAPBinding(style = Style.DOCUMENT, use = Use.LITERAL, parameterStyle = ParameterStyle.BARE)
	public EventRenewResponse renew(EventRenewRequest request) {
		throw new NotImplementedException();
	}

	@WebMethod(operationName = "GetStatus")
	@WebResult(name = "GetStatusResponse")
	@SOAPBinding(style = Style.DOCUMENT, use = Use.LITERAL, parameterStyle = ParameterStyle.BARE)
	public EventGetStatusResponse getStatus(EventGetStatusRequest request) {
		throw new NotImplementedException();
	}
	
	private Util getUtil() {
		if(util == null)
			util = new Util();
		return util;
	}

}
