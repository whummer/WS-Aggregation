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

package at.ac.tuwien.infosys.aggr.request;

import java.util.LinkedList;
import java.util.List;

import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import org.w3c.dom.Element;

import at.ac.tuwien.infosys.aggr.xml.XQueryProcessor;

@XmlRootElement(name=NotifyInput.JAXB_ELEMENT_NAME)
@XmlJavaTypeAdapter(AbstractInput.Adapter.class)
public class NotifyInput extends NonConstantInput {

	public static final String JAXB_ELEMENT_NAME = "notify";

	public NotifyInput() { }
	public NotifyInput(NotifyInput toCopy) { 
		copyFrom(toCopy);
	}
	public NotifyInput(NotifyInput toCopy, Object newData) { 
		copyFrom(toCopy);
		setTheContent(newData);
	}
	public NotifyInput(Object data) {
		setTheContent(data);
	}
	@Override
	@SuppressWarnings("all")
	public <T extends AbstractInput> T copy() throws Exception {
		NotifyInput c = new NotifyInput(this);
		return (T)c;
	}
	
	@Override
	public List<AbstractInput> generateInputs() throws Exception {
		List<AbstractInput> result = new LinkedList<AbstractInput>();
		List<Object> l = new LinkedList<Object>();
		Object content = getTheContent();
		if(getContentType() != null && getContentType().equals("waql") && 
				(content instanceof String) && !((String)content).isEmpty()) {
			
			Object o = XQueryProcessor.getInstance().execute((String)content);
			if(o instanceof String) {
				l.add((String)o);
			} if(o instanceof Element) {
				l.add((Element)o);
			} else if(o instanceof List<?>) {
				for(Object s : (List<?>)o) {
					if(s instanceof String) 
						l.add((String)s);
					else
						throw new RuntimeException("Unexpected type: " + o);
				}
			} else {
				throw new RuntimeException("Unexpected type: " + o);
			}
		}
		if(l.isEmpty())
			l.add(content);
		for(Object s : l) {
			result.add(new NotifyInput(this, s));
		}
		return result;
	}
	
}
