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
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public class BaselineEdge {
	static Set<BaselineEdge> edges = new HashSet<BaselineEdge>();
	List<Edge> levels = new LinkedList<Edge>();
	BaselineNode from;
	BaselineNode to;
	double pheromone;
	Set<Constraint> satisfiedConstraints = new HashSet<Constraint>();
	public BaselineEdge(BaselineNode from, BaselineNode to) {
		this.from = from;
		this.to = to;
	}
	public static BaselineEdge get(BaselineNode from, BaselineNode to, Set<Constraint> satisfiedConstraints) {
		for(BaselineEdge e : edges) {
			if(e.from.equals(from) && e.to.equals(to) && 
					(e.satisfiedConstraints.equals(satisfiedConstraints))) {
				return e;
			}
		}
		return null;
	}
	public static BaselineEdge getOrCreate(BaselineNode from, BaselineNode to, Set<Constraint> satisfiedConstraints) {
		if(satisfiedConstraints == null)
			satisfiedConstraints = new HashSet<Constraint>();
		BaselineEdge existing = get(from, to, satisfiedConstraints);
		if(existing != null)
			return existing;
		//System.out.println("adding edge " + from.name + "->" + to.name);
		BaselineEdge e = new BaselineEdge(from, to);
		edges.add(e);
		from.outgoing.add(e);
		to.incoming.add(e);
		if(satisfiedConstraints != null) {
			e.satisfiedConstraints.addAll(satisfiedConstraints);
		}
		return e;
	}
	public void remove(boolean recursive) {
		BaselineEdge.edges.remove(this);
		from.outgoing.remove(this);
		to.incoming.remove(this);
		if(recursive) {
			for(Edge e1 : levels) {
				e1.remove(recursive);
			}
			if(to.incoming.isEmpty()) {
				to.remove(recursive);
			}
			levels.clear();
		}
	}
	
}