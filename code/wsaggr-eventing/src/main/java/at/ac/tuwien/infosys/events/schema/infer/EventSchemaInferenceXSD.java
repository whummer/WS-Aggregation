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

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import org.w3c.dom.Element;

import at.ac.tuwien.infosys.aggr.events.query.EventStream;
import at.ac.tuwien.infosys.events.schema.EventGroupingStrategy;
import at.ac.tuwien.infosys.events.schema.LoggedEvent;
import at.ac.tuwien.infosys.events.schema.SchemaQualityComparator;
import at.ac.tuwien.infosys.events.schema.XmlSchemaInference;
import at.ac.tuwien.infosys.events.schema.XmlSchemaInferenceDotNet;
import at.ac.tuwien.infosys.events.schema.EventGroupingStrategy.EventWindow;
import at.ac.tuwien.infosys.events.schema.XmlSchemaInference.SchemaSet;

public class EventSchemaInferenceXSD 
	extends EventSchemaInference {

	private List<LoggedEvent> events = new ArrayList<LoggedEvent>();

	public EventSchemaInferenceXSD(EventStream stream, SchemaInferenceConfig cfg) {
		super(stream, cfg);
	}

	@Override
	public void addEvent(Element event) throws Exception {
		if(config.considerEventPayload) {
			//infer.addPositiveInstance(event);
			events.add(new LoggedEvent(event, getNextSequenceIndex()));
		} else {
			String s = util.xml.toString(event);
			s = s.substring(0, s.indexOf(">")) + 
					(config.addEventTimestamp ? ("time=\"" + 
					System.currentTimeMillis() + "\"") : "");
			s += s.endsWith("/") ? ">" : "/>";
			System.out.println("new event: " + s);
			Element e = util.xml.toElement(s);
			events.add(new LoggedEvent(e, getNextSequenceIndex()));
			//infer.addPositiveInstance(e);
		}
	}

	public SchemaSet getCurrentlyBestEventSchema() {
		return doInference();
	}

	private SchemaSet doInference() {
		EventGroupingStrategy groupings = new EventGroupingStrategy(events, config);
		SchemaQualityComparator comp = new SchemaQualityComparator();
		SchemaSet bestSchema = null;
		for(List<EventWindow> eventGroup : groupings) {
			XmlSchemaInference infer = new XmlSchemaInferenceDotNet();
			boolean error = false;
			for(EventWindow event : eventGroup) {
				Element group = event.toSingleElement();
				try {
					infer.addPositiveInstance(group);
				} catch (Exception e) {
					error = true;
					logger.warn("Unable to infer schema from event group: " + util.xml.toString(group, true), e);
				}
			}
			if(!error) {
				SchemaSet schema = infer.getCurrentSchema();
				System.out.println("schema: " + schema);
				if(bestSchema == null || comp.compare(schema, bestSchema) < 0) {
					bestSchema = schema;
				}
			}
		}
		return bestSchema;
	}

	private int getNextSequenceIndex() {
		if(events.isEmpty())
			return 0;
		return events.get(events.size() - 1).sequenceIndex + 1;
	}
	
	@Override
	public List<Node> getTwineStarts() {
		return new LinkedList<Node>();
	}
	
}
