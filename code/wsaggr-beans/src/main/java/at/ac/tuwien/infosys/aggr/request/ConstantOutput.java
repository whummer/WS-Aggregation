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

import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import org.w3c.dom.Element;

@XmlJavaTypeAdapter(AbstractOutput.Adapter.class)
@XmlType(name=ConstantOutput.JAXB_ELEMENT_NAME)
public abstract class ConstantOutput extends AbstractOutput {
	
	public static final String JAXB_ELEMENT_NAME = "constantOutput";

	
	public ConstantOutput() { }
	public ConstantOutput(Element request) {
		this.content.add(request);
	}
	
	public void copyFrom(ConstantOutput toClone) {
		super.copyFrom(toClone);
	}
	

}
