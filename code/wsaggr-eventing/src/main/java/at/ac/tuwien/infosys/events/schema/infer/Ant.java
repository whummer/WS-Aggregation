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

package at.ac.tuwien.infosys.events.schema.infer;

import java.util.LinkedList;
import java.util.List;

public class Ant {
	Node currentNode;
	static List<Ant> ants = new LinkedList<Ant>();
	
	public Ant() {}
	public Ant(Node position) {
		this.currentNode = position;
	}
	public void remove() {
		if(currentNode != null) {
			currentNode.ants.remove(this);
		}
		ants.remove(this);
	}
	public String toString() {
		return "[Ant@" + ((currentNode == null) ? null : ("'" + currentNode.basis.name + "'")) + "]";
	}
}