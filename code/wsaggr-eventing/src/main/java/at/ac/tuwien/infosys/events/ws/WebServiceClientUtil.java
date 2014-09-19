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

package at.ac.tuwien.infosys.events.ws;

import io.hummer.util.ws.EndpointReference;
import io.hummer.util.ws.WebServiceClient;
import io.hummer.util.ws.request.InvocationResult;
import io.hummer.util.xml.XMLUtil;

import org.w3c.dom.Element;

import at.ac.tuwien.infosys.aggr.request.RequestInput;

public class WebServiceClientUtil {

	private static final XMLUtil xmlUtil = new XMLUtil();
	
	private WebServiceClientUtil(){}
	
	
	public static void execute(EndpointReference epr, WSEvent event) throws Exception{
		RequestInput input = new RequestInput(xmlUtil.toElement(event));
		input.getSoapHeaders().addAll(event.getHeadersCopy());
		final RequestInput theInput = input.copyViaJAXB();
		
		final WebServiceClient client = WebServiceClient.getClient(epr);
		final InvocationResult res = client.invoke(theInput.getRequest());
		res.getResult();
	}
	
	public static <T> T execute(EndpointReference epr, Object request, Class<T> responseType) throws Exception{
		T retVal = null;
		
		final WebServiceClient client = WebServiceClient.getClient(epr);
		final InvocationResult res = client.invoke(new RequestInput(xmlUtil.toElement(request)).getRequest());
		
		if(responseType != null){
			Element result = (Element)res.getResult();
			result = (Element)result.getFirstChild();
			retVal = xmlUtil.toJaxbObject(responseType, result);
		}
		
		return retVal;
		
	}
	
	
}
