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

import io.hummer.util.ws.EndpointReference;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import at.ac.tuwien.infosys.aggr.request.AbstractOutput;

@XmlRootElement(name=SOAPEventOutput.JAXB_ELEMENT_NAME)
@XmlJavaTypeAdapter(AbstractOutput.Adapter.class)
public class SOAPEventOutput extends NonConstantOutput {
	
	public static final String JAXB_ELEMENT_NAME = "send";

	@XmlAttribute
	public String serviceURL;
	
	@XmlElement
	public EndpointReference endTo; 		
	
	/* TODO: whu: hashCode() and equals() not implemented (super... is returned). Is that on purpose? */
	
	@Override
	public int hashCode() {
//		int hc = 0;
//		if(endTo != null) hc += endTo.hashCode();
//		if(serviceURL != null) hc += serviceURL.hashCode();		
		return super.hashCode();
	}
	
	@Override
	public boolean equals(Object o) {
//		boolean eq = true;
//		if(!(o instanceof EventingInput))
//			return false;
//		EventingInput e = (EventingInput)o;
//		try {
//			if(e.getEndTo() != null) eq &= e.getEndTo().equals(endTo);
//		} catch (Exception e1) { }
//		if(e.getServiceURL() != null) eq &= e.getServiceURL().equals(serviceURL);
		return super.equals(o);
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T extends AbstractOutput> T copy() throws Exception {
		SOAPEventOutput retVal = new SOAPEventOutput();
		retVal.serviceURL = this.serviceURL;		
		return (T) retVal;
	}
	
}
