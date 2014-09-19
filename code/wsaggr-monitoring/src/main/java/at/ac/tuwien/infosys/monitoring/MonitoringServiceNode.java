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

package at.ac.tuwien.infosys.monitoring;

import io.hummer.util.Util;
import io.hummer.util.ws.AbstractNode;

import java.util.List;

import javax.jws.WebMethod;
import javax.jws.WebService;
import javax.jws.soap.SOAPBinding;
import javax.jws.soap.SOAPBinding.ParameterStyle;
import javax.jws.soap.SOAPBinding.Style;
import javax.jws.soap.SOAPBinding.Use;
import javax.xml.bind.annotation.XmlRootElement;

import org.apache.log4j.Logger;

import at.ac.tuwien.infosys.aggr.request.AbstractInput;
import at.ac.tuwien.infosys.aggr.request.AbstractOutput;
import at.ac.tuwien.infosys.monitoring.config.Constants;
import at.ac.tuwien.infosys.monitoring.config.MonitoringConfig;
import at.ac.tuwien.infosys.monitoring.config.MonitoringConfigSet;

@WebService(targetNamespace = Constants.NAMESPACE)
public class MonitoringServiceNode extends AbstractNode {
	
	private static final Logger LOGGER = Util.getLogger(MonitoringServiceNode.class);
	
	@XmlRootElement(name=StartRequest.NAME_ELEMENT, namespace=Constants.NAMESPACE)
	public static class StartRequest{
		public static final String NAME_ELEMENT = "startNode";		
	}
	
	@XmlRootElement(name=StopRequest.NAME_ELEMENT, namespace=Constants.NAMESPACE)
	public static class StopRequest{
		public static final String NAME_ELEMENT = "stopNode";		
	}
	
	@XmlRootElement(name=DestroyRequest.NAME_ELEMENT, namespace=Constants.NAMESPACE)
	public static class DestroyRequest{
		public static final String NAME_ELEMENT = "destroyNode";		
	}
	
	@XmlRootElement(name=UndeployRequest.NAME_ELEMENT, namespace=Constants.NAMESPACE)
	public static class UndeployRequest{
		public static final String NAME_ELEMENT = "undeployNode";		
	}
	
	@XmlRootElement(name=ShutdownRequest.NAME_ELEMENT, namespace=Constants.NAMESPACE)
	public static class ShutdownRequest{
		public static final String NAME_ELEMENT = "shutdownNode";		
	}
		
	private final MonitoringService service; 
	private final MonitoringEngine engine;
	
	public MonitoringServiceNode(MonitoringService service, MonitoringEngine engine){
		this.service = service;
		this.engine = engine;
	}		
	
	//Service Methods
	
	@WebMethod(operationName = MonitoringConfig.NAME_ELEMENT)
	@SOAPBinding(style = Style.DOCUMENT, use = Use.LITERAL, parameterStyle = ParameterStyle.BARE)
	public void deploy(MonitoringConfig config) throws Exception{
		service.deploy(config);
	}

	@WebMethod(operationName = UndeployRequest.NAME_ELEMENT)
	@SOAPBinding(style = Style.DOCUMENT, use = Use.LITERAL, parameterStyle = ParameterStyle.BARE)
	public void undeploy(UndeployRequest request) throws Exception{
		service.undeploy();
	}
	
	@WebMethod(operationName = ShutdownRequest.NAME_ELEMENT)
	@SOAPBinding(style = Style.DOCUMENT, use = Use.LITERAL, parameterStyle = ParameterStyle.BARE)
	public void shutdown(ShutdownRequest request) throws Exception{
		Runnable r = new Runnable() {
			@Override
			public void run() {
				try {				
					MonitoringServiceNode.this.service.shutdown();
					LOGGER.info(String.format("Node terminated @ '%s'.", getEPR().getAddress()));
				} catch(Exception e){
					LOGGER.error("Error while terminating.", e);
				}				
			}
		};
		Thread t = new Thread(r);
		t.start();
	}
	
	//Engine Methods
	
	@WebMethod(operationName = DeploymentNodeConfig.NAME_ELEMENT)
	@SOAPBinding(style = Style.DOCUMENT, use = Use.LITERAL, parameterStyle = ParameterStyle.BARE)
	public void configure(DeploymentNodeConfig config) throws Exception{
		engine.configure(config);
		LOGGER.info(String.format("Node @ %s configured to use config %s with Inputs=[%s] Sets=[%s], Outputs=[%s].", 
				getEPR().getAddress(), 
				config.getIdentifier(),
				getInputIds(config.getAllInputs()), 
				getMonitoringSetIds(config.getMonitoringSets()), 
				getOutuptIds(config.getAllOutputs())));
	}
	
	@WebMethod(operationName = StartRequest.NAME_ELEMENT)
	@SOAPBinding(style = Style.DOCUMENT, use = Use.LITERAL, parameterStyle = ParameterStyle.BARE)
	public void start(StartRequest obj) throws Exception{
		engine.start();
		LOGGER.info(String.format("Node @ %s is started.", getEPR().getAddress()));
	}
	
	@WebMethod(operationName = StopRequest.NAME_ELEMENT)
	@SOAPBinding(style = Style.DOCUMENT, use = Use.LITERAL, parameterStyle = ParameterStyle.BARE)	
	public void stop(StopRequest obj) throws Exception{
		engine.stop();
		LOGGER.info(String.format("Node @ %s is stoped.", getEPR().getAddress()));
	}
	
	@WebMethod(operationName = DestroyRequest.NAME_ELEMENT)
	@SOAPBinding(style = Style.DOCUMENT, use = Use.LITERAL, parameterStyle = ParameterStyle.BARE)	
	public void destroy(DestroyRequest obj) throws Exception{
		engine.destroy();
		LOGGER.info(String.format("Node @ %s is destroyed.", getEPR().getAddress()));
	}
	
	@WebMethod(exclude=true)
	@Override
	public void deploy(String url) throws Exception{
		super.deploy(url);
		LOGGER.info(String.format("Node started @ '%s'.", url));
	}

	private static String getInputIds(List<AbstractInput> inputs){
		StringBuffer b = new StringBuffer();
		boolean isFirst = true;
		for (AbstractInput i : inputs) {
			if(i.getExternalID() != null){
				if(!isFirst){
					b.append(", ");
				}
				b.append(i.getExternalID());
				isFirst = false;
			}
		}
		return b.toString();
	}
	
	private static String getMonitoringSetIds(List<MonitoringConfigSet> set){
		StringBuffer b = new StringBuffer();
		boolean isFirst = true;
		for (MonitoringConfigSet s : set) {
			if(!isFirst){
				b.append(", ");				
			}
			b.append(s.getIdentifier());
			isFirst = false;
		}
		return b.toString();
	}
	
	private static String getOutuptIds(List<AbstractOutput> outputs){
		StringBuffer b = new StringBuffer();
		boolean isFirst = true;
		for (AbstractOutput o : outputs) {
			if(o.getExternalID() != null){
				if(!isFirst){
					b.append(", ");
				}
				b.append(o.getExternalID());
				isFirst = false;
			}
		}
		return b.toString();
	}
	
}
