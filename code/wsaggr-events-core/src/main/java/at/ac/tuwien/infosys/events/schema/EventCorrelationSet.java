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

package at.ac.tuwien.infosys.events.schema;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.apache.log4j.Logger;
import org.w3c.dom.Element;

import at.ac.tuwien.infosys.aggr.xml.XPathProcessor;
import io.hummer.util.Util;

public class EventCorrelationSet {

	private static final Logger logger = Util.getLogger(EventCorrelationSet.class);
	private final List<EventPropertySelector> correlatedProperties = 
		new LinkedList<EventPropertySelector>();
	
	public static class EventPropertySelector {
		public String xpath;
		public String rootElementName;
		public EventCorrelationSet correlationSet;

		public EventPropertySelector(EventCorrelationSet owner, String xpath) {
			this.correlationSet = owner;
			this.xpath = xpath;
		}
		public boolean matches(Element event) {
			Object a = apply(event);
			if(a == null)
				return false;
			if(a instanceof List<?> && ((List<?>)a).isEmpty()) 
				return false;
			return true;
		}
		public Object apply(Element event) {
			try {
				if(rootElementName != null && 
						!event.getLocalName().equalsIgnoreCase(rootElementName)) {
					return null;
				}
				Object o = XPathProcessor.evaluate(xpath, event);
				if(logger.isDebugEnabled()) logger.debug("Applying " + xpath + " to " + event + " - " + o);
				return o;
			} catch (Exception e) {
				logger.warn("Unable to apply event property selector xpath '" + xpath  + "' to event " + event, e);
				return null;
			}
		}
		public static Set<EventPropertySelector> match(
				Set<EventPropertySelector> props, Element element) {
			Set<EventPropertySelector> result = new HashSet<EventPropertySelector>();
			for(EventPropertySelector s : props) {
				if(s.matches(element)) {
					result.add(s);
				}
			}
			return result;
		}
		
	}
	

	public static class EventPropertyValue {
		public EventPropertySelector selector;
		public Object value;
		public EventPropertyValue(EventPropertySelector selector, Object value) {
			this.selector = selector;
			this.value = value;
		}
		
		@Override
		public String toString() {
			return "[V xpath='" + selector.xpath + "',value=" + value + "]";
		}
	}
	
	public static Set<EventPropertySelector> getAllProperties(List<EventCorrelationSet> corr) {
		Set<EventPropertySelector> result = new HashSet<EventPropertySelector>();
		for(EventCorrelationSet c : corr) {
			result.addAll(c.getCorrelatedProperties());
		}
		return result;
	}
	
	public List<EventPropertySelector> getCorrelatedProperties() {
		return correlatedProperties;
	}

}
