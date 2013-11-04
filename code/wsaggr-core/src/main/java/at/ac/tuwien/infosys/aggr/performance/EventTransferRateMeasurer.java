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

package at.ac.tuwien.infosys.aggr.performance;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.w3c.dom.Element;

import at.ac.tuwien.infosys.util.Util;
import at.ac.tuwien.infosys.util.xml.ElementWrapper;

public class EventTransferRateMeasurer<T> {
	
	private static final long MAX_STORE_DURATION_MS = 1000*60*3; // time to store measurements
	
	// identifier --> timestamp --> size of received event data
	private final Map<T,List<EventSize>> timestampToEventDataSize = new Hashtable<T, List<EventSize>>();
	private Util util = new Util();
	
	private static class EventSize {
		long timestamp;
		int bytes;
	}

	public void addTransmittedData(T identifier, Element data) {
		if(data == null) return;
		addTransmittedData(identifier, util.xml.toString(data));
	}
	public void addTransmittedData(T identifier, ElementWrapper data) {
		if(data == null) return;
		addTransmittedData(identifier, data.getSize());
	}
	public void addTransmittedData(T identifier, String data) {
		if(data == null) return;
		addTransmittedData(identifier, data.getBytes().length);
	}
	public void addTransmittedData(T identifier, int dataSize) {
		try {
			if(identifier == null)
				return;
			synchronized (timestampToEventDataSize) {
				if(!timestampToEventDataSize.containsKey(identifier))
					timestampToEventDataSize.put(identifier, new LinkedList<EventSize>());
				EventSize size = new EventSize();
				size.timestamp = System.currentTimeMillis();
				size.bytes = dataSize;
				List<EventSize> list = timestampToEventDataSize.get(identifier);
				list.add(size);
				removeOutdatedValues(list);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void removeOutdatedValues(List<EventSize> list) {
		synchronized(list) {
			if(list.isEmpty())
				return;
			long firstTime = list.get(0).timestamp;
			long now = System.currentTimeMillis();
			while(now > (firstTime + MAX_STORE_DURATION_MS)) {
				list.remove(0);
				if(list.isEmpty())
					break;
				firstTime = list.get(0).timestamp;
			}
		}
	}

	public void removeStream(String identifier) {
		synchronized (timestampToEventDataSize) {
			timestampToEventDataSize.remove(identifier);
		}
	}
	
	public Map<T,Double> getKBytesPerSecondAsMap(int timeframeSec) {
		Map<T,Double> result = new HashMap<T, Double>();
		synchronized (timestampToEventDataSize) {
			long now = System.currentTimeMillis();
			for(T identifier : new HashSet<T>(timestampToEventDataSize.keySet())) {
				List<EventSize> sizes = timestampToEventDataSize.get(identifier);
				double total = 0;
				for(EventSize size : sizes) {
					if(size.timestamp > (now - (timeframeSec*1000)))
						total += (double)size.bytes / 1000.0;
				}
				result.put(identifier, total);
				removeOutdatedValues(sizes);
			}
		}
		return result;
	}
	
	public double getKBytesPerSecond(int timeframeSec) {
		double result = 0;
		synchronized (timestampToEventDataSize) {
			long now = System.currentTimeMillis();
			for(List<EventSize> sizes : timestampToEventDataSize.values()) {
				for(EventSize size : sizes) {
					if(size.timestamp > (now - (timeframeSec*1000)))
						result += (double)size.bytes / 1000.0;
				}
			}
		}
		return result / (double)timeframeSec;
	}

	public Map<T,Double> getEventsPerMinuteAsMap() {
		Map<T,Double> result = new HashMap<T, Double>();
		synchronized (timestampToEventDataSize) {
			long now = System.currentTimeMillis();
			for(T identifier : new HashSet<T>(timestampToEventDataSize.keySet())) {
				List<EventSize> sizes = timestampToEventDataSize.get(identifier);
				double total = 0;
				for(EventSize size : sizes) {
					if(size.timestamp > (now - (60*1000))) // get events from last 60 seconds...
						total ++;
				}
				result.put(identifier, total);
				removeOutdatedValues(sizes);
			}
		}
		return result;
	}

	public void cleanup(T identifier) {
		timestampToEventDataSize.remove(identifier);
	}
}
