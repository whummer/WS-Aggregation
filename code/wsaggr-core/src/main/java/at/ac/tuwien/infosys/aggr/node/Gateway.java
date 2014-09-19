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

import io.hummer.util.Configuration;
import io.hummer.util.NotImplementedException;
import io.hummer.util.Util;
import io.hummer.util.par.GlobalThreadPool;
import io.hummer.util.test.TestUtil;
import io.hummer.util.ws.AbstractNode;
import io.hummer.util.ws.EndpointReference;
import io.hummer.util.ws.WebServiceClient;
import io.hummer.util.ws.request.InvocationRequest;
import io.hummer.util.ws.request.InvocationResult;
import io.hummer.util.ws.request.RequestType;
import io.hummer.util.xml.JAXBTypes;

import java.net.ConnectException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.annotation.Resource;
import javax.jws.WebMethod;
import javax.jws.WebParam;
import javax.jws.WebResult;
import javax.jws.WebService;
import javax.jws.soap.SOAPBinding;
import javax.jws.soap.SOAPBinding.ParameterStyle;
import javax.jws.soap.SOAPBinding.Style;
import javax.jws.soap.SOAPBinding.Use;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.UriInfo;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.namespace.QName;
import javax.xml.ws.WebServiceContext;
import javax.xml.ws.handler.Handler;
import javax.xml.ws.handler.MessageContext;

import org.apache.log4j.Logger;
import org.w3c.dom.Element;

import at.ac.tuwien.infosys.aggr.AggregationClient;
import at.ac.tuwien.infosys.aggr.account.CredentialsValidation;
import at.ac.tuwien.infosys.aggr.account.User;
import at.ac.tuwien.infosys.aggr.account.UserSession;
import at.ac.tuwien.infosys.aggr.account.UsernameSOAPHeader;
import at.ac.tuwien.infosys.aggr.events.query.EventingQueryCoordinator;
import at.ac.tuwien.infosys.aggr.events.query.EventingQueryCoordinator.ActiveQueryResult;
import at.ac.tuwien.infosys.aggr.monitor.ModificationNotification;
import at.ac.tuwien.infosys.aggr.monitor.ModificationNotification.EventStreamIdSOAPHeader;
import at.ac.tuwien.infosys.aggr.node.Registry.RegistryException;
import at.ac.tuwien.infosys.aggr.performance.AggregatorPerformanceInfo;
import at.ac.tuwien.infosys.aggr.persist.SavedAggregationRequest;
import at.ac.tuwien.infosys.aggr.proxy.AggregatorNodeProxy;
import at.ac.tuwien.infosys.aggr.proxy.GatewayProxy;
import at.ac.tuwien.infosys.aggr.request.AbstractInput;
import at.ac.tuwien.infosys.aggr.request.AggregationRequest;
import at.ac.tuwien.infosys.aggr.request.AggregationResponse;
import at.ac.tuwien.infosys.aggr.request.RequestInput;
import at.ac.tuwien.infosys.aggr.request.SavedQueryInput;
import at.ac.tuwien.infosys.aggr.strategy.StrategyChain;
import at.ac.tuwien.infosys.aggr.strategy.Topology;
import at.ac.tuwien.infosys.aggr.strategy.TopologyOptimizerVNS;
import at.ac.tuwien.infosys.aggr.strategy.TopologyOptimizerVNS.OptimizationParameters;
import at.ac.tuwien.infosys.aggr.strategy.TopologyUtil;
import at.ac.tuwien.infosys.aggr.util.Constants;
import at.ac.tuwien.infosys.aggr.util.Invoker.InvokerTask;
import at.ac.tuwien.infosys.aggr.util.RequestAndResultQueues;
import at.ac.tuwien.infosys.aggr.util.RequestAndResultQueues.RequestWorker;

@WebService(targetNamespace=Configuration.NAMESPACE)
@Path("gateway")
public class Gateway extends AbstractNode implements RequestWorker<AggregationRequest, AggregationResponse> {

	private static Logger logger = Util.getLogger(Gateway.class);
	public static final String REGISTRY_FEATURE_GATEWAY = "__gateway_feature__";

	private static Util util = new Util();
	private static TestUtil testUtil = new TestUtil();
	private StrategyChain strategyChain;
	private RequestAndResultQueues<AggregationRequest, AggregationResponse> queues = 
		new RequestAndResultQueues<AggregationRequest, AggregationResponse>(this);
	private Map<String, EndpointReference> masters = new HashMap<String, EndpointReference>();
	private Map<String, Topology> topologyCache = new HashMap<String, Topology>();
	
	@Resource
	private WebServiceContext context;

	public static final class GatewayException extends Exception {
		private static final long serialVersionUID = 1L;
		public GatewayException() {}
		public GatewayException(Throwable t) {
			super(t);
		}
	}

	public Gateway() {
		strategyChain = StrategyChain.loadDefault(this);
		strategyChain.setOwner(this);
	}

	private AggregationResponse aggregate(AggregationRequest request) throws GatewayException {
		return aggregate(request, null);
	}
	@WebMethod
	@WebResult(name="result")
	@SOAPBinding(style=Style.DOCUMENT, use=Use.LITERAL, parameterStyle=ParameterStyle.BARE)
	public AggregationResponse aggregate(@WebParam AggregationRequest request,
			@WebParam(header=true, name=UsernameSOAPHeader.LOCALNAME,
			targetNamespace=UsernameSOAPHeader.NAMESPACE) 
			UsernameSOAPHeader username) throws GatewayException {

		long before = System.currentTimeMillis();
		try {
			
			// check if we need to load a saved request from the DB..
			List<SavedQueryInput> compositeInputs = request.getCompositeQueryInputs();
			if(compositeInputs != null && !compositeInputs.isEmpty()) {
				
				if(compositeInputs.size() > 1) {
					throw new NotImplementedException(
							"Multiple composite aggregation queries not supported.");
				}
				
				String queryName = compositeInputs.get(0).getTheContent().toString().trim();
				logger.info("Loading query named '" + queryName + "', referenced from composite request.");
				
				AggregationRequest savedRequest = 
							getPersistedAggregationRequest(queryName, username);
				// important: add all inputs of the request that was sent along this invocation
				// --> used to pass user parameters to saved aggregation requests
				for(AbstractInput i : request.getAllInputs()) {
					// of course, we need to fix the IDs of the inputs that we add, 
					// if there are duplicates. TODO: what if the queries contain a 
					// reference to a specific ID, e.g. $1{//foo} ??! FIXME
					if(!(i instanceof SavedQueryInput)) {
						if(!savedRequest.getInputsByID(i.getExternalID()).isEmpty()) {
							i.setExternalID("" + savedRequest.getMaxNumericExternalInputID() + 1);
						}
						savedRequest.getInputs().addInput(i);
					}
				}
				if(!util.str.isEmpty(request.getQueries().getQuery())) {
					savedRequest.getQueries().setQuery(request.getQueries().getQuery());
				}
				request = savedRequest;
			}

			/* set creator username of the query */
			if(username != null)
				request.setCreatorUsername(username.getValue());
			
			/* treat specially if we have an eventing input (i.e., event subscription) 
			 		--> create active query topology and return topology ID.
			 		(in fact, this is also done by the gateway..) */
			if(request.containsEventingOrMonitoringInput()) {
				AggregationResponse r = new AggregationResponse(request);
				String id = createTopology(request.getTopologyID(), null, request);
				r.setTopologyID(id);
				return r;
			}
			
			long requestID = InvokerTask.getNewRequestID();
			request.setInternalID(requestID);
			if(logger.isDebugEnabled()) logger.debug("gateway request: " + util.xml.toString(request));
	
			if(!strategyChain.doDebugQueries()) {
				request.setDebug(false);
			}
			
			queues.putRequest(request);
			
			// request will be handled by a background worker thread..
			
			AggregationResponse response = queues.takeResponse(requestID);
			
			if(request.isDebug()) {
				response.buildGraphFromDebugInfo();
				logger.info("Built dependency graph debug information: " + util.xml.toString(response.getGraph()));
			}
			
			if(response.isError()) {
				if(response.isException())
					throw (Exception)response.getResult();
				throw new Exception("Illegal 'null' result retrieved in Gateway.");
			}
			
			return response;

		} catch (Exception e) {
			
			Throwable toReport = e;
			/* the first cause in the exception hierarchy is usually the exception from WebServiceClient. */
			toReport = (toReport.getCause() != null ? toReport.getCause() : toReport);
			/* the second cause in the exception hierarchy is the actual cause we are interested in. */
			toReport = (toReport.getCause() != null ? toReport.getCause() : toReport);
			
			logger.info("Exception during aggregation: " + toReport);
			try {
				context.getMessageContext().put(MessageContext.HTTP_RESPONSE_CODE, 200);
			} catch (IllegalStateException e2) {
				/* the exception 
				 * "java.lang.IllegalStateException: getMessageContext() can only be called while servicing a request" 
				 * may happen if this method gets called directly from another method within a REST 
				 * request (because message context is only available during a SOAP request). */
			}
			throw new GatewayException(toReport);
		} finally {
			logger.info("Total request processing time: " + ((double)(System.currentTimeMillis() - before)/1000.0) + " seconds");
		}
	}

	private AggregatorNode selectMasterWithRetries(AggregationRequest request) {
		try {
			String topologyID = request.getTopologyID();
			AggregatorNode master = strategyChain.selectMasterAggregator(topologyID, request);
			if(master == null) {
				return null;
			}
			int socketTestRetries = 3;
			for(int i = 0; i < socketTestRetries; i ++) {
				if(util.net.isPortOpen(master.getEPR().getAddress())) {
					break;
				}
				/* previous master aggregator suggestion was invalid
				 * --> try again with a new master suggestion. */
				logger.info("Suggested master aggregator is apparently unavailable (" + 
						(socketTestRetries - i) + " retries left): " + master);
				master = strategyChain.selectMasterAggregator(topologyID, request);
			}
			if(logger.isDebugEnabled()) logger.debug("Selected master aggregator: " + master);
			return master;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
	
	@WebMethod(exclude=true)
	public AggregationResponse handleRequest(AggregationRequest request) {
		
		try {
			// make sure every request input has a unique ID.
			ensureInputIDs(request);
			prepareForWAQLPreprocessing(request);

			int numRetries = 3;
			int numRetriesLeft = numRetries;

			do {
				AggregatorNode master = null;
				try {
					master = selectMasterWithRetries(request);

					if(master == null)
						return new AggregationResponse(request, new Exception("No master aggregator node could be selected. Are there any aggregators deployed and entered in the registry?"));

					WebServiceClient client = WebServiceClient.getClient(master.getEPR());
					InvocationRequest aggrRequest = new RequestInput(util.xml.toElement(request)).getRequest();
					if(!testUtil.isNullOrTrue(request.getTimeout())) {
						aggrRequest.timeout = false;
					}
					logger.info("Selected master aggregator: " + master.getEPR().getAddress());
					if(logger.isDebugEnabled()) logger.debug("Sending aggregation request " + request + " to master aggregator " + master);
					InvocationResult res = client.invoke(aggrRequest);
					Element result = (Element)res.getResult();
					result = (Element)result.getFirstChild();
					
					AggregationResponse responseFromAggregator = util.xml.toJaxbObject(AggregationResponse.class, result);
					
					return new AggregationResponse(responseFromAggregator, result);
				} catch (Exception e) {
					List<Class<?>> causes = util.exc.getAllCauseClasses(e);
					if(!causes.contains(SocketException.class) &&
							!causes.contains(ConnectException.class) &&
							!causes.contains(SocketTimeoutException.class)) {
						throw e;
					}
					logger.warn("Socket exception when contacting master aggregator: " + master);
					strategyChain.handleUnreachableNode(master);
				}
			} while (numRetriesLeft-- > 0);

			throw new Exception("Retried " + numRetries + " times to contact suggested master aggregators, giving up.");

		} catch (Exception e) {
			logger.error("Error handling request in gateway: " + (e.getCause() != null ? e.getCause() : e), e);
			return new AggregationResponse(request, e);
		}
	}

	private void prepareForWAQLPreprocessing(AggregationRequest request) {
		for(AbstractInput in : request.getAllInputs()) {
			if(in instanceof RequestInput) {
				RequestInput i = (RequestInput)in;
				if(i.getServiceURL() != null) {
					i.setServiceURL("<a>" + i.getServiceURL() + "</a>/text()");
					if(logger.isDebugEnabled()) logger.debug("Preparing input for WAQL processing; new serviceURL: " + i.getServiceURL());
				}
			}
		}
	}

	private void ensureInputIDs(AggregationRequest request) {
		if(request == null)
			return;
		int maxID = request.getMaxNumericExternalInputID();
		maxID = maxID < 0 ? 0 : maxID;
		for(AbstractInput in : request.getAllInputs()) {
			if(in.getExternalID() == null || in.getExternalID().trim().equals("")) {
				in.setExternalID("" + (++maxID));
			}
			if(in.getUniqueID() == null || in.getUniqueID().trim().equals("")) {
				in.setUniqueID(in.getExternalID());
			}
		}
	}

	@WebResult(name="topologyID")
	@WebMethod
	public String createTopology(
			@WebParam(name="type") String type,
			@WebParam(name="feature") List<String> feature,
			@WebParam(name="request") AggregationRequest request) throws GatewayException {

		try {

			if(feature == null)
				feature = new LinkedList<String>();
			if(type == null)
				type = "tree(1,0)";
			
			// make sure every request input has a unique ID.
			ensureInputIDs(request);

			AggregatorNode master = strategyChain.selectMasterAggregator(null, request);

			if(master == null || master.getEPR() == null)
				throw new RuntimeException("No master aggregator node could be selected. " +
						"Are there any aggregators deployed and entered in the registry?");

			AggregationClient client = new AggregationClient(master.getEPR(), true);
			String topologyID = client.createTopology(type, feature, request);

			masters.put(topologyID, master.getEPR());

			return topologyID;
		} catch (Exception e) {
			throw new GatewayException(e);
		}
	}

	@WebResult(name="success")
	@WebMethod
	public boolean destroyTopology(@WebParam(name="topologyID") String topologyID) throws GatewayException {
		EndpointReference epr = null;
		synchronized (masters) {
			epr = masters.remove(topologyID);
		}
		synchronized (topologyCache) {
			topologyCache.remove(topologyID);
		}
		logger.debug("Destroying topology with ID " + topologyID + " - " + epr);
		if(epr != null) {
			AggregationClient c = new AggregationClient(epr, true);
			try {
				c.destroyTopology(topologyID);	
			} catch (Exception e) {
				throw new GatewayException(e);
			}
			return true;
		}
		return false;
	}

	@WebMethod
	@WebResult(name="EndpointReference", targetNamespace=EndpointReference.NS_WS_ADDRESSING)
	public EndpointReference getMasterAggregator(@WebParam(name="topologyID") String topologyID) {
		return masters.get(topologyID);
	}
	
	@WebResult(name="Topology")
	@WebMethod
	public List<Topology> getTopology(@WebParam(name="topologyID") String topologyID) throws GatewayException {
		//if("DefaultTopology".equals(topologyID)) {
		//	return Arrays.asList(getTestTopology()); // code for testing..
		//}

		synchronized (topologyCache) {
			if(topologyCache.containsKey(topologyID))
				return Arrays.asList(topologyCache.get(topologyID));
		}

		EndpointReference epr = null;
		if(topologyID != null && !topologyID.trim().isEmpty()) {
			synchronized (masters) {
				epr = masters.get(topologyID);
			}
			if(epr != null) {
				AggregatorNodeProxy c = new AggregatorNodeProxy(epr, true);
				try {
					Topology t = c.getTopology(topologyID);
					if(t != null) {
						synchronized (topologyCache) {
							topologyCache.put(topologyID, t);
						}
						return Arrays.asList(t);
					}	
				} catch (Exception e) {
					throw new GatewayException(e);
				}
			}
		} else {
			List<Topology> tops = new LinkedList<Topology>();
			
			return tops;
		}
		return null;
	}
	
	/**
	 * The gateway can act as an 'event router':
	 * This method is used to forward incoming events to aggregator nodes.
	 * @param event
	 * @param header
	 * @throws GatewayException
	 */
	@SOAPBinding(style=Style.DOCUMENT, use=Use.LITERAL, parameterStyle=ParameterStyle.BARE)
	@WebMethod(operationName="Event")
	public void onEvent(
			@WebParam final ModificationNotification event,
			@WebParam(header=true, 
					name=EventingQueryCoordinator.LOCALNAME_FORWARD_EVENT_TO,
					targetNamespace=Configuration.NAMESPACE) 
			final EndpointReference forwardTo,
			@WebParam(header=true, 
					name=ModificationNotification.LOCALNAME_EVENTSTREAM_ID,
					targetNamespace=Configuration.NAMESPACE) 
			final EventStreamIdSOAPHeader eventStreamID) throws GatewayException {

		if(forwardTo == null || eventStreamID == null)
			return;
		GlobalThreadPool.execute(new Runnable() {
			public void run() {
				try {
					if(logger.isDebugEnabled()) logger.debug("Forwarding event to aggregator " + forwardTo.getAddress());
					AggregatorNodeProxy p = new AggregatorNodeProxy(forwardTo, true);
					p.onEvent(event, util.xml.toElement(eventStreamID));
				} catch (Exception e) {
					logger.info("Unable to forward event to aggregator " + (forwardTo == null ? null : forwardTo.getAddress()) + 
							", exception: " + e + ", cause(s): " + util.exc.getAllCauseClasses(e) + " , event: \n" + event);
					logger.warn("Unable to forward event to aggregator.", e);
				}
			}
		});
	}

	@WebResult(name="result")
	@WebMethod
	public List<Topology> collectAllTopologies() throws GatewayException {
		try {
			return TopologyUtil.collectAllTopologies();	
		} catch (Exception e) {
			throw new GatewayException(e);
		}
	}

	@WebResult(name="result")
	@WebMethod
	public ActiveQueryResult pollActiveQueryResult(
			@WebParam(name="topologyID") String topologyID, 
			@WebParam(name="clientID") String clientID, 
			@WebParam(name="onlyUpdates") Boolean onlyUpdates, 
			@WebParam(name="onlyOnce") Boolean onlyOnce) throws GatewayException {
		EndpointReference epr = masters.get(topologyID);
		if(epr == null)
			return null;
		try {
			ActiveQueryResult result = new AggregatorNodeProxy(epr).pollActiveQueryResult(
				topologyID, clientID, onlyUpdates, onlyOnce);
			return result;	
		} catch (Exception e) {
			throw new GatewayException(e);
		}
	}

	@WebMethod
	public boolean updateTopology(
			@WebParam(name="topologyID") String topologyID,
			@WebParam(name="updates") Object updates) throws GatewayException {
		Util util = new Util();
		Topology topology = null;
		if(updates instanceof Element) {
			List<Element> children = util.xml.getChildElements((Element)updates);
			Element firstChild = children.get(0);
			if(children.size() > 0 && firstChild.getLocalName().equalsIgnoreCase("Topology")) {
				try {
					topology = util.xml.toJaxbObject(Topology.class, firstChild);	
				} catch (Exception e) {
					throw new GatewayException(e);
				}
			}
		}
		if(updates instanceof Topology) {
			topology = (Topology)updates;
		}
		if(topology != null) {
			if(topology.getTopologyID() == null || topology.getTopologyID().isEmpty())
				topology.setTopologyID(topologyID);
			topologyCache.put(topologyID, topology);
		}
		return true;
	}
	
	@WebMethod
	@WebResult(name="queryID")
	public List<String> getActiveQueries() throws GatewayException {
		List<String> result = new LinkedList<String>();
		List<String> ids = new LinkedList<String>();
		synchronized (masters) {
			ids.addAll(masters.keySet());
		}
		for(String m : ids) {
			AggregatorNodeProxy p = new AggregatorNodeProxy(masters.get(m));
			try {
				ActiveQueryResult r = p.pollActiveQueryResult(m, null, null, true);
				if(r != null)
					result.add(m);	
			} catch (Exception e) {
				throw new GatewayException(e);
			}
		}
		return result;
	}
	

	/* METHODS FOR ACCESS TO STORED AGGREGATION REQUESTS */

	
	@WebResult(name="success")
	@WebMethod
	public boolean persistAggregationRequest(
			@WebParam(name="name") String name, 
			@WebParam(name="request") AggregationRequest request,
			@WebParam(name="overwrite") boolean overwrite,
			@WebParam(name="public") boolean isPublic,
			@WebParam(header=true, name=UsernameSOAPHeader.LOCALNAME,
			targetNamespace=UsernameSOAPHeader.NAMESPACE) 
			UsernameSOAPHeader username) throws RegistryException {
		try {
			
			SavedAggregationRequest req = new SavedAggregationRequest();
			req.setName(name);
			req.setPublic(isPublic);
			String requestString = util.xml.toString(request);
			req.setRequest(requestString);
			if(username != null) {
				User creator = User.load(username.getValue());
				if(creator != null)
					req.setCreator(creator);
			}
			try {
				logger.info("Saving aggregation request '" + name + "' (public=" + isPublic + ") for user " + username);
				req = SavedAggregationRequest.save(req, overwrite);
			} catch (Exception e) {
				throw new Exception(e);
			}
			return true;
			
		} catch (Exception e) {
			throw new RegistryException(e);
		}
	}
	@WebResult(name="request")
	@WebMethod
	public AggregationRequest getPersistedAggregationRequest(
			@WebParam(name="name") String name,
			@WebParam(header=true, name=UsernameSOAPHeader.LOCALNAME,
			targetNamespace=UsernameSOAPHeader.NAMESPACE) 
			UsernameSOAPHeader username) throws RegistryException {
		
		if(name != null) name = name.trim();
		logger.info("Loading persisted aggregation request '" + name + "' for user " + username);
		try {
			User creator = username != null ? User.load(username.getValue()) : null;
			SavedAggregationRequest saved = SavedAggregationRequest.load(name, creator);
			AggregationRequest request = util.xml.toJaxbObject(
					AggregationRequest.class, util.xml.toElement(saved.getRequest()));
			return request.cloneCanonical();
		} catch (Throwable e) {
			logger.info("Unable to load request from DB: " + e);
			if(context != null) {
				context.getMessageContext().put(MessageContext.HTTP_RESPONSE_CODE, 200);
			}
			throw new RegistryException(e);
		}
	}
	
	
	@GET @Path("requests")
	@WebMethod(exclude=true)
	public JAXBTypes.ObjectList getAllPersistedRequestsREST(
			@QueryParam("pattern") String namePattern) {
		return new JAXBTypes.ObjectList(SavedAggregationRequest.loadAllNames(namePattern, null));
	}

	@WebMethod @WebResult(name="requestName")
	public List<String> getAllPersistedRequests(
			@WebParam(name="pattern") String namePattern,
			@WebParam(header=true, name=UsernameSOAPHeader.LOCALNAME,
			targetNamespace=UsernameSOAPHeader.NAMESPACE) 
			UsernameSOAPHeader username) {
		String name = username != null ? username.getValue() : null;
		return SavedAggregationRequest.loadAllNames(namePattern, name);
	}

	@GET @Path("execute/{requestName}")
	@WebMethod(exclude=true)
	public Object executeSavedRequest(
			@PathParam("requestName") String requestName,
			@Context UriInfo info) throws GatewayException {
		try {
			Gateway g = (Gateway)AbstractNode.getDeployedNodeForResourceUri(info);
			AggregationRequest request = getPersistedAggregationRequest(requestName, null);
			Object o = g.aggregate(request).getResult();
			if(o instanceof Element) 
				return util.xml.toString((Element)o);
			return o;	
		} catch (Exception e) {
			throw new GatewayException(e);
		}
	}

	@GET @Path("download")
	@WebMethod(exclude=true)
	public String getURL(
			@QueryParam("url") String url) {
		try {
			return util.io.readFile(url);
		} catch (Exception e) {
			logger.info("Unable to download URL '" + url + "': " + e);
			return null;
		}
	}

	/* METHODS FOR USER/ACCOUNT MANAGEMENT */

	@XmlRootElement(name="login", namespace=Configuration.NAMESPACE)
	public static class LoginRequest {
		@XmlElement
		private String username;
		@XmlElement
		private String passhash;

		public LoginRequest() {}
		public LoginRequest(String username, String passhash) {
			this.username = username;
			this.passhash = passhash;
		}
		
		@XmlTransient
		public String getUsername() {
			return username;
		}
		@XmlTransient
		public String getPasshash() {
			return passhash;
		}
	}
	@XmlRootElement(namespace=Configuration.NAMESPACE)
	public static class LoginResponse {
		@XmlElement
		private boolean success;
		@XmlElement(name="sessionID")
		private String sessionID;
		@XmlElement
		private String expiry;
		@XmlElement
		private String message;

		@XmlTransient
		public boolean isSuccess() {
			return success;
		}
		public void setSessionID(String sessionID) {
			this.sessionID = sessionID;
		}
		@XmlTransient
		public String getExpiry() {
			return expiry;
		}
		public void setExpiry(String expiry) {
			this.expiry = expiry;
		}
		@XmlTransient
		public String getSessionID() {
			return sessionID;
		}
		public void setSuccess(boolean success) {
			this.success = success;
		}
		@XmlTransient
		public String getMessage() {
			return message;
		}
		public void setMessage(String message) {
			this.message = message;
		}
	}

	@XmlRootElement(name="register", namespace=Configuration.NAMESPACE)
	public static class RegisterRequest {
		@XmlElement
		private String username;
		@XmlElement
		private String passhash;
		@XmlElement
		private String email;
		@XmlElement
		private String captchaChallenge;
		@XmlElement
		private String captchaResponse;

		@XmlTransient
		public String getUsername() {
			return username;
		}
		@XmlTransient
		public String getPasshash() {
			return passhash;
		}
		@XmlTransient
		public String getEmail() {
			return email;
		}
		@XmlTransient
		public String getCaptchaChallenge() {
			return captchaChallenge;
		}
		@XmlTransient
		public String getCaptchaResponse() {
			return captchaResponse;
		}
	}
	@XmlRootElement(namespace=Configuration.NAMESPACE)
	public static class RegisterResponse {
		@XmlElement
		private boolean success;
		@XmlElement
		private String message;

		@XmlTransient
		public boolean isSuccess() {
			return success;
		}
		@XmlTransient
		public String getMessage() {
			return message;
		}
	}

	@WebMethod
	@SOAPBinding(parameterStyle=ParameterStyle.BARE)
	public synchronized RegisterResponse register(RegisterRequest request) {
		RegisterResponse r = new RegisterResponse();
		r.success = false;

		/* perform captcha verification */
		if(!util.str.isEmpty(Constants.CAPTCHA_PRIVATE_KEY)) {
			String url = Configuration.getValue(Configuration.PROP_CAPTCHA_VERIFY_URL);
			try {
				WebServiceClient c = WebServiceClient.getClient(new EndpointReference(new URL(url)));
				InvocationResult res = c.invoke(new InvocationRequest(RequestType.HTTP_POST, 
							"privatekey=" + Constants.CAPTCHA_PRIVATE_KEY +
							"&remoteip=" + Constants.CAPTCHA_DEFAULT_REMOTE_IP +
							"&challenge=" + request.captchaChallenge +
							"&response=" + request.captchaResponse
						));
				String resString = "" + res.getResult();
				if(res.getResult() instanceof Element) {
					resString = res.getResultAsElement().getTextContent();
				}
				if(!resString.contains("true") || resString.contains("false")) {
					r.message = "Invalid captcha text entered. Please try again.";
					return r;
				}
	
			} catch (Exception e) {
				logger.warn("Error validating captcha.", e);
				r.message = "Error validating captcha.";
				return r;
			}
		}
		
		String nameRegex = Constants.REGEX_USERNAME;
		String hexRegex = Constants.REGEX_HEXCODE;
		String emailRegex = Constants.REGEX_EMAIL;

		if(util.str.isEmpty(request.username) || !request.username.matches(nameRegex)) {
			r.message = "Invalid user name. Valid pattern is: " + nameRegex;
			return r;
		}
		if(util.str.isEmpty(request.passhash) || !request.passhash.matches(hexRegex)) {
			r.message = "Invalid password hash received: " + request.passhash + " - Valid pattern is: " + hexRegex;
			return r;
		}
		if(util.str.isEmpty(request.email) || !request.email.matches(emailRegex)) {
			r.message = "Invalid email. Valid pattern is: " + emailRegex;
			return r;
		}
		
		User u = User.load(request.username);
		if(u == null) {
			u = new User();
			u.setUsername(request.username);
			u.setPasshash(request.passhash);
			u.setEmail(request.email);
			u = User.save(u);
			logger.info("Successfully registered new user '" + request.username + "'");
			r.success = true;
			return r;
		} else {
			r.message = "Username is already taken. Please choose another name.";
			return r;
		}
	}

	@WebMethod
	@SOAPBinding(parameterStyle=ParameterStyle.BARE)
	public LoginResponse login(LoginRequest request) {
		LoginResponse r = new LoginResponse();
		User u = User.load(request.username);
		if(u != null && request.username.equals(u.getUsername()) 
				&& request.passhash.equals(u.getPasshash())) {
			r.success = true;
			UserSession s = new UserSession(u);
			s = UserSession.save(s);
			r.sessionID = s.getSessionID();
			r.expiry = "" + s.getExpiryTime();
		} else if(u != null && !request.passhash.equals(u.getPasshash())) {
			logger.info("Invalid username/expected PW/actual PW: " + 
					request.username + "/" + u.getPasshash() + "/" + 
					request.getPasshash());
		}
		return r;
	}


	@XmlRootElement(name="checkAccess", namespace=Configuration.NAMESPACE)
	public static class CheckAccessRequest {
		@XmlElement
		private String username;
		@XmlElement(name="sessionID")
		private String sessionID;
		@XmlElement
		private String operation;

		@XmlTransient
		public String getUsername() {
			return username;
		}
		@XmlTransient
		public String getOperation() {
			return operation;
		}
		@XmlTransient
		public String getSessionID() {
			return sessionID;
		}
	}
	@XmlRootElement(namespace=Configuration.NAMESPACE)
	public static class CheckAccessResponse {
		@XmlElement
		private boolean success;
		@XmlElement
		private String message;

		@XmlTransient
		public boolean isSuccess() {
			return success;
		}
		@XmlTransient
		public String getMessage() {
			return message;
		}
	}


	@WebMethod
	@SOAPBinding(parameterStyle=ParameterStyle.BARE)
	public LoginResponse checkCredentials(CheckAccessRequest req) {
		LoginResponse r = new LoginResponse();
		String error = null;
		try {
			QName opName = QName.valueOf(req.operation);
			error = CredentialsValidation.getErrorForMessage(opName, req.username, req.sessionID);
		} catch (Exception e) {
			logger.warn("Error evaluating credentials: " + req.operation + 
					"/" + req.username + "/" + req.sessionID, e);
			error = "Exception: " + e.getMessage();
		}
		r.setSuccess(error != null);
		r.setMessage(error);
		return r;
	}


	/* FURTHER UTILITY METHODS */
	
	@WebMethod
	public void setOptimization(@WebParam(name="doOptimize") boolean doOptimize,
			@WebParam(name="params") OptimizationParameters params) {
		if(doOptimize) {
			TopologyOptimizerVNS.doRun(params);
		} else {
			TopologyOptimizerVNS.doPause();
		}
	}
	
	@WebMethod
	@WebResult(name="AggregatorPerformanceInfo", targetNamespace=Configuration.NAMESPACE)
	public AggregatorPerformanceInfo getPerformanceInfo(
			@WebParam(name="aggregatorEPR") EndpointReference aggregatorEPR,
			@WebParam(name="doNotDeleteData") Boolean doNotDeleteData,
			@WebParam(name="detailed") Boolean detailed,
			@WebParam(name="forUsername") String forUsername) throws Exception {
		if(!aggregatorEPR.getAddress().endsWith("?wsdl"))
			aggregatorEPR.setAddress(aggregatorEPR.getAddress() + "?wsdl");
		return new AggregationClient(aggregatorEPR).getPerformanceInfo(
				doNotDeleteData, detailed, forUsername);
	}

	@WebMethod(exclude=true)
	public void deploy(String url) throws Exception {
		deploy(url, new Handler[0]);
	}
	@WebMethod(exclude=true)
	public void deploy(String url, Handler<?> ... handlers) throws Exception {
		deploy(this, url, handlers);
		Runnable r = new Runnable() {
			public void run() {
				try {
					Gateway.addGatewayToRegistry(Gateway.this);
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
			}
		};
		GlobalThreadPool.executePeriodically(r, 60*1000, 5*1000);
		Gateway.addGatewayToRegistry(Gateway.this);
	}

	@Override
	protected Runnable getTerminateTask(final TerminateRequest params) {
		return new Runnable() {
			public void run() {
				try {
					final TerminateRequest p = params != null ? params : new TerminateRequest();
					final List<Object> tokens = new LinkedList<Object>();
					if(p.recursive) {
						final List<AggregatorNode> nodes = Registry.getRegistryProxy().getAggregatorNodes();
						for(final AggregatorNode a : nodes) {
							GlobalThreadPool.execute(new Runnable() {
								public void run() {
									try {
										tokens.add(new Object());
										new AggregatorNodeProxy(a).terminate(p);
									} catch (Exception e) { }
								}
							});
						}
						while(tokens.size() < nodes.size()) {
							Thread.sleep(300);
						}
					}
				} catch (Exception e) {
					logger.warn("Unexpected error.", e);
				}
			}
		};
	}

	public static GatewayProxy getGatewayProxy(String registryFeatureName) {
		try {
			List<DataServiceNode> nodes = Registry.getRegistryProxy().getDataServiceNodes(registryFeatureName);
			if(nodes.isEmpty()) {
				throw new RuntimeException("Unable to find gateway for " +
						"registry feature name '" + registryFeatureName + "'");
			}
			if(nodes.size() > 1) {
				logger.info("Found multiple gateway nodes for registry feature name '" + 
						registryFeatureName + "'. Using random node: " + nodes.get(0).getEPR().getAddress());
			}
			return new GatewayProxy(nodes.get(0).getEPR());
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
	public static GatewayProxy getGatewayProxy() {
		try {
			return new GatewayProxy(Registry.getRegistryProxy().getGateway().getEPR());	
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@WebMethod
	public void setStrategy(@WebParam(name="strategyChain") StrategyChain strategyChain) throws GatewayException {
		synchronized (this.strategyChain) {
			try {
				this.strategyChain = StrategyChain.loadFromJaxbObject(strategyChain, this);	
			} catch (Exception e) {
				throw new GatewayException(e);
			}
		}
	}
	
	@WebMethod(exclude=true)
	public StrategyChain getStrategyChain() {
		return strategyChain;
	}

	public static Gateway addGatewayToRegistry(Gateway g) throws Exception {
		return addGatewayToRegistry(g.getEPR().getAddress());
	}
	public static Gateway addGatewayToRegistry(String address) throws Exception {
		// first, check if we already have a main gateway..
		boolean hasMainGateway = false;
		EndpointReference gatewayEPR = null;
		try {
			gatewayEPR = Gateway.getGatewayProxy().getEpr();
		} catch (Exception e) {
			logger.info("Could not find existing gateway, setting new main gateway: " + address);
		}
		if(gatewayEPR != null) {
			if(util.net.isPortOpen(gatewayEPR.getAddress())) {
				logger.debug("Existing gateway found, adding/updating secondary gateway: " + address);
				hasMainGateway = true;
			} else {
				logger.info("Could not contact existing gateway (" + 
						gatewayEPR.getAddress() + "), setting new main gateway: " + address);
			}
		}
		Gateway gateway = new Gateway();
		EndpointReference epr = new EndpointReference(
				"<wsa:EndpointReference xmlns:wsa=\"http://schemas.xmlsoap.org/ws/2004/08/addressing\" " +
				"xmlns:tns=\"" + Configuration.NAMESPACE + "\">" +
					"<wsa:Address>" + address + "</wsa:Address>" +
					"<wsa:ServiceName PortName=\"GatewayPort\">" +
						"tns:GatewayService" +
					"</wsa:ServiceName>" +
				"</wsa:EndpointReference>");
		gateway.setEPR(epr);
		logger.debug("Periodically adding gateway to registry. (Main gateway already exists: " + hasMainGateway + ")");
		if(!hasMainGateway) {
			Registry.getRegistryProxy().setGateway(gateway, false);
		} else {
			Registry.getRegistryProxy().addDataServiceNode(
					Gateway.REGISTRY_FEATURE_GATEWAY, gateway.getEPR());
		}
		return gateway;
	}
	
}
