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

package at.ac.tuwien.infosys.monitoring.config;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import org.w3c.dom.Element;

import at.ac.tuwien.infosys.aggr.request.AbstractOutput;

@XmlJavaTypeAdapter(AbstractOutput.Adapter.class)
@XmlType(name=NonConstantOutput.JAXB_ELEMENT_NAME)
public abstract class NonConstantOutput extends AbstractOutput {
	
	public static final String JAXB_ELEMENT_NAME = "nonConstantOutput";

	/**
	 * optional; 
	 * used if a particular target service should be used (instead of all services of a feature)
	 */
	@XmlAttribute
	public String serviceURL;
	/**
	 * optional; take care of correct escaping of the newline \n
	 * httpHeaders="Cookie: key1=\"value2\"\nCookie: key2=\"value2\"\n"
	 */
	@XmlAttribute
	public String httpHeaders;

	
	public NonConstantOutput() { }
	public NonConstantOutput(Element request) {
		this.content.add(request);
	}
	
	public void copyFrom(NonConstantOutput toClone) {
		super.copyFrom(toClone);
		this.httpHeaders = toClone.httpHeaders;
		this.serviceURL = toClone.serviceURL;
	}
	

}
