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
package at.ac.tuwien.infosys.aggr.test;

import io.hummer.util.Configuration;
import io.hummer.util.ws.EndpointReference;
import io.hummer.util.xml.XMLUtil;

import org.junit.Ignore;
import org.w3c.dom.Element;

import at.ac.tuwien.infosys.aggr.AggregationClient;
import at.ac.tuwien.infosys.aggr.request.WAQLQuery;

@Ignore
public class RemoteGatewayTest {

	public static void main(String[] args) throws Exception {
		
		String address = Configuration.getRegistryAddress();
		//host = "dublin.vitalab.tuwien.ac.at";
		//host = "localhost";
		System.out.println(address);
		EndpointReference epr = new EndpointReference(
				"<wsa:EndpointReference xmlns:wsa=\"http://schemas.xmlsoap.org/ws/2004/08/addressing\" " +
				"xmlns:tns=\"" + Configuration.NAMESPACE + "\">" +
					"<wsa:Address>" + address + "</wsa:Address>" +
					"<wsa:ServiceName PortName=\"GatewayPort\">" +
						"tns:GatewayService" +
					"</wsa:ServiceName>" +
				"</wsa:EndpointReference>");
		
		AggregationClient client = new AggregationClient(epr);
		
		WAQLQuery queries = new WAQLQuery();
		//RequestInputs inputs = new RequestInputs();
		
		queries = new WAQLQuery();
		String request = "<input " +
						"serviceURL=\"http://finance.yahoo.com/q/hp?\" " +
						"type=\"HTTP_GET\">s=$('BIG','BAC')</input>";
		
		queries.setQuery("//td[@class='yfnc_tabledata1']");
		
		Element result = client.aggregate(null, queries, request);
		XMLUtil.getInstance().print(result);
	}
	
}
