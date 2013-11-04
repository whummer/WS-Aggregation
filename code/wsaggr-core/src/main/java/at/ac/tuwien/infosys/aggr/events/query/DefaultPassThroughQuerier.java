package at.ac.tuwien.infosys.aggr.events.query;

import java.util.LinkedList;
import java.util.List;

import org.w3c.dom.Element;

import at.ac.tuwien.infosys.aggr.request.NonConstantInput;
import at.ac.tuwien.infosys.util.NotImplementedException;

public class DefaultPassThroughQuerier implements EventQuerier {

	private List<EventQueryListener> listeners = new LinkedList<EventQueryListener>();
	private NonConstantInput input;
	private String query;
	private EventStream stream;
	
	public void addEvent(EventStream stream, Element event) throws Exception {
		throw new NotImplementedException();
	}
	public void addEvent(Element event) throws Exception {
		//System.out.println("Adding event: " + Util.getInstance().toString(event));
		for(EventQueryListener l : listeners)
			l.onResult(this, event);
	}
	public void addListener(EventQueryListener l) throws Exception {
		listeners.add(l);
	}
	public void close() throws Exception {}
	public List<Element> getEventDump(EventStream stream, int maxItems) throws Exception {
		return new LinkedList<Element>();
	}
	public String getEventDumpAsString(EventStream stream) throws Exception {
		return null;
	}
	public NonConstantInput getInput() {
		return input;
	}
	public String getOriginalQuery() {
		return query;
	}
	public EventStream getStream() {
		return stream;
	}
	public void initQuery(NonConstantInput input, String query, EventStream eventStream) throws Exception {
		this.input = input;
		this.query = query;
		this.stream = eventStream;
	}
	public EventStream newStream() throws Exception {
		throw new NotImplementedException();
	}
	public EventStream newStream(int maxBufferSize) throws Exception {
		throw new NotImplementedException();
	}
	public long getNotYetProcessedInputEvents(EventStream s) {
		return stream.getEnqueuedAndNotYetBufferedItems();
	}
	public long getNumberOfBufferedEvents(EventStream s) {
		return 0;
	}
}