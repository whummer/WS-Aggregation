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
package at.ac.tuwien.infosys.aggr.cloudopt.deployment;

import javax.jws.WebMethod;
import javax.jws.WebService;
import javax.jws.soap.SOAPBinding;
import javax.jws.soap.SOAPBinding.ParameterStyle;
import javax.jws.soap.SOAPBinding.Style;
import javax.jws.soap.SOAPBinding.Use;
import javax.xml.bind.annotation.XmlRootElement;

import at.ac.tuwien.infosys.util.Configuration;

/**
 * @author basic
 *
 */
@WebService(targetNamespace=Configuration.NAMESPACE)
public interface IDeploymentService {
	
	@SOAPBinding(style=Style.DOCUMENT, use=Use.LITERAL, parameterStyle=ParameterStyle.BARE)
	@WebMethod(operationName="numberOfInstances")
	void setNumberOfInstances(NumberOfInstances numberOfInstances);
	
	@XmlRootElement(namespace=Configuration.NAMESPACE, name="numberOfInstances")
	public class NumberOfInstances {
		private int numberOfInstances = 0;
		
		public NumberOfInstances(){};

		public int getNumberOfInstances() {
			return numberOfInstances;
		}

		public void setNumberOfInstances(int numberOfInstances) {
			this.numberOfInstances = numberOfInstances;
		}
	}
}
