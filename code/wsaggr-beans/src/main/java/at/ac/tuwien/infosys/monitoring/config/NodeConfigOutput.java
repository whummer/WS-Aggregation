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

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import at.ac.tuwien.infosys.aggr.request.AbstractOutput;
import at.ac.tuwien.infosys.monitoring.config.EventTypeEndpointReferenceMapping;
import at.ac.tuwien.infosys.ws.EndpointReference;

@XmlRootElement(name=NodeConfigOutput.JAXB_ELEMENT_NAME)
@XmlJavaTypeAdapter(AbstractOutput.Adapter.class)
public class NodeConfigOutput extends NonConstantOutput {

	public static final String JAXB_ELEMENT_NAME = "nodeConfigOut";
	
	@XmlElement(name=EventTypeEndpointReferenceMapping.NAME_ELEMENT)
	private List<EventTypeEndpointReferenceMapping> mappings = new ArrayList<EventTypeEndpointReferenceMapping>();
	
	public List<EndpointReference> getEventTypeRecipients(String name) {
		EventTypeEndpointReferenceMapping retVal = null;
		for (EventTypeEndpointReferenceMapping mapping : mappings) {
			if(mapping.getEventType().equals(name)){
				retVal = mapping;
				break;
			}
		}
		if(retVal == null){
			retVal = new EventTypeEndpointReferenceMapping();
			retVal.setEventType(name);
			mappings.add(retVal);
		}		
		return retVal.getEndPoints();
	}


	@SuppressWarnings("unchecked")
	@Override
	public <T extends AbstractOutput> T copy() throws Exception {
		NodeConfigOutput retVal = new NodeConfigOutput();
		for (EventTypeEndpointReferenceMapping m : mappings) {
			retVal.getEventTypeRecipients(m.getEventType()).addAll(m.getEndPoints());
		}
		return (T)retVal;
	}

}
