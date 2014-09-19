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

/**
 * 
 */
package at.ac.tuwien.infosys.aggr.cloudopt.collaboration;

import io.hummer.util.Configuration;

import javax.jws.WebMethod;
import javax.jws.WebService;
import javax.jws.soap.SOAPBinding;
import javax.jws.soap.SOAPBinding.ParameterStyle;
import javax.jws.soap.SOAPBinding.Style;
import javax.jws.soap.SOAPBinding.Use;
import javax.xml.bind.annotation.XmlRootElement;

import at.ac.tuwien.infosys.ws.EndpointReference;

/**
 * @author basic
 *
 */
@WebService(targetNamespace=Configuration.NAMESPACE)
public interface ICollaborativeNode {

	@SOAPBinding(style=Style.DOCUMENT, use=Use.LITERAL, parameterStyle=ParameterStyle.BARE)
	@WebMethod(operationName="endpointReference")
	void registerNewNode(EndpointReference nodeEndpointReference);

	@XmlRootElement(namespace=Configuration.NAMESPACE)
	public static class RegisterNodeRequest {
		
	}
	
//	@SOAPBinding(style=Style.DOCUMENT, use=Use.LITERAL, parameterStyle=ParameterStyle.BARE)
//	@WebMethod(operationName="registerNewNode")
//	void registerNewNode(NodeURI uri);
	
	@SOAPBinding(style=Style.DOCUMENT, use=Use.LITERAL, parameterStyle=ParameterStyle.BARE)
	@WebMethod(operationName="hello")
	void hello(MyString hallo);
	@SOAPBinding(style=Style.DOCUMENT, use=Use.LITERAL, parameterStyle=ParameterStyle.BARE)
	@WebMethod(operationName="hello")
	void hello1(String hallo);
//	
	
	/**
	 * VERY IMPORTANT. IF YOU GET EXCEPTION 
	 * javax.xml.ws.soap.SOAPFaultException: 
	 * Cannot find dispatch method for {http://infosys.tuwien.ac.at/WS-Aggregation}myString
	 * THE name OF @XmlRootElement MUST BE EQUAL TO THE NAME OF THE METHOD
	 * @author basic
	 *
	 */
	@XmlRootElement(namespace=Configuration.NAMESPACE, name="hello")
	public class MyString {
		public MyString(){}

		private String string = "empty";

		public String getString() {
			return string;
		}
		public void setString(String string) {
			this.string = string;
		}
	}
	
	@XmlRootElement(namespace=Configuration.NAMESPACE, name = "registerNewNode")
	public class NodeURI {
		
		private String uri;

		public String getUri() {
			return uri;
		}

		public void setUri(String uri) {
			this.uri = uri;
		}
		
	}
}
