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
package at.ac.tuwien.infosys.aggr.monitor;

import java.util.LinkedList;
import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAnyElement;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlMixed;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.XmlValue;
import javax.xml.namespace.QName;

import org.w3c.dom.Element;

import at.ac.tuwien.infosys.aggr.request.AggregationRequest;
import at.ac.tuwien.infosys.aggr.request.AggregationResponse;
import at.ac.tuwien.infosys.util.Configuration;
import at.ac.tuwien.infosys.util.xml.XMLUtil;
import at.ac.tuwien.infosys.events.ws.WSEvent;

@XmlRootElement(name="Event", namespace=Configuration.NAMESPACE)
@XmlAccessorType(XmlAccessType.FIELD)
public class ModificationNotification extends WSEvent {

	public static final String LOCALNAME_EVENTSTREAM_ID = "EventStreamID";
	public static final String LOCALNAME_TOPOLOGY_ID = "topologyID";
	public static final QName SOAP_HEADER_EVENT_STREAM_ID = new QName(Configuration.NAMESPACE, LOCALNAME_EVENTSTREAM_ID);
	public static final QName SOAP_HEADER_TOPOLOGY_ID = new QName(Configuration.NAMESPACE, LOCALNAME_TOPOLOGY_ID);

	@XmlElement(name="eventStreamID")
	private String eventStreamID;
	@XmlElement(name="monitoringID")
	private String monitoringID;
	@XmlElement(name="requestID")
	private String requestID;
	@XmlAnyElement
	private List<Object> data = new LinkedList<Object>();

	@XmlRootElement(name=LOCALNAME_EVENTSTREAM_ID, namespace=Configuration.NAMESPACE)
	public static class EventStreamIdSOAPHeader {
		@XmlValue
		public String value;
		public EventStreamIdSOAPHeader() {}
		public EventStreamIdSOAPHeader(String value) {
			this.value = value;
		}
	}

	@XmlRootElement(name=LOCALNAME_EVENTSTREAM_ID, namespace=Configuration.NAMESPACE)
	public static class TopologyIdSOAPHeader {
		@XmlValue
		public String value;
	}

	public ModificationNotification(MonitoringTask task, List<AggregationResponse> responses) {
		for(AggregationResponse response : responses) {
			data.add(response.getResult());
			if(response.getInResponseTo() instanceof AggregationRequest)
				requestID = ((AggregationRequest)response.getInResponseTo()).getRequestID();
		}
	}

	public ModificationNotification(ModificationNotification toCopy) {
		data = toCopy.data;
		monitoringID = toCopy.monitoringID;
		eventStreamID = toCopy.eventStreamID;
		requestID = toCopy.requestID;
	}

	@XmlTransient
	public String getEventStreamID() {
		return eventStreamID;
	}
	public void setEventStreamID(String eventStreamID) {
		this.eventStreamID = eventStreamID;
	}
	@XmlTransient
	public String getMonitoringID() {
		return monitoringID;
	}
	public void setMonitoringID(String monitoringID) {
		this.monitoringID = monitoringID;
	}
	@XmlTransient
	public String getRequestID() {
		return requestID;
	}
	public void setRequestID(String requestID) {
		this.requestID = requestID;
	}
	@XmlTransient
	public List<Object> getData() {
		return data;
	}
	public void setData(List<Object> data) {
		this.data = data;
	}
	
	public ModificationNotification() {}
	
	public Element getDataAsElement() throws Exception {
		XMLUtil util = new XMLUtil();
		if(data.size() == 1) {
			if(data.get(0) instanceof Element)
				return (Element)data.get(0);
			return util.toElement(data.get(0));
		}
		Element result = util.toElement("<event/>");
		for(Object o : data) {
			if(o instanceof Element)
				util.appendChild(result, (Element)o);
			util.appendChild(result, util.toElement(o));
		}
		return result;
	}
	
	@Override
	public String toString() {
		try {
			return new XMLUtil().toString(this);
		} catch (Exception e) {
			return super.toString();
		}
	}
}
