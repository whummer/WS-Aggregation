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

import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

import org.apache.log4j.Logger;

import at.ac.tuwien.infosys.util.Util;
import at.ac.tuwien.infosys.util.coll.MapDelegator;

public class GarbageCollector {

	private static final int CHECK_INTERVAL_SECONDS = 60;
	
	private static final Logger logger = Util.getLogger(GarbageCollector.class);
	
	private static List<GCTask> tasks = new LinkedList<GCTask>();
	private static Timer timer = new Timer();
	//private static List<GCTaskForMaps> mapTasks = new LinkedList<GCTaskForMaps>();
	
	//private static enum RepeatType { TIME_TRIGGERED, COUNTER_TRIGGERED }
	private static abstract class GCTask {
		protected List<WatchItem> items = new LinkedList<WatchItem>();
		public abstract void check();
	}
	private static class GCTaskForListAndSet extends GCTask {
		private Collection<?> listOrSet;
		private void removeObject(WatchItem i) {
			while(listOrSet.remove(i.keyOrValue)); {
				logger.info("Removing item " + i.keyOrValue + " from collection");
			}
		}
		public void check() {
			long now = System.currentTimeMillis();
			for(int i = 0; i < items.size(); i ++) {
				WatchItem item = items.get(i);
				Long time = item.lastAccessTime;
				if(time == null) time = new Long(0);
				long minTime = now - item.maxIdleSeconds;
				if(time < minTime) {
					removeObject(item);
					i--;
				}
			}
		}
	}
	private static class GCTaskForMap extends GCTask {
		private Map<?,?> map;
		private void removeObject(WatchItem i) {
			logger.info("Removing key " + i.keyOrValue + " from map; object: " + map.get(i.keyOrValue));
			map.remove(i.keyOrValue);
			items.remove(i);
		}
		@Override
		public void check() {
			long now = System.currentTimeMillis();
			for(int i = 0; i < items.size(); i ++) {
				WatchItem item = items.get(i);
				Long time = item.lastAccessTime;
				if(time == null) time = new Long(0);
				long minTime = now - item.maxIdleSeconds;
				if(time < minTime) {
					removeObject(item);
					i--;
				}
			}
		}
	}
	
	private static class WatchItem {
		private Object keyOrValue;
		private long lastAccessTime;
		private long maxIdleSeconds;
	}
	
	static {
		timer.scheduleAtFixedRate(new TimerTask() {
			public void run() {
				try {
					synchronized (tasks) {
						for(GCTask task : tasks) {
							task.check();
						}
					}
				} catch (Exception e) {
					logger.warn("Unexpected error.", e);
				}
			}
		}, new Date(), CHECK_INTERVAL_SECONDS * 1000);
	}

//	public static void addTimedTask(GCCallback callback, long intervalSeconds) {
//		GCTask task = new GCTask();
//		task.listener = callback;
//	}
	
	public static class GCAwareMap<K,V> extends MapDelegator<K,V> {
		
		private Set<K> keysToGarbageCollect = new HashSet<K>();
		
		public GCAwareMap(Map<K,V> underlyingMap) {
			super(underlyingMap);
		}
		@Override
		public V get(Object key) {
			if(keysToGarbageCollect.contains(key))
				recordUsage(underlyingMap, key);
			return super.get(key);
		}
		public V put(K key, V value) {
			recordUsage(underlyingMap, key);
			return super.put(key, value);
		}
	}

	public static void deleteAfter(Collection<?> coll, Object item, long timeoutSeconds) {
		deleteWhenUnused(coll, item, timeoutSeconds);
	}
	
	public static void deleteAfter(Map<?,?> map, Object key, long timeoutSeconds) {
		deleteWhenUnused(map, key, timeoutSeconds);
	}
	
	public static <K,V> Map<K,V> deleteWhenUnused(Map<K,V> map, Object key, long maxIdleTimeSeconds) {
		Object value = map.get(key);
		if(value != null) {
			GCTaskForMap task = null;
			synchronized (tasks) {
				for(GCTask t : tasks) {
					if(t instanceof GCTaskForMap) {
						if(((GCTaskForMap)t).map == map)
							task = (GCTaskForMap)t;
					}
				}
				if(task == null)
					task = new GCTaskForMap();
				WatchItem i = new WatchItem();
				i.keyOrValue = key;
				i.lastAccessTime = System.currentTimeMillis();
				i.maxIdleSeconds = maxIdleTimeSeconds;
				task.items.add(i);
			}
		}
		GCAwareMap<K,V> newMap = new GCAwareMap<K,V>(map);
		return newMap;
	}

	public static void deleteWhenUnused(
			Collection<?> coll, Object item, long maxIdleTimeSeconds) {
		if(item != null) {
			GCTaskForListAndSet task = null;
			synchronized (tasks) {
				for(GCTask t : tasks) {
					if(t instanceof GCTaskForListAndSet) {
						if(((GCTaskForListAndSet)t).listOrSet == coll)
							task = (GCTaskForListAndSet)t;
					}
				}
				if(task == null)
					task = new GCTaskForListAndSet();
				WatchItem i = new WatchItem();
				i.keyOrValue = item;
				i.lastAccessTime = System.currentTimeMillis();
				i.maxIdleSeconds = maxIdleTimeSeconds;
				task.items.add(i);
			}
		}
	}
	
	public static void recordUsage(Map<?,?> map, Object key) {
		synchronized (tasks) {
			long time = System.currentTimeMillis();
			for(GCTask t : tasks) {
				if(t instanceof GCTaskForMap) {
					GCTaskForMap tsk = (GCTaskForMap)t;
					if(tsk.map == map) {
						for(WatchItem i : tsk.items) {
							if(i.keyOrValue.equals(key)) {
								i.lastAccessTime = time;
							}
						}
					}
				}
			}
		}
	}
	
}
