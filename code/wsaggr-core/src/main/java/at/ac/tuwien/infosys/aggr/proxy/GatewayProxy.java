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

import io.hummer.util.Configuration;
import io.hummer.util.ws.EndpointReference;
import io.hummer.util.ws.WebServiceClient;
import io.hummer.util.ws.request.InvocationResult;
import io.hummer.util.xml.XMLUtil;

import java.util.LinkedList;
import java.util.List;

import javax.xml.namespace.QName;

import org.w3c.dom.Element;

import at.ac.tuwien.infosys.aggr.node.AggregatorNode;
import at.ac.tuwien.infosys.aggr.node.Gateway.CheckAccessResponse;
import at.ac.tuwien.infosys.aggr.request.AggregationRequest;
import at.ac.tuwien.infosys.aggr.request.RequestInput;
import at.ac.tuwien.infosys.aggr.strategy.Topology;
import at.ac.tuwien.infosys.aggr.strategy.TopologyOptimizerVNS.OptimizationParameters;

public class GatewayProxy {
	private static final String namespace = Configuration.NAMESPACE;
	private EndpointReference epr;
	private XMLUtil xmlUtil = new XMLUtil();
	private boolean doCache;

	public GatewayProxy(EndpointReference epr) {
		this(epr, true);
	}
	public GatewayProxy(EndpointReference epr, boolean doCache) {
		this.epr = epr;
		this.doCache = doCache;
	}
	public GatewayProxy(AggregatorNode a) {
		this(a.getEPR());
	}

	public AggregatorNode getMasterAggregator(String topologyID) throws Exception {
		WebServiceClient c = WebServiceClient.getClient(epr, doCache);
		RequestInput i = new RequestInput(xmlUtil.toElement(
				"<tns:getMasterAggregator xmlns:tns=\"" + namespace + "\">" +
					(topologyID != null ? "<topologyID>" + topologyID + "</topologyID>" : "") +
				"</tns:getMasterAggregator>"));
		InvocationResult res = c.invoke(i.getRequest());
		Element result = (Element)res.getResult();
		result = xmlUtil.getChildElements(result).get(0);
		Object aggr = xmlUtil.toJaxbObject(AggregatorNode.class, xmlUtil.getChildElements(result).get(0));
		if(aggr instanceof AggregatorNode)
			return (AggregatorNode)aggr;
		return new AggregatorNode((EndpointReference)aggr, false);
	}

	public List<Topology> collectAllTopologies() throws Exception {
		WebServiceClient c = WebServiceClient.getClient(epr, doCache);
		RequestInput i = new RequestInput(xmlUtil.toElement(
				"<tns:collectAllTopologies xmlns:tns=\"" + namespace + "\">" +
				"</tns:collectAllTopologies>"));
		InvocationResult res = c.invoke(i.getRequest());
		Element result = (Element)res.getResult();
		result = xmlUtil.getChildElements(result).get(0);
		List<Topology> list = new LinkedList<Topology>();
		for(Element e : xmlUtil.getChildElements(result)) {
			Element topEl = xmlUtil.changeRootElementName(e, "Topology");
			list.add(xmlUtil.toJaxbObject(Topology.class, topEl));
		}
		return list;
	}

	public CheckAccessResponse checkAccess(QName operation, String username, String sessionID) throws Exception {
		RequestInput i = new RequestInput(xmlUtil.toElement(
				"<tns:checkAccess xmlns:tns=\"" + namespace + "\">" +
					"<operation>" + operation + "</operation>" +
				"</tns:checkAccess>"));
		InvocationResult res = WebServiceClient.getClient(epr).invoke(i.getRequest());
		Element result = (Element)res.getResult();
		result = xmlUtil.getChildElements(result).get(0);
		result = xmlUtil.getChildElements(result).get(0);
		return xmlUtil.toJaxbObject(CheckAccessResponse.class, xmlUtil.changeRootElementName(
				result, Configuration.NAMESPACE, AggregationRequest.NAME_ELEMENT));
	}
	
	public void setOptimization(boolean doOptimize, OptimizationParameters params) throws Exception {
		WebServiceClient c = WebServiceClient.getClient(epr, doCache);
		RequestInput i = new RequestInput(xmlUtil.toElement(
				"<tns:setOptimization xmlns:tns=\"" + namespace + "\">" +
					"<doOptimize>" + doOptimize + "</doOptimize>" +
					(params != null ? xmlUtil.toString(params) : "") +
				"</tns:setOptimization>"));
		c.invoke(i.getRequest());
	}
	
	public EndpointReference getEpr() {
		return epr;
	}
}
