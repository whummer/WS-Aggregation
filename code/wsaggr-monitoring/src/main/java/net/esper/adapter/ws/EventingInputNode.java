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

package net.esper.adapter.ws;

import javax.jws.WebMethod;
import javax.jws.WebParam;
import javax.jws.WebResult;
import javax.jws.WebService;
import javax.jws.soap.SOAPBinding;
import javax.jws.soap.SOAPBinding.ParameterStyle;
import javax.jws.soap.SOAPBinding.Style;
import javax.jws.soap.SOAPBinding.Use;

import at.ac.tuwien.infosys.events.ws.WSEvent;
import at.ac.tuwien.infosys.ws.AbstractNode;

@WebService(targetNamespace = WSEvent.NAMESPACE)
public class EventingInputNode extends AbstractNode {

	private AbstractInputWSAdapter<?> adapter;
	
	public EventingInputNode(AbstractInputWSAdapter<?> adapter){
		this.adapter = adapter;		
	}
	
	
	@WebMethod(operationName = "event")
	@WebResult(name = "result")
	@SOAPBinding(style = Style.DOCUMENT, use = Use.LITERAL, parameterStyle = ParameterStyle.BARE)
	public void processEvent(@WebParam WSEvent soapEvent) throws Exception {
		adapter.processEvent(soapEvent);
	}


	public void start() throws Exception {
		deploy(getEPR().getAddress());		
	}


	public void destroy() throws Exception {
		// TODO correct Termination
		final TerminateRequest params = new TerminateRequest();
		Runnable r = getTerminateTask(params);
		if (r != null) {
			r.run();
		}
	}	
}
