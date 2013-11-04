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
package at.ac.tuwien.infosys.aggr.proxy;

import java.util.LinkedList;
import java.util.List;

import org.w3c.dom.Element;

import at.ac.tuwien.infosys.ws.WebServiceClient;
import at.ac.tuwien.infosys.aggr.node.AggregatorNode;
import at.ac.tuwien.infosys.ws.IAbstractNode.TerminateRequest;
import at.ac.tuwien.infosys.aggr.performance.AggregatorPerformanceInfo;
import at.ac.tuwien.infosys.aggr.request.AbstractInput;
import at.ac.tuwien.infosys.aggr.request.AggregationRequest;
import at.ac.tuwien.infosys.ws.request.InvocationResult;
import at.ac.tuwien.infosys.aggr.request.RequestInput;
import at.ac.tuwien.infosys.aggr.strategy.StrategyChain;
import at.ac.tuwien.infosys.aggr.strategy.Topology;
import at.ac.tuwien.infosys.aggr.monitor.ModificationNotification;
import at.ac.tuwien.infosys.aggr.monitor.NotificationTask;
import at.ac.tuwien.infosys.aggr.events.query.EventStoresManager.ByteArray;
import at.ac.tuwien.infosys.aggr.events.query.EventingQueryCoordinator.ActiveQueryResult;
import at.ac.tuwien.infosys.util.Configuration;
import at.ac.tuwien.infosys.util.xml.XMLUtil;

import at.ac.tuwien.infosys.ws.EndpointReference;

public class AggregatorNodeProxy {
	private static final String namespace = Configuration.NAMESPACE;
	private EndpointReference epr;
	private XMLUtil xmlUtil = new XMLUtil();
	private boolean doCache;
	
	public AggregatorNodeProxy(EndpointReference epr) {
		this(epr, true);
	}
	public AggregatorNodeProxy(EndpointReference epr, boolean doCache) {
		this.epr = epr;
		this.doCache = doCache;
	}
	public AggregatorNodeProxy(AggregatorNode a) {
		this(a.getEPR());
	}
	
	public void onEvent(ModificationNotification event, Element ... soapHeaders) throws Exception {
		WebServiceClient c = WebServiceClient.getClient(epr, doCache);
		RequestInput i = new RequestInput(xmlUtil.toElement(event));
		for(Element e : soapHeaders)
			i.addSoapHeader(e);
		c.invoke(i.getRequest());
	}
	
	public void addListeners(List<NotificationTask> listeners) throws Exception {
		WebServiceClient c = WebServiceClient.getClient(epr, doCache);
		String listenerString = "";
		for(NotificationTask listener : listeners)
			listenerString += xmlUtil.toString(listener);
		RequestInput i = new RequestInput(xmlUtil.toElement(
				"<tns:addListeners xmlns:tns=\"" + namespace + "\">" +
					listenerString +
				"</tns:addListeners>"));
		c.invoke(i.getRequest());
	}
	
	public String monitorData(AggregationRequest request/*, EndpointReference notificationReceiver*/) throws Exception {
		WebServiceClient c = WebServiceClient.getClient(epr, doCache);
		RequestInput i = new RequestInput(xmlUtil.toElement(
				"<tns:monitorData xmlns:tns=\"" + namespace + "\">" +
					request.toString("request") +
				"</tns:monitorData>"));
		InvocationResult res = c.invoke(i.getRequest());
		Element result = (Element)res.getResult();
		result = xmlUtil.getChildElements(result).get(0);
		result = xmlUtil.getChildElements(result).get(0);
		return result.getTextContent();
	}

	public Topology getTopology(String topologyID) throws Exception {
		return getTopologies(topologyID).get(0);
	}

	public List<Topology> getTopologies() throws Exception {
		return getTopologies(null);
	}
	private List<Topology> getTopologies(String topologyID) throws Exception {
		WebServiceClient c = WebServiceClient.getClient(epr, doCache);
		RequestInput i = new RequestInput(xmlUtil.toElement(
				"<tns:getTopology xmlns:tns=\"" + namespace + "\">" +
					(topologyID != null ? "<topologyID>" + topologyID + "</topologyID>" : "") +
				"</tns:getTopology>"));
		InvocationResult res = c.invoke(i.getRequest());
		Element result = (Element)res.getResult();
		result = xmlUtil.getChildElements(result).get(0);
		List<Topology> list = new LinkedList<Topology>();
		for(Element e : xmlUtil.getChildElements(result)) {
			Topology t = xmlUtil.toJaxbObject(Topology.class, e);
			list.add(t);
		}
		return list;
	}
	
	public void updateTopology(String topologyID, Topology topology) throws Exception {
		WebServiceClient c = WebServiceClient.getClient(epr, doCache);
		String reqString = "<tns:updateTopology xmlns:tns=\"" + namespace + "\">" +
							"<topologyID>" + topologyID + "</topologyID>" +
							"<updates>" + (xmlUtil.toString(topology)) + "</updates>" +
						"</tns:updateTopology>";
		RequestInput i = new RequestInput(xmlUtil.toElement(reqString));
		c.invoke(i.getRequest());
	}

	public int inheritInput(AggregationRequest request, Topology topology, AggregatorNode fromNode) throws Exception {
		WebServiceClient c = WebServiceClient.getClient(epr, doCache);
		RequestInput i = new RequestInput(xmlUtil.toElement(
				"<tns:inheritInput xmlns:tns=\"" + namespace + "\">" +
					(request.toString("request")) +
					(xmlUtil.toString(xmlUtil.changeRootElementName(xmlUtil.toElement(topology), "topology"))) +
					(xmlUtil.toString(xmlUtil.changeRootElementName(xmlUtil.toElement(fromNode), "fromNode"))) +
				"</tns:inheritInput>"));
		InvocationResult res = c.invoke(i.getRequest());
		Element result = (Element)res.getResult();
		result = xmlUtil.getChildElements(result).get(0);
		result = xmlUtil.getChildElements(result).get(0);
		return Integer.parseInt(result.getTextContent());
	}
	
	public String receiveEventStreamDump(AbstractInput input, 
			EndpointReference forwardFutureEventsTo) throws Exception {
		WebServiceClient c = WebServiceClient.getClient(epr, doCache);
		RequestInput i = new RequestInput(xmlUtil.toElement(
				"<tns:receiveEventStream xmlns:tns=\"" + namespace + "\">" +
					"<input>" +
					(xmlUtil.toString(xmlUtil.toElement(input))) +
					"</input>" +
					forwardFutureEventsTo.toString("forwardFutureEventsTo") +
				"</tns:receiveEventStream>"));
		InvocationResult res = c.invoke(i.getRequest());
		Element result = (Element)res.getResult();
		result = xmlUtil.getChildElements(result).get(0);
		result = xmlUtil.getChildElements(result).get(0);
		ByteArray ba = xmlUtil.toJaxbObject(ByteArray.class, result);
		return ba.string;
	}

	public void stopProcessingAndForwarding(String eventStreamID, EndpointReference receiver,
			AggregationRequest request) throws Exception {
		WebServiceClient c = WebServiceClient.getClient(epr, doCache);
		RequestInput i = new RequestInput(xmlUtil.toElement(
				"<tns:stopForwardingEvents xmlns:tns=\"" + namespace + "\">" +
					"<eventStreamID>" + eventStreamID + "</eventStreamID>" +
					receiver.toString("receiver") +
					request.toString("request") + 
				"</tns:stopForwardingEvents>"));
		c.invoke(i.getRequest());
	}
		
	
	public ActiveQueryResult pollActiveQueryResult(String topologyID, 
			String clientID, Boolean onlyUpdates, Boolean onlyOnce) throws Exception {
		if(onlyUpdates == null)
			onlyUpdates = false;
		if(onlyOnce == null)
			onlyOnce = false;
		WebServiceClient c = WebServiceClient.getClient(epr, doCache);
		RequestInput i = new RequestInput(xmlUtil.toElement(
				"<tns:pollActiveQueryResult xmlns:tns=\"" + namespace + "\">" +
					"<topologyID>" + topologyID + "</topologyID>" +
					(clientID != null ? ("<clientID>" + clientID + "</clientID>") : "") +
					(onlyUpdates ? ("<onlyUpdates>" + onlyUpdates + "</onlyUpdates>") : "") +
					(onlyOnce ? ("<onlyOnce>" + onlyOnce + "</onlyOnce>") : "") +
				"</tns:pollActiveQueryResult>"));
		InvocationResult res = c.invoke(i.getRequest());
		Element result = (Element)res.getResult();
		result = xmlUtil.getChildElements(result).get(0);
		List<Element> children = xmlUtil.getChildElements(result);
		if(children.isEmpty()) {
			return null;
		}
		result = children.get(0);
		return xmlUtil.toJaxbObject(ActiveQueryResult.class, result);
	}
	
	public AggregatorPerformanceInfo getPerformanceInfo(Boolean doNotDeleteData) throws Exception {
		return getPerformanceInfo(doNotDeleteData, null);
	}

	public AggregatorPerformanceInfo getPerformanceInfo(Boolean doNotDeleteData, Boolean detailed) throws Exception {
		WebServiceClient c = WebServiceClient.getClient(epr, doCache);
		RequestInput i = new RequestInput(xmlUtil.toElement(
				"<tns:getPerformanceInfo xmlns:tns=\"" + namespace + "\">" +
				(doNotDeleteData != null ? "<doNotDeleteData>" + doNotDeleteData + "</doNotDeleteData>" : "") +
				(detailed != null ? "<detailed>" + detailed + "</detailed>" : "") +
				"</tns:getPerformanceInfo>"));
		InvocationResult res = c.invoke(i.getRequest());
		Element result = (Element)res.getResult();
		result = xmlUtil.getChildElements(result).get(0);
		result = xmlUtil.getChildElements(result).get(0);
		return xmlUtil.toJaxbObject(AggregatorPerformanceInfo.class, result);
	}
	
	public void setStrategy(StrategyChain strategy) throws Exception {
		WebServiceClient c = WebServiceClient.getClient(epr, doCache);
		RequestInput i = new RequestInput(xmlUtil.toElement(
				"<tns:setStrategy xmlns:tns=\"" + namespace + "\">" +
					strategy.toString("strategyChain") + 
				"</tns:setStrategy>"));
		c.invoke(i.getRequest());
	}
	
	public void startMemoryProfiler() throws Exception {
		WebServiceClient c = WebServiceClient.getClient(epr, doCache);
		RequestInput request = new RequestInput(xmlUtil.toElement(
				"<tns:startMemoryProfiler xmlns:tns=\"" + Configuration.NAMESPACE + "\"/>"));
		c.invoke(request.getRequest());
	}
	
	public void onResult(String topologyID, String inputUID, Element newResult) throws Exception {
		WebServiceClient c = WebServiceClient.getClient(epr, doCache);
		RequestInput i = new RequestInput(xmlUtil.toElement(
				"<tns:onResult xmlns:tns=\"" + namespace + "\">" +
					"<topologyID>" + topologyID + "</topologyID>" + 
					"<inputUID>" + inputUID + "</inputUID>" + 
					"<newResult>" + xmlUtil.toString(newResult) + "</newResult>" + 
				"</tns:onResult>"));
		c.invoke(i.getRequest());
	}
	
	public void terminate(TerminateRequest p) throws Exception {
		WebServiceClient c = WebServiceClient.getClient(epr, doCache);
		RequestInput i = new RequestInput(xmlUtil.toElement(p));
		c.invoke(i.getRequest());
	}
	
}
