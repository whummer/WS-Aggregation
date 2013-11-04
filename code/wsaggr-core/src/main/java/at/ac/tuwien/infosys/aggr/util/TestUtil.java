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
package at.ac.tuwien.infosys.aggr.util;

import java.io.File;
import java.io.Serializable;
import java.net.URL;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import javax.wsdl.Definition;

import org.apache.log4j.Logger;
import org.junit.Ignore;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import at.ac.tuwien.infosys.aggr.AggregationClient;
import at.ac.tuwien.infosys.aggr.node.DataServiceNode;
import at.ac.tuwien.infosys.aggr.node.Registry;
import at.ac.tuwien.infosys.ws.EndpointReference;
import at.ac.tuwien.infosys.ws.WebServiceClient;
import at.ac.tuwien.infosys.ws.IAbstractNode.TerminateRequest;
import at.ac.tuwien.infosys.ws.request.InvocationRequest;
import at.ac.tuwien.infosys.ws.request.RequestType;
import at.ac.tuwien.infosys.aggr.proxy.AggregatorNodeProxy;
import at.ac.tuwien.infosys.aggr.proxy.RegistryProxy;
import at.ac.tuwien.infosys.aggr.strategy.Topology;
import at.ac.tuwien.infosys.util.Util;
import at.ac.tuwien.infosys.util.xml.XMLUtil;

@Ignore
public class TestUtil implements Serializable {
	private static final long serialVersionUID = -8476593833476987984L;

	public static Object lock = new Object();
	
	public static int numServiceResponseSize = 10;
	private static final Logger logger = Util.getLogger(TestUtil.class);

	private static Map<Integer, String> dummyFlightResponseString = new HashMap<Integer, String>();
	private static Map<String,String> dummyQuoteResponseString = new HashMap<String, String>();
	private Element yesVote;
	private Element noVote;
	
	public TestUtil() {
		try {
			Util util = new Util();
			yesVote = util.xml.toElement("<vote>yes</vote>");
			noVote = util.xml.toElement("<vote>no</vote>");
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public static void terminateAllTopologies() throws Exception {
		AggregationClient a = new AggregationClient(Registry.getRegistryProxy().getGateway().getEPR());
		for(Topology t : a.getAllTopologies()) {
			a.destroyTopology(t.getTopologyID());
		}
	}

	public static void restartServiceNodes(String feature) throws Exception {
		long before = System.currentTimeMillis();
		RegistryProxy.resetCache();
		List<DataServiceNode> nodes = Registry.getRegistryProxy().getDataServiceNodes(feature);
		List<DataServiceNode> nodesOriginal = nodes;
		int nodesBefore = nodes.size();
		XMLUtil xmlUtil = new XMLUtil();
		System.out.println("Restarting " + nodesBefore + " service nodes of feature '" + feature + "'...");
		for(DataServiceNode n : nodes) {
			try {
				String wsdl = n.getEPR().getAddress();
				if(!wsdl.endsWith("?wsdl"))
					wsdl = wsdl + "?wsdl";
				String namespace = null;

				try {
					Definition wsdlDef = WebServiceClient.getWsdlDefinition(wsdl);
					namespace = wsdlDef.getTargetNamespace();
				} catch (Exception e) {
					e.printStackTrace();
				}
				
				WebServiceClient c = WebServiceClient.getClient(new EndpointReference(new URL(wsdl)));
				Element request = xmlUtil.toElement(new TerminateRequest());
				request = xmlUtil.changeRootElementName(request, namespace, request.getLocalName());
				c.invoke(new InvocationRequest(RequestType.SOAP11, request));
				
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		Thread.sleep(5000);
		int nodesNow = 0;

		do {
			RegistryProxy.resetCache();
			Thread.sleep(5000);
			try {
				nodes = Registry.getRegistryProxy().getDataServiceNodes(feature);
				nodesNow = nodes.size();
			} catch (Exception e) {
				logger.warn("Unable to get data service nodes from registry.", e);
			}
			Set<DataServiceNode> intersect = new HashSet<DataServiceNode>(nodesOriginal);
			intersect.removeAll(nodes);
			System.out.println("Waiting for " + (nodesBefore - nodesNow) + " nodes to be restarted... " + intersect);
		} while (nodesNow < nodesBefore);
		System.out.println("All service nodes restarted. Took " + (System.currentTimeMillis() - before) + "ms");
	}
	
	public static void restartAggregators() throws Exception {
		long before = System.currentTimeMillis();
		RegistryProxy.resetCache();
		int aggrsBefore = Registry.getRegistryProxy().getAggregatorNodes().size();
		System.out.println("Restarting gateway and " + aggrsBefore + " aggregators...");
		new AggregatorNodeProxy(Registry.getRegistryProxy().getGateway().getEPR()).terminate(new TerminateRequest(true));
		Thread.sleep(15000);
		RegistryProxy.resetCache();
		int aggrsNow = 0;
		boolean gatewayRestarted = false;
		while(!gatewayRestarted) {
			try {
				Registry.getRegistryProxy().getAggregatorNodes().size();
				gatewayRestarted = true;
			} catch (Exception e) {
				Thread.sleep(2000);
			}
		}

		do {
			System.out.println("Waiting for " + (aggrsBefore - aggrsNow) + " aggregators to be restarted...");
			RegistryProxy.resetCache();
			Thread.sleep(5000);
			try {
				aggrsNow = Registry.getRegistryProxy().getAggregatorNodes().size();				
			} catch (Exception e) {
				logger.warn("Unable to get aggregator nodes from registry.", e);
			}
		} while (aggrsNow < aggrsBefore);
		System.out.println("All aggregators restarted. Took " + (System.currentTimeMillis() - before) + "ms");
	}
	
	public Element getDummyResponse_getFlights(int resultsCount) throws Exception {

		XMLUtil xmlUtil = new XMLUtil();
		synchronized (lock) {
			if(!dummyFlightResponseString.containsKey(resultsCount)) {
					
				String ns = "http://test.aggr.infosys.tuwien.ac.at/";
				Document d = xmlUtil.newDocument();
				Element response = d.createElementNS(ns, "matchingFlights");
				response.setPrefix("tns");
				response.setAttribute("xmlns:tns", ns);
				for(int i = 1; i <= resultsCount; i++) {
					Element flight = d.createElementNS(ns, "flight");
					flight.setPrefix("tns");
					Element e = d.createElementNS(ns, "price");
					e.setPrefix("tns");
					e.setTextContent("" + i);
					flight.appendChild(e);
					e = d.createElementNS(ns, "name");
					e.setPrefix("tns");
					e.setTextContent("Flight " + UUID.randomUUID());
					flight.appendChild(e);
					response.appendChild(flight);
				}
			
				dummyFlightResponseString.put(resultsCount, xmlUtil.toString(response));
			}
			return xmlUtil.toElement(dummyFlightResponseString.get(resultsCount));
		}
	}
	
	public Element getDummyResponse_getVote() {
		long time = System.currentTimeMillis();
		boolean vote = time % 2 == 0;
		if(vote)
			return yesVote;
		return noVote;
	}
	
	public Element getDummyResponse_getQuote(String symbol, int numResults) throws Exception {
		Util util = new Util();
		synchronized (lock) {
			if(!Arrays.asList(10, 100, 200).contains(numResults))
				numResults = 100;
			if(dummyQuoteResponseString.get(symbol + numResults) == null) {
				if(symbol == null || symbol.trim().equals(""))
					symbol = "GOOG";
				String content = util.io.readFile(new File("etc/googleHistoricalPrices" + numResults + ".xml").toURI().toURL().toExternalForm());
				content = content.replaceAll("<tickerSymbol>", symbol);
				Element result = util.xml.toElement(content);
				dummyQuoteResponseString.put(symbol + numResults, util.xml.toString(result));
			}	
		}
		Element result = util.xml.toElement(dummyQuoteResponseString.get(symbol + numResults));
		return result;
	}
	
	public Element getDummyResponse_getQuote(long start, long end) throws Exception {
		String ns = "http://test.aggr.infosys.tuwien.ac.at/";
		Document d = XMLUtil.getInstance().newDocument();
		Element response = d.createElementNS(ns, "quotes");
		response.setPrefix("tns");
		response.setAttribute("xmlns:tns", ns);
		for(int i = 1; i <= numServiceResponseSize; i++) {
			Element quote = d.createElementNS(ns, "quote");
			quote.setPrefix("tns");
			quote.setAttribute("time", "" + start);
			quote.setAttribute("value","" + (new Random().nextDouble() * 100));
			response.appendChild(quote);
		}
		return response;
	}
	
	public Element getDummyResponse_render(int start, int end) throws Exception {
		String ns = "http://test.aggr.infosys.tuwien.ac.at/";
		Document d = XMLUtil.getInstance().newDocument();
		Element response = d.createElementNS(ns, "pixels");
		response.setPrefix("tns");
		response.setAttribute("xmlns:tns", ns);
		for(int i = start; i < end; i++) {
			Element pixel = d.createElementNS(ns, "pixel");
			pixel.setPrefix("tns");
			pixel.setAttribute("position", "" + i);
			pixel.setAttribute("color", "" + i); //(int)(new Random().nextDouble() * 65536));
			response.appendChild(pixel);
		}
		return response;
	}
	
	public static String generateListString(String[] in, int maxIndex) {
		String out = "(";
		if(maxIndex < 0)
			maxIndex = in.length;
		int counter = 0;
		for(String i : in) {
			if(counter <= maxIndex) {
				out += "'" + i + "'";
				if(counter < maxIndex)
					out += ",";
			}
			++counter;
		}
		out += ")";
		return out;
	}
}
