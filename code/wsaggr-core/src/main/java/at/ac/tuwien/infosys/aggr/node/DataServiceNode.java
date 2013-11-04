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
package at.ac.tuwien.infosys.aggr.node;

import java.io.Serializable;

import javax.xml.bind.annotation.XmlRootElement;

import org.apache.log4j.Logger;

import at.ac.tuwien.infosys.util.Util;
import at.ac.tuwien.infosys.ws.EndpointReference;
import at.ac.tuwien.infosys.ws.AbstractNode;

@XmlRootElement
public class DataServiceNode extends AbstractNode implements Serializable {
	private static final long serialVersionUID = -8357557900210481904L;
	
	private static final Logger logger = Util.getLogger();
	
	public DataServiceNode() { }
	public DataServiceNode(EndpointReference epr) {
		setEpr(epr);
	}
	
	public Runnable getTerminateTask(TerminateRequest request) {
		return new Runnable() {
			@Override
			public void run() {
				try {
					logger.info("Terminating data service node." + (getEPR() != null ? getEPR().getAddress() : ""));
					Registry.getRegistryProxy().removeDataServiceNode(DataServiceNode.this);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		};
	}
}
