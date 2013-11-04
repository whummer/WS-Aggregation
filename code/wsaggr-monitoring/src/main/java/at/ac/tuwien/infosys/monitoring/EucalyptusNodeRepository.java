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

package at.ac.tuwien.infosys.monitoring;

import java.util.ArrayList;
import java.util.List;

import javax.xml.namespace.QName;

import at.ac.tuwien.infosys.aggr.node.DataServiceNode;
import at.ac.tuwien.infosys.aggr.node.Registry;
import at.ac.tuwien.infosys.monitoring.config.NodeRepositoryNodeConfig;
import at.ac.tuwien.infosys.util.Util;
import at.ac.tuwien.infosys.ws.EndpointReference;

public class EucalyptusNodeRepository implements NodeRepository{

	private static Util UTIL = new Util();
	
	private static String FEATURE = "monitoringNode";
	
	@Override
	public void registerNode(String cluster, NodeRepositoryNodeConfig node) {
		try {
			final EndpointReference e = new EndpointReference(node.getEndTo());
			e.addReferenceParameter(
					UTIL.toElement(String.format("<nodeId>%s</nodeId>", node.getIdentifier())));			
			Registry.getRegistryProxy().addDataServiceNode(FEATURE, e);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Override
	public void unregisterNode(NodeRepositoryNodeConfig node) {
		try {
			Registry.getRegistryProxy().removeDataServiceNode(node.getEndTo());
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	

	@Override
	public List<NodeRepositoryNodeConfig> getNodes(String cluster) {
		List<NodeRepositoryNodeConfig> retVal = new ArrayList<NodeRepositoryNodeConfig>();
		try {
			List<DataServiceNode> dataServiceNodes = Registry.getRegistryProxy().getDataServiceNodes(FEATURE);
			for (DataServiceNode dataServiceNode : dataServiceNodes) {
				NodeRepositoryNodeConfig conf = new NodeRepositoryNodeConfig();
				conf.setEndTo(dataServiceNode.getEPR());
				String id = dataServiceNode.getEPR().getPropOrParamByName(new QName("nodeId")).getTextContent();
				conf.setIdentifier(Long.parseLong(id));
				retVal.add(conf);
			}
		} catch (Exception e) {
			e.printStackTrace();			
		}
		return retVal;
	}

}
