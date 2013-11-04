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

package at.ac.tuwien.infosys.aggr.cloudopt.deployment;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import javax.jws.WebService;
import javax.xml.namespace.QName;
import javax.xml.ws.Service;

import org.apache.log4j.Logger;

import at.ac.tuwien.infosys.aggr.cloudopt.collaboration.ICollaborativeNode.MyString;
import at.ac.tuwien.infosys.aggr.cloudopt.collaboration.ICollaborativeNode.NodeURI;
import at.ac.tuwien.infosys.aggr.cloudopt.matrixmult.IMatrixMultService;
import at.ac.tuwien.infosys.aggr.cloudopt.matrixmult.MatrixMultServiceImpl;
import at.ac.tuwien.infosys.util.Configuration;
import at.ac.tuwien.infosys.ws.AbstractNode;
import at.ac.tuwien.infosys.ws.DynamicWSClient;
import at.ac.tuwien.infosys.ws.EndpointReference;

@WebService(targetNamespace=Configuration.NAMESPACE, endpointInterface="at.ac.tuwien.infosys.aggr.cloudopt.deployment.IDeploymentService")
public class DeploymentServiceImpl extends AbstractNode implements IDeploymentService {
	private static Logger logger = Logger.getLogger(DeploymentServiceImpl.class);
	// $n is a place holder for the number of the instance
	private static String matrixMultServiceDomainName = "service$n";

	private int instanceId = 0;
	
	private String domainURL;
	private String deploymentServiceURL;
		
	private List<AbstractNode> nodes = new ArrayList<AbstractNode>();
	private List<IMatrixMultService> collaborativNodes = new ArrayList<IMatrixMultService>();

	/*
	 * initializing deployment service
	 */
	private void initialize() throws Exception{
		domainURL = Configuration.getUrlWithVariableHost("test.cloudopt.matrix.service.address");
		deploymentServiceURL = domainURL + "/" + "deployment-service";
		this.setEPR(new EndpointReference(new URL(deploymentServiceURL)));
		this.deploy(deploymentServiceURL);
		logger.info("Deployment Service can be found at URL: " + getEPR().getAddress());
	}
	
	public DeploymentServiceImpl() {
		// TODO Auto-generated constructor stub
//		logger.info("testing logger");
	}

	@Override
	public void setNumberOfInstances(NumberOfInstances numberOfInstances) {
		logger.debug("setting number of instances to : " + numberOfInstances.getNumberOfInstances());
		if(instanceId >= numberOfInstances.getNumberOfInstances()){
			logger.debug("there are already enough instances.");
		}else{
			int countOfInstancesToCreate = numberOfInstances.getNumberOfInstances() - instanceId;
			logger.debug("creating " + countOfInstancesToCreate + " new instance(s).");
			for (int i = 0; i < countOfInstancesToCreate; i++) {
				createNewInstance();
			}
		}
	}
	
	private void createNewInstance() {
		MatrixMultServiceImpl matrixMultService = new MatrixMultServiceImpl();
		String url = this.getEpr().getAddress() + "/" + DeploymentServiceImpl.matrixMultServiceDomainName.replace("$n", Integer.toString(instanceId + 1));
		logger.debug("new url: " + url);
		try {
			EndpointReference endPointReference = new EndpointReference(new URL(url));
			matrixMultService.setEPR(endPointReference);
			matrixMultService.deploy(url);
			nodes.add(matrixMultService);
			instanceId++;
			informServicesOfNewInstance(matrixMultService.getEPR());
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}
	
	private void informServicesOfNewInstance(EndpointReference endPointReference) {
		for (IMatrixMultService node : collaborativNodes) {
//			NodeURI uri = new NodeURI();
//			uri.setUri(endPointReference.getAddress());
			String wsdl = endPointReference.getAddress();
			if(!wsdl.endsWith("?wsdl"))
				wsdl += "?wsdl";
			Service s;
			try {
				s = Service.create(new URL(wsdl), new QName(""));
				IMatrixMultService node1 = s.getPort(IMatrixMultService.class);
				node1.hello1("foo");
			} catch (MalformedURLException e) {
				e.printStackTrace();
			}
			node.registerNewNode(endPointReference);
//			MyString string = new MyString();
//			string.setString("something");
//			node.hello(string);
//			node.hello("something");
			}
		try {
			logger.debug("client for: " + endPointReference.getAddress());
			IMatrixMultService collaborativeNode = DynamicWSClient.createClient(IMatrixMultService.class, new URL(endPointReference.getAddress() + "?wsdl"));
			collaborativNodes.add(collaborativeNode);
		} catch (MalformedURLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	@Override
	public void terminate(TerminateRequest params) {
		super.terminate(params);
		logger.debug("terminating also " + nodes.size() + " sevice instances.");
		for (AbstractNode node : nodes) {
			node.terminate(null);
		}
	}
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		try {
			DeploymentServiceImpl ds = new DeploymentServiceImpl();
			ds.initialize();
			BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
			logger.debug("Initializing new client for deployment service.");
			IDeploymentService deploymentServiceClient = DynamicWSClient.createClient(IDeploymentService.class, new URL(ds.getEPR().getAddress() + "?wsdl"));
			NumberOfInstances numberOfInstances = new NumberOfInstances();
			numberOfInstances.setNumberOfInstances(2);
			deploymentServiceClient.setNumberOfInstances(numberOfInstances);
			System.out.println("Press ENTER to terminate Deployment Service.");
			reader.readLine();
			ds.terminate(null);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}


}
