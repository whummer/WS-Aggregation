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
package at.ac.tuwien.infosys.aggr.testbed.config;

import java.util.LinkedList;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlTransient;

/**
 * @author Daniel Domberger
 *
 */
@XmlAccessorType(XmlAccessType.FIELD)
public class EC2Config {
	@XmlTransient
	public static final String TAG_OWNER = "owner";
	
	@XmlTransient
	public static class State {
		public static final String STOPPED = "stopped";
		// stopping is transitional state into stopped
		public static final String STOPPING = "stopping";
		public static final String TERMINATED = "terminated";
		// shutting-down is transitional state into terminated
		public static final String SHUTTING_DOWN = "shutting-down";
		// pending is transitional state into running
		public static final String PENDING = "pending";
		public static final String RUNNING = "running";
	}
	
	private String privateKeyFile;
	private String regionEndpoint;
	private String ami;
	private String key;
	private String secretKey;
	private String instanceType;
	private String sshUser;
	private String keyName;
	private String owner;
	private int numInstances = 1;
	private LinkedList<String> securityGroups;

	/**
	 * @return the regionEndpoint
	 */
	public String getRegionEndpoint() {
		return regionEndpoint;
	}

	/**
	 * @return the ami
	 */
	public String getAmi() {
		return ami;
	}

	/**
	 * @return the instanceType
	 */
	public String getInstanceType() {
		return instanceType;
	}

	/**
	 * @return the keyName
	 */
	public String getKeyName() {
		return keyName;
	}

	/**
	 * @return the vmOwner
	 */
	public String getOwner() {
		return owner;
	}

	/**
	 * @return the securityGroups
	 */
	public LinkedList<String> getSecurityGroups() {
		return securityGroups;
	}

	/**
	 * @return the privateKeyFile
	 */
	public String getPrivateKeyFile() {
		return privateKeyFile;
	}

	/**
	 * @return the numInstances
	 */
	public int getNumInstances() {
		return numInstances;
	}

	/**
	 * @return the key
	 */
	public String getKey() {
		return key;
	}
	
	/**
	 * @return the secretKey
	 */
	public String getSecretKey() {
		return secretKey;
	}
	
	/**
	 * @return the sshUser
	 */
	public String getSshUser() {
		return sshUser;
	}
}
