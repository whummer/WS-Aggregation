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

import java.lang.reflect.InvocationTargetException;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.namespace.QName;

import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.beanutils.PropertyUtils;
import org.apache.commons.lang.StringUtils;
import org.w3c.dom.Element;

import com.espertech.esper.client.EventBean;

import at.ac.tuwien.infosys.events.Event;
import at.ac.tuwien.infosys.events.EventType;
import at.ac.tuwien.infosys.events.EventTypeRepository;
import at.ac.tuwien.infosys.util.Util;
import at.ac.tuwien.infosys.util.xml.XMLUtil;
import at.ac.tuwien.infosys.ws.EndpointReference;

@XmlRootElement(name = WSEvent.NAME, namespace = WSEvent.NAMESPACE)
public class WSEvent extends Event {	

	public static final String NAME = "event";
	public static final String NAMESPACE = "http://schemas.xmlsoap.org/ws/2004/08/eventing";
	public static final String WSA_ACTION_SUBSCRIBE = "http://schemas.xmlsoap.org/ws/2004/08/eventing/Subscribe";
	public static final String WSA_ACTION_UNSUBSCRIBE = "http://schemas.xmlsoap.org/ws/2004/08/eventing/Unsubscribe";

	
	public static final QName NAME_IDENTIFIER = new QName(NAMESPACE,
			"Identifier");
	public static final Element HEADER_WSE_SUBSCRIBE = XMLUtil.getInstance()
			.toElementSafe("<wsa:Action xmlns:wsa=\""
					+ EndpointReference.NS_WS_ADDRESSING + "\">"
					+ WSEvent.WSA_ACTION_SUBSCRIBE + "</wsa:Action>");	
	
	private final List<Element> headers = new LinkedList<Element>();
	
	@XmlElement(name="content", type=WSEventContentProperties.class)
	private Object content;
	
	public boolean hasHeaders() {
		return !headers.isEmpty();
	}
	
	public int getNumHeaders() {
		return headers.size();
	}
	
	public void addHeader(Element e) {
		QName name = new QName(e.getNamespaceURI(), e.getLocalName());
		for(Element h : headers) {
			if(name.equals(new QName(h.getNamespaceURI(), h.getLocalName()))) {
				throw new RuntimeException("Header " + e + " already exists!");
			}
		}
		headers.add(e);
	}

	public Element getHeader(QName name) {
		Element e = null;
		for(Element h : headers) {
			if(name.equals(new QName(h.getNamespaceURI(), h.getLocalName()))) {
				if(e != null)
					throw new RuntimeException();
				e = h;
			}
		}
		return e;
	}

	public List<Element> getHeadersCopy() {
		return Collections.unmodifiableList(headers);
	}

	public String getHeadersAsString() throws Exception {
		Util util = new Util();
		Element e = util.xml.toElement("<Header/>");
		for(Element h : headers)
			util.xml.appendChild(e, h);
		return util.xml.toString(e);
	}
	
	public static Element createHeader(QName name, String value) throws Exception {
		Util util = new Util();
		return util.xml.toElement("<ns:" + name.getLocalPart() + " xmlns:ns=\"" + 
				name.getNamespaceURI() + "\">" + value + "</ns:" + name.getLocalPart() + ">");
	}
	
	@XmlTransient
	public Object getContent() {
		return content;
	}
	public void setContent(Object content) {
		this.content = content;
	}


	@XmlTransient
	@Deprecated 
	/** 
	 * since we want to avoid the existence of duplicate 
	 * headers, do not operate on the List directly, but
	 * use getHeader(..) or addHeader(..) instead.. 
	 * @return
	 */
	public List<Element> getHeaders() {
		return headers;
	}
	
	public static WSEvent toWSEvent(EventBean eventBean) throws Exception{
		final WSEvent retVal = new WSEvent();
		final String eventTypeName = eventBean.getEventType().getName();
		retVal.setType(eventTypeName);
		//TODO set the identifier right
		retVal.setIdentifier(1l);
		Date d = new Date();
		retVal.setTimeStamp(d.getTime());
		WSEventContentProperties properties = new WSEventContentProperties();
		retVal.setContent(properties);
		for (String key : eventBean.getEventType().getPropertyNames()) {
			WSEventContentProperty prop = new WSEventContentProperty();
			prop.setKey(key);
			prop.setValue(eventBean.get(key));
			properties.getProperties().add(prop);
		}		
		return retVal;
	}

	public static WSEvent toWSEvent(EventTypeRepository eventTypeRepo, Event event) throws Exception{
		final WSEvent soapEvent = new WSEvent();
		final String eventTypeName = event.getType();
		soapEvent.setType(eventTypeName);
		soapEvent.setIdentifier(event.getIdentifier());
		soapEvent.setTimeStamp(event.getTimeStamp());
		WSEventContentProperties properties = new WSEventContentProperties();
		convertPropertiesToWSEvent(eventTypeRepo, properties, event, eventTypeName);
		soapEvent.setContent(properties);
		
		return soapEvent;
	}
	
	private static WSEventContentProperty convertProperty(Event event, String key) throws IllegalAccessException, InvocationTargetException, NoSuchMethodException{
		final Object propValue = PropertyUtils.getProperty(event, key);
		WSEventContentProperty retVal = new WSEventContentProperty();
		retVal.setKey(key);
		retVal.setValue(propValue);
		return retVal;
	}
	
	private static void convertPropertiesToWSEvent(EventTypeRepository eventTypeRep, 
			WSEventContentProperties properties, Event event, String eventTypeName) throws IllegalAccessException, InvocationTargetException, NoSuchMethodException{	
		
		final EventType eventType = eventTypeRep.getEventType(eventTypeName);
		
		for (String propKey : eventType.getProperties().keySet()) {
			WSEventContentProperty prop = convertProperty(event, propKey);
			properties.getProperties().add(prop);
		}
		
		final String parentEventTypeName = eventType.getParentType();
		if(StringUtils.isEmpty(parentEventTypeName) == false){
			convertPropertiesToWSEvent(eventTypeRep, properties, event, parentEventTypeName);
		}
	}
	
	public static void convertProperties(EventTypeRepository eventTypeRep, Map<String, Object> properties, Event event, String eventTypeName) throws IllegalAccessException, InvocationTargetException, NoSuchMethodException{
		final EventType eventType = eventTypeRep.getEventType(eventTypeName);
		
		for (String propKey : eventType.getProperties().keySet()) {
			final Object propValue = PropertyUtils.getProperty(event, propKey);
			properties.put(propKey, propValue);
		}
		
		final String parentEventTypeName = eventType.getParentType();
		if(StringUtils.isEmpty(parentEventTypeName) == false){
			convertProperties(eventTypeRep, properties, event, parentEventTypeName);
		}
	}
	
	public static Event fromWSEvent(EventTypeRepository eventTypeRep, WSEvent soapEvent) throws Exception{
		EventType eventType = eventTypeRep.getEventType(soapEvent.getType());
		final Class<?> eventClazz = Class.forName(eventType.getEventClass());		
		final Event event = (Event)eventClazz.newInstance();
		event.setIdentifier(soapEvent.getIdentifier());
		event.setTimeStamp(soapEvent.getTimeStamp());
		event.setType(soapEvent.getType());
		
		WSEventContentProperties properties = (WSEventContentProperties)soapEvent.getContent();		
		for (WSEventContentProperty prop : properties.getProperties()) {			
			BeanUtils.setProperty(event, prop.getKey(), prop.getValue());
		}
		return event;
	}
	
}
