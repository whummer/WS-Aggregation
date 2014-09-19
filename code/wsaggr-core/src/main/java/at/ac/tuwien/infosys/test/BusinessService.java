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
package at.ac.tuwien.infosys.test;

import io.hummer.util.xml.XMLUtil;

import javax.jws.WebMethod;
import javax.jws.WebParam;
import javax.jws.WebResult;
import javax.jws.WebService;
import javax.xml.bind.annotation.XmlElement;

import org.w3c.dom.Element;

import at.ac.tuwien.infosys.aggr.node.DataServiceNode;
import at.ac.tuwien.infosys.aggr.util.TestUtil;
import at.ac.tuwien.infosys.ws.SoapService;

@WebService
public class BusinessService extends DataServiceNode implements SoapService {

	private static final long serialVersionUID = -5081603075727754212L;

	private TestUtil TestUtil = new TestUtil();
	private XMLUtil xmlUtil = new XMLUtil();

	public void onDeploy() { }
	public void onUndeploy() { }
	
	@WebMethod
	@WebResult(name="matchingFlights", targetNamespace="http://test.aggr.infosys.tuwien.ac.at/")
	public synchronized Object getFlights(
			@WebParam(name="request") Object request,
			@WebParam(name="resultsCount") int resultsCount) throws Exception {
		//System.out.println("FlightService::getFlights");
		Object result = TestUtil.getDummyResponse_getFlights(resultsCount);
		//System.out.println("FlightService::getFlights returning");
		return result;
	}

	@WebMethod
	@WebResult(name="vote", targetNamespace="http://test.aggr.infosys.tuwien.ac.at/")
	public Object getVote(
			@WebParam(name="request") Object request) {
		//System.out.println("FlightService::getFlights");
		Element result = TestUtil.getDummyResponse_getVote();
		//System.out.println("FlightService::getFlights returning");
		increaseInvocationCount();
		return result;
	}

	
	private static Long numInvocations = 0L;
	private void increaseInvocationCount() {
		synchronized (numInvocations) {
			numInvocations ++;
			if(numInvocations % 1000 == 0)
				System.out.println(this + " number of invocations: " + numInvocations);
		}
	}

	@WebMethod
	@WebResult(name="quotes", targetNamespace="http://test.aggr.infosys.tuwien.ac.at/")
	public Object getQuotes(
			@WebParam(name="symbol") String symbol, 
			@WebParam(name="resultsCount") int resultsCount
			) throws Exception {
		Object result = TestUtil.getDummyResponse_getQuote(symbol, resultsCount);
		increaseInvocationCount();
		return result;
	}

	@WebMethod
	@WebResult(name="pixels", targetNamespace="http://test.aggr.infosys.tuwien.ac.at/")
	public Object render(
			@WebParam(name="source") String source,
			@WebParam(name="start") int start,
			@WebParam(name="end") int end) throws Exception {
		//System.out.println("FlightService::getFlights");
		//System.out.println("Rendering " + start + " - " + end + " from " + source);
		Object result = TestUtil.getDummyResponse_render(start, end);
		//System.out.println("FlightService::getFlights returning");
		return result;
	}

	@WebMethod
	public Object getStocks(@WebParam(name="portfolio") String portfolio, @WebParam(name="count") Integer count) throws Exception {
		if(count == null)
			count = 3;
		String out = "<result>";
		for(int i = 1; i <= count; i ++) {
			out += "<stock>" + portfolio + "_stock" + i + "</stock>";
		}
		out += "</result>";
		return xmlUtil.toElement(out);
	}
	@WebMethod
	public Object getStockInfo(@WebParam(name="stock") String stock) throws Exception {
		return xmlUtil.toElement("<result><stockInfo stock=\"" + stock + "\"><name>My stock '" + stock + "'</name></stockInfo></result>");
	}
	@WebMethod
	public Object getStockPrice(@WebParam(name="stock") String stock) throws Exception {
		return xmlUtil.toElement("<result><stockPrice stock=\"" + stock + "\">123</stockPrice></result>");
	}
	
	// Begin Mashups'10 Scenario
	@WebMethod
	public Object getCountry(@WebParam(name="city") String city) throws Exception {
		return xmlUtil.toElement("<result><country><name>Austria</name><id>AT</id></country></result>");
	}
	@WebMethod
	public Object getVisaInfo(@WebParam(name="country") String country) throws Exception {
		return xmlUtil.toElement("<result><visaInfo c=\"DE\">Foo Bar</visaInfo><visaInfo c=\"AT\">Visa Information...</visaInfo><div id=\"visas\">Here goes the visa Information...</div></result>");
	}
	@WebMethod
	public Object getHotels(@WebParam(name="city") String city) throws Exception {
		return xmlUtil.toElement("<result><hotels>" +
				"<hotel><name>Sacher</name><stars>5</stars></hotel>" +
				"<hotel><name>Hilton</name><stars>5</stars></hotel>" +
				"<hotel><name>Marriott</name><stars>5</stars></hotel>" +
				"<hotel><name>Imperial</name><stars>5</stars></hotel>" +
				"<hotel><name>Central</name><stars>5</stars></hotel>" +
				"<hotel><name>Sacher 2</name><stars>5</stars></hotel>" +
				"<hotel><name>Sacher 3</name><stars>5</stars></hotel>" +
				"</hotels></result>");
	}
	@WebMethod
	public Object getRooms(@WebParam(name="hotel") String hotel,
			@WebParam(name="date") String date) throws Exception {
		return xmlUtil.toElement("<result><rooms hotel=\"" + hotel + "\" date=\"" + date + "\">" +
				"<hotel>" + hotel + "</hotel>" + 
				"<room><beds>2</beds><price>" + (int)(200 + Math.random()*100) + "</price></room>" +
				"<room><beds>3</beds><price>" + (int)(300 + Math.random()*100) + "</price></room>" +
				"<room><beds>4</beds><price>" + (int)(400 + Math.random()*100) + "</price></room>" +
				"</rooms></result>");
	}
	@WebMethod
	public Object getCooords(@WebParam(name="country") String country) throws Exception {
		return xmlUtil.toElement("<table>" +
					"<row><col>Innsbruck</col><col>47N</col><col>11E</col></row>" +
					"<row><col>Salzburg</col><col>47N</col><col>13E</col></row>" +
					"<row><col>Wien</col><col>48N</col><col>16E</col></row>" +
					"<row><col>Vienna</col><col>48N</col><col>16E</col></row>" +
				"</table>");
	}
	public static class Coordinates {
		@XmlElement(name="lat")
		public String lat;
		@XmlElement(name="long")
		public String longitude;
	}
	@WebMethod
	public Object getWheather(@WebParam(name="coords") Coordinates coords) throws Exception {
		return xmlUtil.toElement(
					"<result><wheather temperature=\"23 Celsius\" date=\"--\" humidity=\"81%\"></wheather></result>");
	}
	// End Mashups'10 Scenario
	
}
