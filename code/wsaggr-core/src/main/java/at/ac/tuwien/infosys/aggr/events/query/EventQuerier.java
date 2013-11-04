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

package at.ac.tuwien.infosys.aggr.events.query;

import java.util.List;

import org.w3c.dom.Element;

import at.ac.tuwien.infosys.aggr.events.query.EventStream;
import at.ac.tuwien.infosys.aggr.request.NonConstantInput;

/**
 * This interface defines the capabilities of an event-based querier,
 * i.e., a component which performs continuous queries over event streams.
 * See also {@link EventStream} and {@link EventBuffer}.
 * 
 * @author Waldemar Hummer
 */
public interface EventQuerier {

	public static interface EventQueryListener {
		void onResult(EventQuerier store, Element newResult);
	}
	
	void initQuery(NonConstantInput input, String query, EventStream eventStream) throws Exception;

	String getOriginalQuery();

	void addEvent(EventStream stream, Element event) throws Exception;

	void addEvent(Element event) throws Exception;

	void addListener(EventQueryListener l) throws Exception;
	
	void close() throws Exception;

	EventStream getStream();
	
	EventStream newStream() throws Exception;

	EventStream newStream(int maxBufferSize) throws Exception;

	/** 
	 * Returns the number of events stored in internal buffers, mostly
	 * because of active windows in window queries.
	 * */
	long getNumberOfBufferedEvents(EventStream s);
	
	/** 
	 * Returns the number of input events that are still buffered and 
	 * have not yet been processed by the querier.
	 * */
	long getNotYetProcessedInputEvents(EventStream s);
	
	List<Element> getEventDump(EventStream stream, int maxItems) throws Exception;
	
	String getEventDumpAsString(EventStream stream) throws Exception;

	NonConstantInput getInput();

}
