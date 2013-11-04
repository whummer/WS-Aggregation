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

package at.ac.tuwien.infosys.aggr.account;

import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.XmlValue;

import at.ac.tuwien.infosys.util.Configuration;

@XmlRootElement(name=UsernameSOAPHeader.LOCALNAME, namespace=Configuration.NAMESPACE)
public class UsernameSOAPHeader {

	public static final String LOCALNAME = "username";
	public static final String NAMESPACE = Configuration.NAMESPACE;

	@XmlValue
	private String value;
	
	public UsernameSOAPHeader() {}
	
	public UsernameSOAPHeader(String username) {
		value = username;
	}
	
	@XmlTransient
	public String getValue() {
		return value;
	}
	public void setValue(String value) {
		this.value = value;
	}
	
	@Override
	public String toString() {
		return value;
	}
}
