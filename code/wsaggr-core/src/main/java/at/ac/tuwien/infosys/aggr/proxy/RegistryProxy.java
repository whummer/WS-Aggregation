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
import io.hummer.util.Util;
import io.hummer.util.par.GlobalThreadPool;
import io.hummer.util.ws.EndpointReference;
import io.hummer.util.ws.WebServiceClient;
import io.hummer.util.ws.request.InvocationRequest;
import io.hummer.util.ws.request.InvocationResult;
import io.hummer.util.ws.request.RequestType;
import io.hummer.util.xml.XMLUtil;

import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.Text;

import at.ac.tuwien.infosys.aggr.node.AggregatorNode;
import at.ac.tuwien.infosys.aggr.node.DataServiceNode;
import at.ac.tuwien.infosys.aggr.node.Gateway;
import at.ac.tuwien.infosys.aggr.request.RequestInput;

public class RegistryProxy {
	
	private EndpointReference epr;
	private XMLUtil xmlUtil = new XMLUtil();
	private boolean doCache;
	
	private static long SYNC_INTERVAL_IF_SUCCESS = 2*60*1000;
	private static long SYNC_INTERVAL_IF_ERROR = 5*1000;
	private static String namespace = Configuration.NAMESPACE;
	private static Map<String, List<DataServiceNode>> cachedServiceNodes = new HashMap<String, List<DataServiceNode>>();
	private static List<AggregatorNode> cachedAggregatorNodes = new LinkedList<AggregatorNode>();
	private static Gateway cachedGateway = null;
	private static Logger logger = Util.getLogger(RegistryProxy.class);
	private static long nextCacheResetTime = 0;
	private static final List<Integer> cacheResetIntervals = 
			new LinkedList<Integer>(Arrays.asList(1000*10, 1000*20, 1000*10, 1000*20, 1000*30, 1000*60, 1000*60*10));
	
	// for testing purposes
	public static Integer numServiceNodes = 0;
	public static Integer numAggregators = 30;
	
	public RegistryProxy(String address) {
		try {
			this.epr = new EndpointReference("<wsa:EndpointReference xmlns:wsa=\"http://schemas.xmlsoap.org/ws/2004/08/addressing\" " +
				"xmlns:tns=\"" + namespace + "\">" +
					"<wsa:Address>" + address + "</wsa:Address>" +
					"<wsa:ServiceName PortName=\"GatewayPort\">" +
						"tns:GatewayService" +
					"</wsa:ServiceName>" +
				"</wsa:EndpointReference>");
		} catch (Exception e) { e.printStackTrace(); /* should never happen */ }
	}
	public RegistryProxy(EndpointReference epr) {
		this.epr = epr;
	}
	public List<DataServiceNode> getAllDataServiceNodes() throws Exception {
		return getDataServiceNodes(null);
	}
	public List<DataServiceNode> getDataServiceNodes(String feature) throws Exception {
		List<DataServiceNode> result = cachedServiceNodes.get(feature);
		if(doCache) {
			if(result != null)
				return result;
		}
		
		result = new ArrayList<DataServiceNode>();
		RequestInput i = new RequestInput(xmlUtil.toElement(
					"<tns:getDataServiceNodes xmlns:tns=\"" + namespace + "\">" +
						(feature == null ? "" : ("<feature>" + feature + "</feature>")) +
					"</tns:getDataServiceNodes>"));
		InvocationResult res = WebServiceClient.getClient(epr).invoke(i.getRequest());
		Element eprs = (Element)res.getResult();
		eprs = xmlUtil.getChildElements(eprs).get(0);
		eprs = xmlUtil.getChildElements(eprs).get(0);
		for(Element e : xmlUtil.getChildElements(eprs)) {
			EndpointReference epr = new EndpointReference(e);
			DataServiceNode n = new DataServiceNode();
			n.setEPR(epr);
			result.add(n);
		}
		synchronized (cachedServiceNodes) {
			if(doCache) {
				cachedServiceNodes.put(feature, result);
			}
		}
		synchronized (numServiceNodes) {
			if(numServiceNodes > 0 && numServiceNodes > result.size())
				numServiceNodes = result.size();
			if(numServiceNodes > 0)
				result = result.subList(0, numServiceNodes);			
		}
		return result;
	}
	public List<AggregatorNode> getAggregatorNodes() throws Exception {
		return getAggregatorNodes(-1);
	}
	public List<AggregatorNode> getAggregatorNodes(int numAggregatorNodes) throws Exception {
		List<AggregatorNode> result = null;
		synchronized (cachedAggregatorNodes) {
			if(doCache) {
				if(cachedAggregatorNodes.size() > 0) {
					result = cachedAggregatorNodes;
					//System.out.println(nextCacheResetTime + " - " + System.currentTimeMillis() + " - " + (System.currentTimeMillis() - nextCacheResetTime)/1000);
					if(nextCacheResetTime > 0 && System.currentTimeMillis() > nextCacheResetTime) {
						resetCache();
						result = null;
					}
				}
			}
		}
		
		//System.out.println("aggregators list result: " + result);
		if(result == null || result.size() <= 0) {
			RequestInput i = new RequestInput(xmlUtil.toElement(
					"<tns:getAggregatorNodes xmlns:tns=\"" + namespace + "\">" +
						"<numAggregatorNodes>" + numAggregatorNodes + "</numAggregatorNodes>" +
					"</tns:getAggregatorNodes>"));
			InvocationResult res = WebServiceClient.getClient(epr).invoke(i.getRequest());
			Element aggregators = (Element)res.getResult();
			aggregators = xmlUtil.getChildElements(aggregators).get(0);
			aggregators = xmlUtil.getChildElements(aggregators).get(0);
			result = new LinkedList<AggregatorNode>();
			for(Element a : xmlUtil.getChildElements(aggregators)) {
				EndpointReference epr = new EndpointReference(a);
				result.add(new AggregatorNode(epr, false));
			}
			synchronized (cachedAggregatorNodes) {
				if(doCache) {
					cachedAggregatorNodes.clear();
					cachedAggregatorNodes.addAll(result);
					if(!cacheResetIntervals.isEmpty()) {
						nextCacheResetTime = System.currentTimeMillis() + cacheResetIntervals.get(0);
						if(cacheResetIntervals.size() > 1 && !cachedAggregatorNodes.isEmpty()) // always leave one item in the list
							cacheResetIntervals.remove(0);
					} else
						nextCacheResetTime = Long.MAX_VALUE;
				}
			}
		}
		if(numAggregators > 0 && numAggregators < result.size())
			result = result.subList(0, numAggregators);
		
		return new LinkedList<AggregatorNode>(result);
	}
	public void addDataServiceNode(final String feature, final EndpointReference endpointReference, 
			boolean syncPeriodically) throws Exception {
		Runnable run = new Runnable() {
			public void run() {
				RequestInput i;
				try {
					i = new RequestInput(xmlUtil.toElement(
							"<tns:addDataServiceNode xmlns:tns=\"" + namespace + "\">" +
								"<feature>" + feature + "</feature>" + 
								endpointReference.toString("serviceEPR") +
							"</tns:addDataServiceNode>"));
					WebServiceClient.getClient(getEPR()).invoke(i.getRequest());
				} catch (Exception e) {
					logger.info("Unable to add data service to registry.", e);
				}
			}
		};
		if(syncPeriodically)
			GlobalThreadPool.executePeriodically(run, SYNC_INTERVAL_IF_SUCCESS, SYNC_INTERVAL_IF_ERROR);
		else
			run.run();
	}
	public void addDataServiceNode(String feature, EndpointReference endpointReference) throws Exception {
		addDataServiceNode(feature, endpointReference, false);
	}
	public void addDataServiceNode(String feature, DataServiceNode node) throws Exception {
		addDataServiceNode(feature, node.getEPR());
	}
	public AggregatorNode getRandomAggregatorNode() throws Exception {
		List<AggregatorNode> a = getAggregatorNodes(0);
		if(a.size() <= 0) 
			return null;
		return a.get((int)(Math.random() * a.size()));
	}
	public DataServiceNode getRandomDataServiceNode(String feature) throws Exception {
		List<DataServiceNode> a = getDataServiceNodes(feature);
		if(a.size() <= 0) 
			return null;
		return a.get((int)(Math.random() * a.size()));
	}
	public Gateway getGateway() throws Exception {
		Gateway gw = cachedGateway;
		Element result = null;
		try {
			if(gw == null) {
				RequestInput i = new RequestInput(xmlUtil.toElement(
						"<tns:getGateway xmlns:tns=\"" + namespace + "\">" +
						"</tns:getGateway>"));
				InvocationResult res = WebServiceClient.getClient(epr).invoke(i.getRequest());
				result = (Element)res.getResult();
				result = xmlUtil.getChildElements(result).get(0);
				result = xmlUtil.getChildElements(result).get(0);
				result = xmlUtil.getChildElements(result).get(0);
				EndpointReference epr = new EndpointReference(result);
				gw = new Gateway();
				gw.setEPR(epr);
				cachedGateway = gw;
			}
		} catch (Exception e) {
			throw new RuntimeException("Unable to determine location of gateway. Invocation response was: " + 
					(result == null ? null : xmlUtil.toString(result)), e);
		}
		return gw;
	}
	public void setNumServiceNodes(int numServices) throws Exception {
		RequestInput i = new RequestInput(xmlUtil.toElement(
				"<tns:setNumServiceNodes xmlns:tns=\"" + namespace + "\">" +
					"<numServices>" + numServices + "</numServices>" +
				"</tns:setNumServiceNodes>"));
		WebServiceClient.getClient(epr).invoke(i.getRequest());
		numServiceNodes = numServices;
		resetCache();
	}
	public void setNumAggregatorNodes(int numAggregatorNodes) throws Exception {
		RequestInput i = new RequestInput(xmlUtil.toElement(
				"<tns:setNumAggregatorNodes xmlns:tns=\"" + namespace + "\">" +
					"<numAggregators>" + numAggregatorNodes + "</numAggregators>" +
				"</tns:setNumAggregatorNodes>"));
		WebServiceClient.getClient(epr).invoke(i.getRequest());
		synchronized (numAggregators) {
			numAggregators = numAggregatorNodes;
		}
		resetCache();
	}
	public static void resetCache() {
		synchronized (cachedServiceNodes) {
			cachedServiceNodes = new HashMap<String, List<DataServiceNode>>();
		}
		synchronized (cachedAggregatorNodes) {
			cachedAggregatorNodes = new LinkedList<AggregatorNode>();
		}
		cachedGateway = null;
	}
	public void addAggregatorNode(AggregatorNode aggregator) throws Exception {
		RequestInput i = new RequestInput(xmlUtil.toElement(
				"<tns:addAggregatorNode xmlns:tns=\"" + namespace + "\">" +
					aggregator.getEPR().toString("aggregator") +
				"</tns:addAggregatorNode>"));
		synchronized (cachedAggregatorNodes) {
			if(doCache) {
				if(!cachedAggregatorNodes.contains(aggregator))
					cachedAggregatorNodes.add(aggregator);
			}
		}
		WebServiceClient.getClient(epr).invoke(i.getRequest());
	}
	public void setGateway(final Gateway gateway, boolean syncPeriodically) throws Exception {
		Runnable run = new Runnable() {
			public void run() {
				RequestInput i;
				try {
					i = new RequestInput(xmlUtil.toElement(
							"<tns:setGateway xmlns:tns=\"" + namespace + "\">" +
								gateway.getEPR().toString("gateway") +
							"</tns:setGateway>"));
					WebServiceClient.getClient(getEPR()).invoke(i.getRequest());
				} catch (Exception e) {
					logger.info("Unable to add gateway to registry.", e);
				}
			}
		};
		if(syncPeriodically)
			GlobalThreadPool.executePeriodically(run, SYNC_INTERVAL_IF_SUCCESS, SYNC_INTERVAL_IF_ERROR);
		else
			run.run();
	}
	public void removeAggregatorNode(AggregatorNode aggregator) throws Exception {
		RequestInput i = new RequestInput(xmlUtil.toElement(
				"<tns:removeAggregatorNode xmlns:tns=\"" + namespace + "\">" +
					aggregator.getEPR().toString("aggregator") +
				"</tns:removeAggregatorNode>"));
		synchronized (cachedAggregatorNodes) {
			if(doCache) {
				cachedAggregatorNodes.remove(aggregator);
			}
		}
		try {
			WebServiceClient.getClient(epr).invoke(i.getRequest());			
		} catch (Exception e) { 
			logger.info("Unable to remove aggregator node: " + e);
		}
	}
	public void removeDataServiceNode(DataServiceNode d) throws Exception {
		removeDataServiceNode(d.getEPR());
	}
	public void removeDataServiceNode(EndpointReference endpointReference) throws Exception {
		removeDataServiceNode(endpointReference, null);
	}
	public void removeDataServiceNode(EndpointReference endpointReference, String feature) throws Exception {
		RequestInput i = new RequestInput(xmlUtil.toElement(
				"<tns:removeDataServiceNode xmlns:tns=\"" + namespace + "\">" +
				endpointReference.toString("dataService") +
				(feature == null ? "" : "<feature>" + feature + "</feature>") +
				"</tns:removeDataServiceNode>"));
		synchronized (cachedServiceNodes) {
			if(doCache) {
				for(String f : cachedServiceNodes.keySet()) {
					if(feature == null || feature.equals(f))
						cachedServiceNodes.get(f).remove(new DataServiceNode(endpointReference));
				}
			}
		}
		WebServiceClient.getClient(this.epr).invoke(i.getRequest());
	}
	public void removeDataServiceNodes(String feature) throws Exception {
		RequestInput i = new RequestInput(xmlUtil.toElement(
				"<tns:removeDataServiceNodes xmlns:tns=\"" + namespace + "\">" +
					"<feature>" + feature + "</feature>" +
				"</tns:removeDataServiceNodes>"));
		synchronized (cachedServiceNodes) {
			if(doCache) {
				cachedServiceNodes.remove(feature);
			}
		}
		WebServiceClient.getClient(epr).invoke(i.getRequest());
	}
	
	@SuppressWarnings("unchecked")
	public <T> T getObject(String name) throws Exception {
		InvocationRequest req = new InvocationRequest(RequestType.HTTP_GET, null);
		req.cache = false;
		String url = epr.getAddress().trim();
		if(url.endsWith("?wsdl")) 
			url = url.substring(0, url.length() - "?wsdl".length());
		url = url.substring(0, url.lastIndexOf("/")) + "/rest" + 
				url.substring(url.lastIndexOf("/")) + "/objects/" + name;
		EndpointReference _epr = new EndpointReference(new URL(url));
		InvocationResult res = WebServiceClient.getClient(_epr).invoke(req);
		Object result = null;
		if(res.getResult() instanceof Element) {
			Element e = (Element)res.getResult();
			result = e;
			if(e.getNodeName().equals("doc")) {
				if(e.getChildNodes().getLength() == 1) {
					Node n = e.getChildNodes().item(0);
					result = n;
					if(n instanceof Text) {
						result = n.getTextContent();
					}
				} else if(e.getChildNodes().getLength() > 1) {
					String s = xmlUtil.toStringCanonical(e);
					s = s.substring(s.indexOf(">") + 1);
					s = s.substring(0, s.lastIndexOf("<"));
					s = s.trim();
					result = s;
				} else if(e.getChildNodes().getLength() <= 0) {
					result = null;
				}
			}
		}
		return (T)result;
	}

	public boolean putObject(String name, Object obj) throws Exception {
		String objStr = "";
		if(obj instanceof String)
			objStr = (String)obj;
		else if(obj instanceof Element) 
			objStr = xmlUtil.toString((Element)obj);
		else if(obj != null)
			objStr = xmlUtil.toString(obj);
		InvocationRequest req = new InvocationRequest(RequestType.HTTP_POST, objStr);
		req.cache = false;
		String url = epr.getAddress().trim();
		if(url.endsWith("?wsdl")) 
			url = url.substring(0, url.length() - "?wsdl".length());
		url = url.substring(0, url.lastIndexOf("/")) + "/rest" + 
				url.substring(url.lastIndexOf("/")) + "/objects/" + name;
		EndpointReference _epr = new EndpointReference(new URL(url));
		InvocationResult res = WebServiceClient.getClient(_epr).invoke(req);
		if(res.getResult() instanceof Element) {
			try {
				return Boolean.parseBoolean(((Element)res.getResult()).getTextContent().trim());
			} catch (Exception e) {
				logger.info("Unable to parse result of Registry::putObject operation: " + xmlUtil.toString((Element)res.getResult()));
			}
		}
		return true;
	}
	
	public EndpointReference getEPR() {
		return epr;
	}
	
	public boolean isDoCache() {
		return doCache;
	}
	public void setDoCache(boolean doCache) {
		this.doCache = doCache;
	}
	
}
