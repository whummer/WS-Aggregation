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
package at.ac.tuwien.infosys.aggr.node;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.jws.WebMethod;
import javax.jws.WebParam;
import javax.jws.WebResult;
import javax.jws.WebService;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriInfo;

import org.apache.log4j.Logger;
import org.w3c.dom.Element;

import at.ac.tuwien.infosys.aggr.proxy.RegistryProxy;
import at.ac.tuwien.infosys.aggr.util.Constants;
import at.ac.tuwien.infosys.util.Configuration;
import at.ac.tuwien.infosys.util.IDocumentCache;
import at.ac.tuwien.infosys.util.IDocumentCache.CacheEntry;
import at.ac.tuwien.infosys.util.Util;
import at.ac.tuwien.infosys.ws.AbstractNode;
import at.ac.tuwien.infosys.ws.EndpointReference;

@WebService(name="Registry", serviceName="RegistryService", portName="RegistryPort", targetNamespace=Configuration.NAMESPACE)
@Path("registry")
public class Registry extends AbstractNode {

	protected static Logger logger = at.ac.tuwien.infosys.util.Util.getLogger(Registry.class);
	
	// for testing purposes
	public int numServiceNodes = 0;
	public int numAggregatorNodes = 0;

	private final Map<String, List<EndpointReference>> serviceNodes = new HashMap<String, List<EndpointReference>>();
	private final List<EndpointReference> aggregatorNodes = new ArrayList<EndpointReference>();
	//private SortedMap<String,Object> arbitraryObjectsMap = new TreeMap<String,Object>();
	private final IDocumentCache arbitraryObjectsMap = new IDocumentCache.DocumentCache(Constants.PU);
	private Gateway gateway;
	private Util util = new Util();
	
	private static EndpointReference epr;
	
	public static final class RegistryException extends Exception {
		private static final long serialVersionUID = 1L;
		public RegistryException() {}
		public RegistryException(Throwable t) {
			super(t);
		}
	}
	
	public static RegistryProxy getRegistryProxy() {
		return new RegistryProxy(getRegistryEPR());
	}
	
	@GET @Path("services") @Produces(MediaType.APPLICATION_XML)
	@WebMethod(exclude=true)
	public Object getDataServiceNodes(
			@QueryParam("feature") String feature,
			@Context UriInfo info) throws RegistryException {
		try {
			Registry r = (Registry)AbstractNode.getDeployedNodeForResourceUri(info);
			return ((Element)r.getDataServiceNodes(feature)).getOwnerDocument();
		} catch (Exception e) {
			throw new RegistryException(e);
		}
	}

	@WebMethod
	@WebResult(name="result")
	public Object getDataServiceNodes(
			@WebParam(name="feature") String feature) throws RegistryException {
		synchronized (serviceNodes) {
			List<EndpointReference> serviceNodesList = new LinkedList<EndpointReference>();
			if(feature == null || feature.trim().isEmpty()) {
				for(String f : serviceNodes.keySet()) {
					serviceNodesList.addAll(serviceNodes.get(f));
				}
			} else {
				if(serviceNodes.get(feature) == null)
					serviceNodes.put(feature, new ArrayList<EndpointReference>());
				serviceNodesList = serviceNodes.get(feature);
			}
			
			if(numServiceNodes > 0 && numServiceNodes <= serviceNodesList.size())
				serviceNodesList = serviceNodesList.subList(0, numServiceNodes);
			StringBuilder b = new StringBuilder();
			b.append("<result>");
			for(EndpointReference epr : serviceNodesList) {
				b.append(epr.toString());
			}
			b.append("</result>");
			try {
				return util.xml.toElement(b.toString());
			} catch (Exception e) {
				throw new RegistryException(e);
			}
		}
	}

	@WebMethod
	public EndpointReference getRandomAggregatorNode() {
		List<EndpointReference> aggregators = getActualAggregatorsList();
		if(aggregators.size() <= 0)
			return null;
		return aggregators.get((int)(Math.random()*(double)aggregators.size()));
	}

	/**
	 * Returns the subset of aggregators with size limitation of numAggregatorNodes.
	 * @return
	 */
	private List<EndpointReference> getActualAggregatorsList() {
		List<EndpointReference> result = aggregatorNodes;
		if(this.numAggregatorNodes > 0 && this.numAggregatorNodes <= aggregatorNodes.size())
			result = aggregatorNodes.subList(0, this.numAggregatorNodes);
		return result;
	}

	@GET @Path("aggregators") @Produces(MediaType.APPLICATION_XML)
	@WebMethod(exclude=true)
	public Object getAggregatorNodes(
			@QueryParam("maxNodes") String numAggregatorNodes,
			@Context UriInfo info) throws RegistryException {
		try {
			Registry r = (Registry)AbstractNode.getDeployedNodeForResourceUri(info);
			Integer num = util.math.toInteger(numAggregatorNodes);
			return ((Element)r.getAggregatorNodes(num)).getOwnerDocument();
		} catch (Exception e) {
			throw new RegistryException(e);
		}
	}

	@WebMethod @WebResult(name="result")
	public Object getAggregatorNodes(
			@WebParam(name="numAggregatorNodes") Integer numAggregatorNodes) throws RegistryException {
		synchronized (aggregatorNodes) {
			try {
				List<EndpointReference> result = aggregatorNodes;
				if(numAggregatorNodes == null)
					numAggregatorNodes = -1;
				if(numAggregatorNodes > 0 && numAggregatorNodes <= aggregatorNodes.size())
					result = aggregatorNodes.subList(0, numAggregatorNodes);
				if(this.numAggregatorNodes > 0 && this.numAggregatorNodes <= aggregatorNodes.size())
					result = aggregatorNodes.subList(0, this.numAggregatorNodes);
				
				String out = "<result>";
				for(EndpointReference epr : result) {
					out += epr.toString();
				}
				out += "</result>";
				return util.xml.toElement(out);
			} catch (Exception e) {
				e.printStackTrace();
				return null;
			}
		}
	}

	@GET @Path("gateway") @Produces(MediaType.APPLICATION_XML)
	@WebMethod(exclude=true)
	public Object getGateway(
			@Context UriInfo info) throws RegistryException {
		try {
			Element result = util.xml.toElement("<result/>");
			Registry r = (Registry)AbstractNode.getDeployedNodeForResourceUri(info);
			Object gateway = r.getGateway();
			if(r.getGateway() != null) {
				result = (Element)gateway;
			}
			return result.getOwnerDocument();
		} catch (Exception e) {
			throw new RegistryException(e);
		}
	}

	@WebMethod
	@WebResult(name="result")
	public synchronized Object getGateway() throws RegistryException {
		if(gateway == null)
			return null;
		try {
			Element result = util.xml.toElement("<result/>");
			util.xml.appendChild(result, util.xml.toElement(gateway.getEPR()));
			return result;
		} catch (Exception e) {
			throw new RegistryException(e);
		}
	}

	@WebMethod
	public synchronized void setGateway(
			@WebParam(name="gateway") EndpointReference gateway) {
		synchronized (aggregatorNodes) {
			if(this.gateway == null || !this.gateway.getEPR().equals(gateway)) {
				System.out.println("set gateway: " + gateway);
			}
			this.gateway = new Gateway();
			this.gateway.setEPR(gateway);			
		}
	}

	@WebMethod
	public void addAggregatorNode(
			@WebParam(name="aggregator") EndpointReference aggregator) {
		synchronized (aggregatorNodes) {
			if(!aggregatorNodes.contains(aggregator)) {
				System.out.println("add aggregator: " + aggregator.getAddress());
				aggregatorNodes.add(aggregator);
			}
		}
		RegistryProxy.resetCache();
	}

	@WebMethod
	public void removeAggregatorNode(
			@WebParam(name="aggregator") EndpointReference aggregator) {
		synchronized (aggregatorNodes) {
			if(aggregatorNodes.contains(aggregator)) {
				System.out.println("removing aggregator: " + aggregator.getAddress());
				aggregatorNodes.remove(aggregator);
			}
		}
	}
	
	/* METHODS FOR ACCESS TO ARBITRARY KEY/VALUE OBJECTS */

	@DELETE @Path("objects/{key}") 
	@Produces(MediaType.APPLICATION_XML)
	@WebMethod(exclude=true)
	public Object removeArbitraryObject(
			@PathParam("key") String key,
			@Context UriInfo info) throws RegistryException {
		try {
			Registry r = (Registry)AbstractNode.getDeployedNodeForResourceUri(info);
			//System.out.println("DELETE: " + r + " - " + key);
			if(r == null || key == null)
				return "<success>false</success>";
			key = "<objectMapKey>" + key + "</objectMapKey>";
			synchronized (r.arbitraryObjectsMap) {
				//r.arbitraryObjectsMap.remove(key);
				r.arbitraryObjectsMap.put(key, "");
			}
		} catch (Exception e) {
			throw new RegistryException(e);
		}
		return "<success>true</success>";
	}
	@POST @Path("objects/{key}") 
	@Produces(MediaType.APPLICATION_XML)
	@WebMethod(exclude=true)
	public Object putArbitraryObject(
			@PathParam("key") String key,
			String value,
			@Context UriInfo info) throws RegistryException {
		try {
			Registry r = (Registry)AbstractNode.getDeployedNodeForResourceUri(info);
			if(r == null || key == null)
				return "<success>false</success>";
			key = "<objectMapKey>" + key + "</objectMapKey>";
			logger.info("POST object: " + key + " = " + value);
			synchronized (r.arbitraryObjectsMap) {
				r.arbitraryObjectsMap.put(key, value);
			}

		} catch (Exception e) {
			throw new RegistryException(e);
		}
		return "<success>true</success>";
	}
	@GET @Path("objects/{key}") 
	@WebMethod(exclude=true)
	public Object getArbitraryObject(
			@PathParam("key") String key,
			@Context UriInfo info) throws RegistryException {
		try {
			
			Registry r = (Registry)AbstractNode.getDeployedNodeForResourceUri(info);
			if(r == null || key == null)
				return null;
			key = "<objectMapKey>" + key + "</objectMapKey>";
			//System.out.println("GET: " + r + " - " + key + " - " + r.arbitraryObjectsMap.get(key));
			CacheEntry e = r.arbitraryObjectsMap.get(key);
			if(e == null)
				return null;
			return e.value;
			
		} catch (Exception e) {
			throw new RegistryException(e);
		}
	}
	@GET @Path("objects") 
	@Produces(MediaType.APPLICATION_XML)
	@WebMethod(exclude=true)
	public Object getArbitraryObjects(@Context UriInfo info) throws RegistryException {
		try {
			Registry r = (Registry)AbstractNode.getDeployedNodeForResourceUri(info);
			if(r == null)
				return null;
			StringBuilder b = new StringBuilder();
			b.append("<objects>\n");
			synchronized (r.arbitraryObjectsMap) {
				String like = "<objectMapKey>%";
				for(String key : r.arbitraryObjectsMap.getKeys(like)) {
					CacheEntry e = r.arbitraryObjectsMap.get(key);
					if(e != null) {
						String keyString = util.xml.toElement(key).getTextContent().trim();
						b.append("<object><key>" + keyString + "</key><value>");
						b.append(e.value);
						b.append("</value></object>\n");
					} else {
						logger.info("DocumentCache delivered a key '" + key + "' which returns a NULL object. This should not happen!");
					}
				}
			}
			b.append("</objects>");
			return b.toString();
		} catch (Exception e) {
			throw new RegistryException(e);
		}
	}
	
	
	
	/* METHODS FOR ACCESS TO DATA SERVICE NODES */

	@WebMethod
	public void removeDataServiceNode(
			@WebParam(name="dataService") EndpointReference dataService,
			@WebParam(name="feature") String feature) {
		synchronized (serviceNodes) {
			boolean removed = false;
			for(String s : serviceNodes.keySet()) {
				if(feature == null || feature.equals(s)) {
					while(serviceNodes.get(s).remove(dataService)) {
						removed = true;
					}
				}
			}
			if(removed) {
				System.out.println("removing data service: " + dataService.getAddress() + (feature == null ? "" : (" for feature " + feature)));
			}
		}
	}

	@WebMethod
	public void removeDataServiceNodes(
			@WebParam(name="feature") String feature) {
		synchronized (serviceNodes) {
			serviceNodes.remove(feature);
		}
	}

	@WebMethod
	public void addDataServiceNode(
			@WebParam(name="feature") String feature, 
			@WebParam(name="serviceEPR") EndpointReference serviceEPR) {
		synchronized (serviceNodes) {
			if(serviceNodes.get(feature) == null)
				serviceNodes.put(feature, new ArrayList<EndpointReference>());
			if(!serviceNodes.get(feature).contains(serviceEPR))
				serviceNodes.get(feature).add(serviceEPR);
			Collections.shuffle(serviceNodes.get(feature));
		}
	}

	private static EndpointReference getRegistryEPR() {
		if(epr == null) {
			try {
				synchronized(Registry.class) {
					epr = new EndpointReference("<wsa:EndpointReference xmlns:wsa=\"http://schemas.xmlsoap.org/ws/2004/08/addressing\" " +
						"xmlns:tns=\"" + Configuration.NAMESPACE + "\">" +
						"<wsa:Address>" + Configuration.getRegistryAddress() + "</wsa:Address>" +
						"<wsa:ServiceName PortName=\"GatewayPort\">" +
							"tns:GatewayService" +
						"</wsa:ServiceName>" +
					"</wsa:EndpointReference>");
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return epr;
	}

	protected Runnable getTerminateTask(TerminateRequest params) {
		return null;
	}

	@GET @Path("terminate")
	@WebMethod(exclude=true)
	public String terminate(@Context UriInfo info) throws RegistryException {
		try {
			
			Registry r = (Registry)AbstractNode.getDeployedNodeForResourceUri(info);
			if(r == null)
				return "";
			r.terminate(new TerminateRequest());

		} catch (Exception e) {
			throw new RegistryException(e);
		}
		return "";
	}
	
}
