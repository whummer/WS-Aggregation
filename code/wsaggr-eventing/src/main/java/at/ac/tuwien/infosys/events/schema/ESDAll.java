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

import javax.xml.bind.annotation.XmlRootElement;

import at.ac.tuwien.infosys.events.schema.ESDObject.ESDObjectWithUnsortedChildren;

@XmlRootElement(name="all")
public class ESDAll extends ESDObjectWithUnsortedChildren {

	public String toQuery(QueryOutputConfig cfg) {
		int id = cfg.getID();
		System.out.println(getElementName() + " id : " + id);
		String s = getTemplate(getElementName());
		s = replaceQueryVariables(s, id);
		String children = "";
		for(Object o : getChildren()) {
			if(o instanceof ESDObject) {
				ESDObject c = (ESDObject)o;
				QueryOutputConfig cfg1 = new QueryOutputConfig(cfg, id);
				String inner = c.toQuery(cfg1);
				children += "(" + inner + "),";
			}
		}
		if(!children.isEmpty()) {
			children = children.substring(0, children.length() - 1);
		}
		if(children.isEmpty()) {
			children = "true()";
		}
		s = s.replace("##self.children##", children);
		s = s.replace("##self.following-siblings##", "()");
		s = s.replace("\n", "\n" + cfg.indentation);
		return s;
	}
	
	protected String getElementName() {
		return "all";
	}
	
}
