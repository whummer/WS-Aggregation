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

package at.ac.tuwien.infosys.events.test;

import java.util.concurrent.atomic.AtomicInteger;

import org.w3c.dom.Element;

import at.ac.tuwien.infosys.aggr.events.query.EventQuerier;
import at.ac.tuwien.infosys.aggr.events.query.EventQuerier.EventQueryListener;
import at.ac.tuwien.infosys.aggr.events.query.EventStream;
import at.ac.tuwien.infosys.events.query.EventQuerierMXQuery;
import at.ac.tuwien.infosys.util.Util;
import at.ac.tuwien.infosys.util.par.Parallelization;

@org.junit.Ignore
public class EventQuerierTest implements EventQueryListener {

	private Util util = new Util();
	private AtomicInteger eventCount = new AtomicInteger();
	private AtomicInteger resultCount = new AtomicInteger();
	private final int maxBufferSize = 1;
	private final int eventIntervalMS = 10;
	private final EventQuerier querier = new EventQuerierMXQuery();
	private EventStream stream = null;

	public void test() throws Exception {
		String query = util.xml.toElement(getClass().getResourceAsStream("query_SmallWindow.xml")).getTextContent();
		query = query.trim().replace("\t", " ").replaceAll("  ", " ");
		System.out.println(query);

		stream = querier.newStream(maxBufferSize);
		querier.initQuery(null, query, stream);
		querier.addListener(this);

		Runnable job = new Runnable() {
			public void run() {
				for(int i = 0; i < 10000; i ++) {
					int value = (int)(Math.random() * 10.0);
					try {
						value = i % 3;
						querier.addEvent(stream, util.xml.toElement("<value>" + value + "</value>"));
						eventCount.incrementAndGet();
						Thread.sleep(eventIntervalMS);
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}
		};
		
		final long t1 = System.currentTimeMillis();
		Parallelization.runMultiple(job, 1, true);
		Runtime.getRuntime().addShutdownHook(new Thread() {
			public void run() {
				printStats(t1);
			}
		});
		printStats(t1);
		
		System.exit(0);
	}
	
	private void printStats(long t1) {
		long t2 = System.currentTimeMillis();
		double diff = t2 - t1;
		System.out.println("Processed " + eventCount + " events in " + 
				(diff/1000.0) + " seconds, " + 
				((double)eventCount.get()/(diff/1000)) + " events/second");
	}

	@Override
	public void onResult(EventQuerier store, Element newResult) {
		resultCount.incrementAndGet();
		System.out.println("result " + resultCount.get() + " from window query: " + util.xml.toString(newResult));
		System.out.println("buffered, not yet processed input events: " + querier.getNotYetProcessedInputEvents(stream));
		System.out.println("number of events in internal window buffers: " + querier.getNumberOfBufferedEvents(stream));
	}

	public static void main(String[] args) throws Exception {
		new EventQuerierTest().test();
	}
}
