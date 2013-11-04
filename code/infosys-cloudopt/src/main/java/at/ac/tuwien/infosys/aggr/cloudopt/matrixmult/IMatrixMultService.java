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

package at.ac.tuwien.infosys.aggr.cloudopt.matrixmult;

import javax.jws.WebMethod;
import javax.jws.WebParam;
import javax.jws.WebResult;
import javax.jws.WebService;
import javax.jws.soap.SOAPBinding;
import javax.jws.soap.SOAPBinding.ParameterStyle;
import javax.jws.soap.SOAPBinding.Style;
import javax.jws.soap.SOAPBinding.Use;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import at.ac.tuwien.infosys.aggr.cloudopt.OptimizationGoal;
import at.ac.tuwien.infosys.aggr.cloudopt.ProvidesProperty;
import at.ac.tuwien.infosys.aggr.cloudopt.collaboration.ICollaborativeNode;
import at.ac.tuwien.infosys.aggr.cloudopt.constraints.HardConstraints;
import at.ac.tuwien.infosys.aggr.cloudopt.constraints.SoftConstraints;
import at.ac.tuwien.infosys.util.Configuration;
import at.ac.tuwien.infosys.ws.EndpointReference;

@WebService(targetNamespace=Configuration.NAMESPACE)
@SoftConstraints({"numTasks", ""})
@HardConstraints({"numTasks < 10", "queueLength < 100"})
@OptimizationGoal(expression="1000*cpu + memory", type="min")
public interface IMatrixMultService extends ICollaborativeNode {

	@ProvidesProperty(name="numOfTasks")
	@SOAPBinding(parameterStyle=ParameterStyle.BARE)
	GetNumOfTasksResponse getNumOfTasks(GetNumOfTasks request);

	@ProvidesProperty(name="numOfTasks", isSetter=true)
	void setNumOfTasks(int numOfTasks);

	@ProvidesProperty(name="queueLength")
	long getQueueLength();

	@ProvidesProperty(name="cpu")
	double getCPU();
	
	@SOAPBinding(style=Style.DOCUMENT, use=Use.LITERAL, parameterStyle=ParameterStyle.BARE)
	@WebMethod(operationName="matrixMult")
	MatrixMultResponse matrixMult(@WebParam MatrixMultRequest request);
	
}
