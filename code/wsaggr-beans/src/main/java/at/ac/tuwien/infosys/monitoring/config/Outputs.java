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

import io.hummer.util.Util;
import io.hummer.util.xml.XMLUtil;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import javax.xml.bind.annotation.XmlAnyElement;
import javax.xml.bind.annotation.XmlMixed;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.log4j.Logger;

import at.ac.tuwien.infosys.aggr.request.AbstractOutput;

@XmlRootElement(name="outputs")
public class Outputs {
	
	private static final Logger LOGGER = Util.getLogger(Outputs.class);
	
	@XmlAnyElement
	@XmlMixed
	private List<AbstractOutput> output;
	
	public Outputs() { 
		this.output = new LinkedList<AbstractOutput>();
	}
	public Outputs(List<? extends AbstractOutput> outputs) {
		this.output = new LinkedList<AbstractOutput>();
		for(AbstractOutput i : outputs)
			this.output.add(i);
	}
	
	@XmlTransient
	public List<AbstractOutput> getOutputsCopy() {
		if(output != null) {
			for(int i = 0; i < output.size(); i ++) {
				Object o = output.get(i);
				if((o instanceof String) && ((String)o).trim().isEmpty()) {
					LOGGER.debug("Removing empty string from list of outputs...");
					output.remove(i--);
				}
			}
			return Collections.unmodifiableList(output);
		}
		return null;
	}
	
	public boolean addOutput(AbstractOutput i) {		
		return output.add(i);
	}

	public boolean addAllOutputs(List<AbstractOutput> i) {
		return output.addAll(i);
	}

	public void clearOutputs() {
		output.clear();
	}
	
	@Override
	public String toString() {
		try {
			return XMLUtil.getInstance().toString(this);
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}
}
