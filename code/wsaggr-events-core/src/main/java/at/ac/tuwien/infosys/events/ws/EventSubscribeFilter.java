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

package at.ac.tuwien.infosys.events.ws;

import java.util.LinkedList;
import java.util.List;

import javax.xml.bind.annotation.XmlAnyElement;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlMixed;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.w3c.dom.Element;
import org.w3c.dom.Text;

import io.hummer.util.xml.XMLUtil;


@XmlRootElement(name = "Filter", namespace = WSEvent.NAMESPACE)
public class EventSubscribeFilter {

	@XmlAttribute(name= "Dialect")
	private String dialect;

	@XmlAnyElement
	@XmlMixed
	private final List<Object> content = new LinkedList<Object>();

	public EventSubscribeFilter(){		
	}
	
	public EventSubscribeFilter(EventSubscribeFilter filter){
		this.dialect = filter.getDialect();
		this.content.addAll(filter.getContent());
	}
	
	@XmlTransient
	public String getDialect() {
		return dialect;
	}
	public void setDialect(String dialect) {
		this.dialect = dialect;
	}

	@XmlTransient
	public List<Object> getContent() {
		return content;
	}
	public void setContent(List<Object> c) {
		this.content.addAll(c);
	}

	@XmlTransient
	public void setTheContent(Object o) {
		content.clear();
		content.add(o);
	}
	
	public Object getTheContent() {
		for(int i = 0; i < content.size(); i ++) {
			Object o = content.get(i);
			if(o instanceof Text) {
				Text t = (Text)o;
				if(t.getTextContent().trim().equals(""))
					content.remove(i--);
			} else if(o instanceof String) {
				if(((String)o).trim().equals(""))
					content.remove(i--);
			}
		}
			
		if(content.size() == 1)
			return content.get(0);
		else if(content.size() == 0)
			return null;
		return content;
	}
	

	@XmlTransient
	public Element getTheContentAsElement() throws Exception {
		Object o = getTheContent();
		if(o instanceof Element)
			return (Element)o;
		if(o instanceof List<?> && ((List<?>)o).size() == 1)
			return (Element)((List<?>)o).get(0);
		if(o instanceof String)
			return XMLUtil.getInstance().toElement((String)o);
		throw new Exception("Cannot convert to Element: " + o);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		/** it seems that content.hashCode() does not work reliably..! */
		/*result = prime * result + ((content == null) ? 0 : content.hashCode());*/
		result = prime * result + ((dialect == null) ? 0 : dialect.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		EventSubscribeFilter other = (EventSubscribeFilter) obj;
		/*if (content == null) {
			if (other.content != null)
				return false;
		} else if (!content.equals(other.content))
			return false;*/
		if (dialect == null) {
			if (other.dialect != null)
				return false;
		} else if (!dialect.equals(other.dialect))
			return false;
		return true;
	}
	
	
}
