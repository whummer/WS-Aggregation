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
import java.util.HashMap;
import java.util.List;

import at.ac.tuwien.infosys.monitoring.config.NodeRepositoryNodeConfig;

public class LocalNodeRepository implements NodeRepository {

	private HashMap<String, List<NodeRepositoryNodeConfig>> repo = new HashMap<String, List<NodeRepositoryNodeConfig>>();
	
	@Override
	public void registerNode(String cluster, NodeRepositoryNodeConfig node) {
		if(repo.containsKey(cluster) == false){
			repo.put(cluster, new ArrayList<NodeRepositoryNodeConfig>());
		}
		repo.get(cluster).add(node);
	}

	@Override
	public void unregisterNode(NodeRepositoryNodeConfig node) {
		for (String cluster : repo.keySet()) {
			for (NodeRepositoryNodeConfig nodeConfig : repo.get(cluster)) {
				if(nodeConfig.getIdentifier() == node.getIdentifier()){
					repo.get(cluster).remove(nodeConfig);
					break;
				}
			}
		}
	}
		
	@Override
	public List<NodeRepositoryNodeConfig> getNodes(String cluster) {
		return repo.get(cluster);
	}

}
