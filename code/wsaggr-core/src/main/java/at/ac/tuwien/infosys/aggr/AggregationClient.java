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
package at.ac.tuwien.infosys.aggr;

import java.util.HashMap;
import java.util.Map;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import org.apache.log4j.Logger;
import org.w3c.dom.Element;

import at.ac.tuwien.infosys.aggr.account.SessionIdSOAPHeader;
import at.ac.tuwien.infosys.aggr.account.UsernameSOAPHeader;
import at.ac.tuwien.infosys.aggr.flow.FlowNodesDTO;
import at.ac.tuwien.infosys.aggr.node.Gateway.LoginRequest;
import at.ac.tuwien.infosys.aggr.node.Gateway.LoginResponse;
import at.ac.tuwien.infosys.aggr.performance.AggregatorPerformanceInfo;
import at.ac.tuwien.infosys.aggr.proxy.AggregatorNodeProxy;
import at.ac.tuwien.infosys.aggr.request.AbstractInput.RequestInputs;
import at.ac.tuwien.infosys.aggr.request.AggregationRequest;
import at.ac.tuwien.infosys.aggr.request.RequestInput;
import at.ac.tuwien.infosys.aggr.request.WAQLQuery;
import at.ac.tuwien.infosys.aggr.strategy.Topology;
import at.ac.tuwien.infosys.util.Configuration;
import at.ac.tuwien.infosys.util.Util;
import at.ac.tuwien.infosys.ws.AbstractNode;
import at.ac.tuwien.infosys.ws.EndpointReference;
import at.ac.tuwien.infosys.ws.WebServiceClient;
import at.ac.tuwien.infosys.ws.request.InvocationRequest;
import at.ac.tuwien.infosys.ws.request.InvocationResult;
import at.ac.tuwien.infosys.ws.request.RequestType;

public class AggregationClient {

	public static final Logger logger = at.ac.tuwien.infosys.util.Util.getLogger(AggregationClient.class);
	
	private Util util = new Util();
	private EndpointReference target;
	private boolean doCache = false;

	private String username;
	private String passwordHash;
	private String sessionID;

	public AggregationClient(EndpointReference target) {
		this.target = target;
	}

	public AggregationClient(EndpointReference target, 
			String username, String passhash) {
		this.target = target;
		this.username = username;
		this.passwordHash = passhash;
	}

	public AggregationClient(AbstractNode target) {
		this.target = target.getEPR();
	}
	
	public AggregationClient(EndpointReference target, boolean cacheWebServiceClient) {
		this.target = target;
		this.doCache = cacheWebServiceClient;
	}

	private void ensureSessionID() {
		if(!util.str.isEmpty(sessionID))
			return;
		if(util.str.isEmpty(username))
			return;
		sessionID = login(username, passwordHash);
	}
	
	private void addSessionIDHeader(InvocationRequest r) {
		try {
			ensureSessionID();
			r.soapHeaders.add(util.xml.toElement(
					new UsernameSOAPHeader(username)));
			r.soapHeaders.add(util.xml.toElement(
					new SessionIdSOAPHeader(sessionID)));
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private String login(String username, String password) {
		if(!util.str.isMD5(password)) {
			password = util.str.md5(password);
		}
		LoginRequest r = new LoginRequest(username, password);
		InvocationRequest req;
		try {
			req = new InvocationRequest(RequestType.SOAP, util.xml.toElement(r));
			Element result = WebServiceClient.getClient(target, doCache).invoke(req).getResultAsElement();
			result = util.ws.getFirstChildIfSOAPBody(result);
			return util.xml.toJaxbObject(LoginResponse.class, result).getSessionID();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
	
	public Element aggregate(WAQLQuery queries, Object input) throws Exception {
		return aggregate(null, queries, input);
	}
	
	public Element aggregate(AggregationRequest request) throws Exception {
		Element requestEl = util.xml.toElement(request);
		return doAggregate(requestEl);
	}
	
	public Element aggregate(String topologyID, WAQLQuery queries, Object input) throws Exception {
		String in = "";
		if(topologyID == null || topologyID.trim().equals(""))
			topologyID = null;
		if(input instanceof String) {
			try {
				in = (String)input;
				input = util.xml.toElement((String)input); 
			} catch (Exception e) {
				// swallow
			}
		}
		if(input instanceof Element) {
			Element inEl = (Element)input;
			if(inEl.getTagName().equals("input"))
				in = "<inputs>" + util.xml.toString(inEl) + "</inputs>";
		} if(input instanceof RequestInputs)
			in = ((RequestInputs)input).toString();
		
		String requestString = "<tns:aggregate " +
				"xmlns:tns=\"" + Configuration.NAMESPACE + "\">" +
				(topologyID == null ? "" : "<topologyID>" + topologyID + "</topologyID>") +
				queries +
				in +
				"</tns:aggregate>";
		//System.out.println(requestString);
		
		Element request = util.xml.toElement(requestString);

		return doAggregate(request);
	}
	
	private Element doAggregate(Element request) throws Exception {
		WebServiceClient client = WebServiceClient.getClient(target, doCache);
		InvocationRequest req = new InvocationRequest(RequestType.SOAP, request);
		addSessionIDHeader(req);
		InvocationResult r = client.invoke(req);
		Element result = (Element)r.getResult();
		return (Element)result.getFirstChild();
	}
	
	public FlowNodesDTO getDependencies(AggregationRequest request) throws Exception {
		RequestInput i = new RequestInput(util.xml.toElement(
				"<tns:getDependencies xmlns:tns=\"" + Configuration.NAMESPACE + "\">" +
				request.toString("request") +
				"</tns:getDependencies>"));
		Element result = WebServiceClient.getClient(target, doCache).invoke(i.getRequest()).getResultAsElement();
		result = util.ws.getFirstChildIfSOAPBody(result);
		result = util.xml.getChildElements(result).get(0);
		return util.xml.toJaxbObject(FlowNodesDTO.class, result);
	}
	
	public String createTopology(AggregationRequest request) throws Exception {
		return createTopology(null, (String[])null, request);
	}
	
	public String createTopology(String type, AggregationRequest request) throws Exception {
		return createTopology(type, (String[])null, request);
	}
	
	public String createTopology(String type, String[] features) throws Exception {
		return createTopology(type, features, null);
	}
	public String createTopology(String type, java.util.List<String> features, AggregationRequest theRequest) throws Exception {
		return createTopology(type, features.toArray(new String[]{}), theRequest);
	}
	
	@XmlRootElement(name="createTopology", namespace=Configuration.NAMESPACE)
	public static class CreateTopologyRequest {
		@XmlElement
		public String type;
		@XmlElement
		public String[] features;
		@XmlElement
		AggregationRequest request;
	}
	
	private String createTopology(String type, String[] features, AggregationRequest theRequest) throws Exception {
		CreateTopologyRequest req = new CreateTopologyRequest();
		req.type = type;
		req.features = features;
		req.request = theRequest;
		Element request = util.xml.toElement(req);
		
		WebServiceClient client = WebServiceClient.getClient(target, doCache);
		InvocationRequest invReq = new RequestInput(request).getRequest();
		addSessionIDHeader(invReq);
		InvocationResult r = client.invoke(invReq);
		Element result = (Element)r.getResult();
		return util.xml.getTextContent(result.getFirstChild().getFirstChild());
	}
	
	public boolean destroyTopology(String topologyID) throws Exception {
		Element request = util.xml.toElement("<tns:destroyTopology " +
				"xmlns:tns=\"" + Configuration.NAMESPACE + "\">" +
				"<topologyID>" + topologyID + "</topologyID>" +
				"</tns:destroyTopology>");
		
		WebServiceClient client = WebServiceClient.getClient(target, doCache);
		InvocationRequest invReq = new RequestInput(request).getRequest();
		addSessionIDHeader(invReq);
		InvocationResult r = client.invoke(invReq);
		Element result = r.getResultAsElement();
		return Boolean.parseBoolean(result.getFirstChild().getFirstChild().getTextContent());
	}

	public Topology getTopology(String topologyID) throws Exception {
		AggregatorNodeProxy p = new AggregatorNodeProxy(this.target, true);
		return p.getTopology(topologyID);
	}

	public java.util.List<Topology> getAllTopologies() throws Exception {
		AggregatorNodeProxy p = new AggregatorNodeProxy(this.target, true);
		return p.getTopologies();
	}

	public AggregatorPerformanceInfo getPerformanceInfo(
			Boolean doNotDeleteData, Boolean detailed, String forUsername) throws Exception {
		RequestInput i = new RequestInput(util.xml.toElement(
				"<tns:getPerformanceInfo xmlns:tns=\"" + Configuration.NAMESPACE + "\">" +
				(doNotDeleteData != null ? ("<doNotDeleteData>" + doNotDeleteData + "</doNotDeleteData>") : "") +
				(detailed != null ? ("<detailed>" + detailed + "</detailed>") : "") +
				(forUsername != null ? ("<forUsername>" + forUsername + "</forUsername>") : "") +
				"</tns:getPerformanceInfo>"));
		Element result = WebServiceClient.getClient(target, doCache).invoke(i.getRequest()).getResultAsElement();
		result = util.ws.getFirstChildIfSOAPBody(result);
		result = util.xml.getChildElements(result).get(0);
		return util.xml.toJaxbObject(AggregatorPerformanceInfo.class, result);
	}

	public Map<String,AggregationRequest> getAllPersistedRequests() throws Exception {
		return getAllPersistedRequests(null);
	}
	public Map<String,AggregationRequest> getAllPersistedRequests(String pattern) throws Exception {
		Map<String,AggregationRequest> result = new HashMap<String, AggregationRequest>();
		WebServiceClient c = WebServiceClient.getClient(target);
		RequestInput i = new RequestInput(util.xml.toElement(
				"<tns:getAllPersistedRequests xmlns:tns=\"" + Configuration.NAMESPACE + "\">" +
					(pattern != null ? ("<pattern>" + pattern + "</pattern>") : "") +
				"</tns:getAllPersistedRequests>"));
		InvocationRequest req = i.getRequest();
		addSessionIDHeader(req);
		InvocationResult res = c.invoke(req);
		Element resEl = (Element)res.getResult();
		resEl = util.xml.getChildElements(resEl).get(0);
		for(Element e : util.xml.getChildElements(resEl)) {
			String reqName = e.getTextContent();
			result.put(reqName, getPersistedAggregationRequest(reqName));
		}
		
		return result;
	}
	public AggregationRequest getPersistedAggregationRequest(String requestName) throws Exception {
		RequestInput i = new RequestInput(util.xml.toElement(
				"<tns:getPersistedAggregationRequest xmlns:tns=\"" + Configuration.NAMESPACE + "\">" +
					"<name>" + requestName + "</name>" +
				"</tns:getPersistedAggregationRequest>"));
		InvocationRequest req = i.getRequest();
		addSessionIDHeader(req);
		InvocationResult res = WebServiceClient.getClient(target).invoke(req);
		Element result = (Element)res.getResult();
		if(result == null)
			return null;
		if(util.xml.getChildElements(result).isEmpty())
			return null;
		result = util.xml.getChildElements(result).get(0);
		if(util.xml.getChildElements(result).isEmpty())
			return null;
		result = util.xml.getChildElements(result).get(0);
		return util.xml.toJaxbObject(AggregationRequest.class, util.xml.changeRootElementName(
				result, Configuration.NAMESPACE, AggregationRequest.NAME_ELEMENT));
	}

	public void persistRequest(String requestName, 
			AggregationRequest req) throws Exception {
		persistRequest(requestName, req, true, false);
	}
	public void persistRequest(String requestName, AggregationRequest req, 
			boolean isPublic) throws Exception {
		persistRequest(requestName, req, true, isPublic);
	}
	public void persistRequest(String requestName, AggregationRequest request, 
			boolean overwrite, boolean isPublic) throws Exception {
		RequestInput i = new RequestInput(util.xml.toElement(
				"<tns:persistAggregationRequest xmlns:tns=\"" + Configuration.NAMESPACE + "\">" +
					"<name>" + requestName + "</name>" +
					request.toString("request") +
					"<overwrite>" + overwrite + "</overwrite>" +
					"<public>" + isPublic + "</public>" +
				"</tns:persistAggregationRequest>"));
		InvocationRequest req = i.getRequest();
		addSessionIDHeader(req);
		WebServiceClient.getClient(target).invoke(req);
	}

}
