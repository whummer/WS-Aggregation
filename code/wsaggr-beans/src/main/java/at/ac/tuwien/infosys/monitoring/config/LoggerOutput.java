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
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import at.ac.tuwien.infosys.aggr.request.AbstractOutput;
import at.ac.tuwien.infosys.aggr.request.ConstantOutput;

@XmlRootElement(name=LoggerOutput.JAXB_ELEMENT_NAME)
@XmlJavaTypeAdapter(AbstractOutput.Adapter.class)
public class LoggerOutput extends ConstantOutput {

	public static final String JAXB_ELEMENT_NAME = "log";
	
	@XmlAttribute
	public String priority;

	@SuppressWarnings("unchecked")
	@Override
	public <T extends AbstractOutput> T copy() throws Exception {
		LoggerOutput retVal = new LoggerOutput();
		retVal.priority = this.priority;
		return (T) retVal;
	}
	
}
