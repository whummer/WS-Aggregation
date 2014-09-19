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

import org.apache.log4j.Logger;
import org.w3c.dom.Element;

import at.ac.tuwien.infosys.aggr.events.query.DefaultPassThroughQuerier;
import at.ac.tuwien.infosys.aggr.events.query.EventStream;
import at.ac.tuwien.infosys.events.schema.EventCorrelationSet;
import at.ac.tuwien.infosys.events.schema.EventCorrelationSet.EventPropertySelector;
import at.ac.tuwien.infosys.events.schema.XmlSchemaInference.SchemaSet;
import io.hummer.util.Util;

public abstract class EventSchemaInference 
	extends DefaultPassThroughQuerier {

	protected static Logger logger = Util.getLogger(EventSchemaInference.class);
	
	protected SchemaInferenceConfig config;
	protected Util util = new Util();

	public static class SchemaInferenceConfig {
		public int minEventWindowSize = 2;
		public int maxEventWindowSize = -1;
		public boolean considerEventPayload = false;
		public boolean addEventTimestamp = false;
		public boolean allowOverlappingWindows = true;
		public String namePatternFirstElementInStream = ".*";
		public String namePatternLastElementInStream = ".*";
		public List<EventCorrelationSet> correlationSets = new LinkedList<EventCorrelationSet>();
		public String eventTypeXPath = "local-name(.)";
		public void addCorrelation(String ... propertySelectors) {
			EventCorrelationSet set = new EventCorrelationSet();
			for(String s : propertySelectors) {
				set.getCorrelatedProperties().add(
						new EventPropertySelector(set, s));
			}
			correlationSets.add(set);
		}
	}

	public EventSchemaInference(EventStream stream, SchemaInferenceConfig cfg) {
		stream.addDirectListener(this);
		this.config = cfg;
	}

	@Override
	public abstract void addEvent(Element event) throws Exception;

	public abstract SchemaSet getCurrentlyBestEventSchema();

	public abstract List<Node> getTwineStarts();

}
