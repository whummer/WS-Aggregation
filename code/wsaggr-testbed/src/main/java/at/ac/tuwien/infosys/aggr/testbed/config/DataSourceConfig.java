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

package at.ac.tuwien.infosys.aggr.testbed.config;

import java.util.LinkedList;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;

/**
 * @author Daniel Domberger
 *
 */
public class DataSourceConfig {
	@XmlAttribute
	public int parallelInstances;
	
	@XmlElement
	public String type;
	
	@XmlElement
	public String contentType;
	
	@XmlElement(name = "feature")
	public String[] features;
	
	/*
	 * classname and operations are exclusive
	 */
	@XmlElement
	public String classname;
	
	@XmlElement
	public String classpath;
	
	@XmlElement(name = "operation")
	public LinkedList<DataSourceOperationConfig> operations;
	
	@XmlElement
	public String url;
	
	public void initExample() {
		parallelInstances = 10;
		type = "REST";
		contentType = "XML";
		url = "http://${hostname}/foo/bar${instanceNo}";
		
		operations = new LinkedList<DataSourceOperationConfig>();
		DataSourceOperationConfig operation = new DataSourceOperationConfig();
		operation.initExample();
		operations.add(operation);
		
		DataSourceOperationConfig operation2 = new DataSourceOperationConfig();
		operation2.initExample2();
		operations.add(operation2);
	}
	
	public void initExample2() {
		type = "SOAP";
		contentType = "JSON";
		classname = "RestService";
		url = "http://${hostname}/foo/bar";
	}
}
