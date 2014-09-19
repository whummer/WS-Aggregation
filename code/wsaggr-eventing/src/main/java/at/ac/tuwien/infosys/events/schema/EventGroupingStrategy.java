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

package at.ac.tuwien.infosys.events.schema;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;

import org.w3c.dom.Element;

import at.ac.tuwien.infosys.events.schema.EventCorrelationSet.EventPropertySelector;
import at.ac.tuwien.infosys.events.schema.infer.EventSchemaInference.SchemaInferenceConfig;
import io.hummer.util.NotImplementedException;
import io.hummer.util.Util;
import io.hummer.util.par.GlobalThreadPool;

public class EventGroupingStrategy 
implements 
	Iterator<List<EventGroupingStrategy.EventWindow>>, 
	Iterable<List<EventGroupingStrategy.EventWindow>> {

	private static Util util = new Util();

	private SchemaInferenceConfig config;
	private List<LoggedEvent> events;

	private boolean iteratorFinished = false;
	private LinkedBlockingQueue<List<EventWindow>> iteratorQueue;
	private List<Set<LoggedEvent>> correlatedEvents;

	public static class EventWindow {
		protected List<LoggedEvent> events = new LinkedList<LoggedEvent>();
		public EventWindow(List<LoggedEvent> events) {
			this.events = events;
		}
		public Element toSingleElement() {
			try {
				Element e = util.xml.toElement("<sequence/>");
				for(LoggedEvent ev : events) {
					util.xml.appendChild(e, ev.event);
				}
				return e;
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}
		public int getFirstEventSequenceID() {
			return events.get(0).sequenceIndex;
		}
		public int getLastEventSequenceID() {
			return events.get(events.size() - 1).sequenceIndex;
		}
	}

	public EventGroupingStrategy(List<LoggedEvent> events, SchemaInferenceConfig config) {
		this.config = config;
		this.events = events;
	}

	private LinkedBlockingQueue<List<EventWindow>> iterateAll() {
		final LinkedBlockingQueue<List<EventWindow>> queue = new LinkedBlockingQueue<List<EventWindow>>();
		Runnable job = new Runnable() {
			public void run() {
				iterateAll(new LinkedList<EventWindow>(), queue);
				iteratorFinished = true;
			}
		};
		GlobalThreadPool.execute(job);
		return queue;
	}
	private void iterateAll(List<EventWindow> groupSoFar, LinkedBlockingQueue<List<EventWindow>> results) {

		int lastStart = groupSoFar.isEmpty() ? -1 :
			groupSoFar.get(groupSoFar.size() - 1).getFirstEventSequenceID();
		int lastEnd = groupSoFar.isEmpty() ? -1 :
			groupSoFar.get(groupSoFar.size() - 1).getLastEventSequenceID();

		for(int start = lastStart + 1; start < events.size(); start ++) {
			if(config.allowOverlappingWindows || start >= lastEnd) {
				LoggedEvent eStart = events.get(start);
				if(eStart.event.getLocalName().matches(
						config.namePatternFirstElementInStream)) {
					for(int end = start; end < events.size(); end ++) {
						LoggedEvent eEnd = events.get(end);
						if(eEnd.event.getLocalName().matches(
								config.namePatternLastElementInStream)) {
							int diff = end - start + 1;
							if(config.minEventWindowSize < 1 || diff >= config.minEventWindowSize) {
								if(config.maxEventWindowSize < 1 || diff <= config.maxEventWindowSize) {
									List<EventWindow> nextGroupStep = new LinkedList<EventWindow>(groupSoFar);
									nextGroupStep.add(new EventWindow(events.subList(start, end + 1)));
									iterateAll(nextGroupStep, results);
								}
							}
						}
					}
				}
			}
		}

		try {
			results.put(groupSoFar);
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
	}

	protected int getMaxSequenceIndexOfCorrelatedEvents(LoggedEvent event) {
		List<Set<LoggedEvent>> corr = getCorrelatedEvents(event);
		int index = -1;
		for(Set<LoggedEvent> c : corr) {
			for(LoggedEvent e : c) {
				if(e.sequenceIndex > index) {
					index = e.sequenceIndex;
				}
			}
		}
		return index;
	}

	private List<Set<LoggedEvent>> getCorrelatedEvents(LoggedEvent event) {
		List<Set<LoggedEvent>> result = new LinkedList<Set<LoggedEvent>>();
		for(Set<LoggedEvent> corr : getCorrelatedEvents()) {
			if(corr.contains(event)) {
				result.add(corr);
			}
		}
		return result;
	}

	private List<Set<LoggedEvent>> getCorrelatedEvents() {
		if(correlatedEvents == null) {
			correlatedEvents = new LinkedList<Set<LoggedEvent>>();
			Map<Object,Set<LoggedEvent>> propValueToCorrelatedEvents = 
				new HashMap<Object, Set<LoggedEvent>>();
			for(EventCorrelationSet c : config.correlationSets) {
				for(EventPropertySelector s : c.getCorrelatedProperties()) {
					for(LoggedEvent e : events) {
						Object value = s.apply(e.event);
						System.out.println("Matching event correlation property: " + value);
						if(value != null) {
							if(!propValueToCorrelatedEvents.containsKey(value)) {
								propValueToCorrelatedEvents.put(value, new HashSet<LoggedEvent>());
							}
							propValueToCorrelatedEvents.get(value).add(e);
						}
					}
				}
			}
			correlatedEvents.addAll(propValueToCorrelatedEvents.values());
		}
		return correlatedEvents;
	}
	
	public boolean hasNext() {
		if(iteratorFinished) {
			iteratorQueue = null;
		}
		return !iteratorFinished;
	}

	public List<EventWindow> next() {
		try {
			return iteratorQueue.take();
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
	}
	
	public Iterator<List<EventWindow>> iterator() {
		if(iteratorQueue == null) {
			iteratorQueue = iterateAll();
		}
		iteratorFinished = false;
		return this;
	}
	
	public void remove() {
		throw new NotImplementedException();
	}
}
