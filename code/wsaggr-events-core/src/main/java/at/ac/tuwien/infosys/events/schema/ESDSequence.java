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

import java.util.List;

import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name="sequence")
public class ESDSequence extends ESDObject {

	public String toQuery(QueryOutputConfig cfg) {
		//int id = cfg.idCounter.incrementAndGet();
		//System.out.println(getElementName() + " id : " + cfg.idCounter.get());
		//String s = getTemplate(getElementName());
		String s = toQueryRecursive(cfg, getChildren());
//		s = replaceQueryVariables(s, id);
//		String children = "";
//		for(Object o : getChildren()) {
//			if(o instanceof ESDObject) {
//				ESDObject c = (ESDObject)o;
//				QueryOutputConfig cfg1 = new QueryOutputConfig(cfg, true);
//				children += c.toQuery(cfg1);
//			}
//		}
//		if(children.isEmpty()) {
//			children = "true()";
//		}
//		s = s.replace("##self.children##", children);
		s = s.replace("\n", "\n" + cfg.indentation);
		return s;
	}

	private String toQueryRecursive(QueryOutputConfig cfg, List<ESDObject> children) {
		if(children.isEmpty())
			return "true()";
		ESDObject head = children.remove(0);
		cfg.getID(); /* set ID now to keep hierarchical ID ordering.. */
		QueryOutputConfig cfg1 = new QueryOutputConfig(cfg);
		String part = toQueryRecursive(cfg1, children);
		String q = head.toQuery(cfg).replace("##self.following-siblings##", part);
		q = q.replace("\n", "\n" + cfg.indentation);
		return q;
	}

	protected String getElementName() {
		return "sequence";
	}
	
	@Override
	public boolean isEqualTo(ESDObject obj, boolean considerOccurrences) {
		if(!super.isEqualTo(obj, considerOccurrences))
			return false;
		if(!(obj instanceof ESDSequence))
			return false;
		ESDSequence s = (ESDSequence)obj;
		if(s.getChildren().size() != getChildren().size())
			return false;
		for(int i = 0; i < getChildren().size(); i ++) {
			if(!getChildren().get(i).isEqualTo(s.getChildren().get(i), true)) {
				return false;
			}
		}
		return true;
	}

}
