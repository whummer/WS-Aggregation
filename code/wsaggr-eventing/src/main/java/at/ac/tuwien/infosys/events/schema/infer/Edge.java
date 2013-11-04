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

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import at.ac.tuwien.infosys.events.schema.EventCorrelationSet;
import at.ac.tuwien.infosys.events.schema.EventCorrelationSet.EventPropertyValue;

public class Edge {
	BaselineEdge basis;
	Node from;
	Node to;
	public Edge(BaselineEdge basis, Node from, Node to) {
		this.basis = basis;
		this.from = from;
		this.to = to;
		from.outgoing.add(this);
		to.incoming.add(this);
		basis.levels.add(this);
	}
	public static Edge getOrCreate(Node from, Node to, List<EventCorrelationSet> correlationSets) {
		Set<Constraint> satisfiedConstraints = new HashSet<Constraint>();
		for(EventCorrelationSet c : correlationSets) {
			Set<EventPropertyValue> match1 = from.getMatches(c);
			Set<EventPropertyValue> match2 = to.getMatches(c);
			for(EventPropertyValue v1 : match1) {
				for(EventPropertyValue v2 : match2) {
					if(v1.selector.equals(v2.selector) && v1.value.equals(v2.value)) {
						satisfiedConstraints.add(
							new Constraint.EqualityConstraint(v1.selector.xpath, v2.selector.xpath));
					}
				}
			}
		}
		BaselineEdge be = BaselineEdge.getOrCreate(from.basis, to.basis, satisfiedConstraints);
		Edge e = new Edge(be, from, to);
		return e;
	}
	
	public void remove(boolean recursive) {
		from.outgoing.remove(this);
		to.incoming.remove(this);
		basis.levels.remove(this);
		if(recursive && to.incoming.isEmpty()) {
			to.remove(recursive);
		}
	}
}
