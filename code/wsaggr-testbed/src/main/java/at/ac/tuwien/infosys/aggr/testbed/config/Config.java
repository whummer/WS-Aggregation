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

import java.io.FileReader;
import java.io.FileWriter;
import java.io.Writer;
import java.util.List;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.log4j.Logger;

import at.ac.tuwien.infosys.util.Util;

/**
 * Representation of the configuration of the testbed. This files is an
 * unmarshalled version of the xml config file.
 * @author Daniel Domberger
 *
 */
@XmlRootElement(name = "TestSuite")
public class Config {
	
	private static final Logger logger = Util.getLogger(Config.class);
	
	@XmlTransient
	public static final String STUFF_FILENAME = "stuff.tar.gz";
	@XmlTransient
	public static final String OS_USER_DEFAULT = "ubuntu";
	@XmlTransient
	public static final String BOOTSTRAP_SCRIPTNAME = "./etc/deploy/bootstrap.sh";
	@XmlTransient
	public static final int requestPort = 1234;
	
	@XmlElement(name = "EC2Config")
	EC2Config ec2config;
	
	@XmlElement(name = "AggregatorConfig")
	private List<AggregatorConfig> aggregatorConfigs;

	@XmlElement(name = "GatewayConfig")
	private GatewayConfig gatewayConfig;
	
	@XmlElement(name = "DataSource")
	private List<DataSourceConfig> dataSourceConfigs;
	
	@XmlElement(name = "TestRun")
	private List<TestRunConfig> testRunConfigs;
	
	public static Config createFromFile(String filename) {
		Config config = null;
		
		try {
			JAXBContext context = JAXBContext.newInstance(Config.class);
			Unmarshaller um = context.createUnmarshaller();
			config = (Config) um.unmarshal(new FileReader(filename));
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		return config;
	}
	
	public void writeToFile(String filename) {
		Writer w = null;
		try {
			JAXBContext context = JAXBContext.newInstance(Config.class);
			Marshaller m = context.createMarshaller();
			m.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
			
			m.marshal(this, System.out);
			
			// Write to file
			w = new FileWriter(filename);
			m.marshal(this, w);
		} catch (Exception e) {
			logger.warn("Unexpected error.", e);
		} finally {
			try {
				w.close();
			} catch (Exception e) {
			}
		}
	}
	
	/**
	 * @return	the Ec2Config
	 */
	public EC2Config getEC2Config() {
		return ec2config;
	}
	
	/**
	 * @return the dataSourceConfigs
	 */
	public List<DataSourceConfig> getDataSourceConfigs() {
		return dataSourceConfigs;
	}

	/**
	 * @return the testRuns
	 */
	public List<TestRunConfig> getTestRunConfigs() {
		return testRunConfigs;
	}
	
	/**
	 * @return the aggregatorConfigs
	 */
	public List<AggregatorConfig> getAggregatorConfigs() {
		return aggregatorConfigs;
	}

	/**
	 * @return the gatewayConfig
	 */
	public GatewayConfig getGatewayConfig() {
		return gatewayConfig;
	}
}
