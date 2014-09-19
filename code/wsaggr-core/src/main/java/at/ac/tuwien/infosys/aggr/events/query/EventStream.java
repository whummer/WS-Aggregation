package at.ac.tuwien.infosys.aggr.events.query;

import io.hummer.util.ws.EndpointReference;

import java.io.Serializable;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import org.w3c.dom.Element;

public class EventStream implements Serializable {

	private static final long serialVersionUID = 1L;

	public String eventStreamID;
	public EndpointReference subscriptionManager;
	public final EventBuffer buffer;
	public final List<Object> contexts = new LinkedList<Object>();
	public final Object bufferLock = new Object();
	public final AtomicBoolean isActive = new AtomicBoolean(true);

	private LinkedBlockingQueue<Element> bufferInput = new LinkedBlockingQueue<Element>();

	public Element take() throws InterruptedException {
		return bufferInput.take();
	}

	public void put(Element element) throws InterruptedException {
		// TODO: enforce maxBufferSize
		bufferInput.put(element);
	}

	public long getEnqueuedAndNotYetBufferedItems() {
		return bufferInput.size();
	}

	public boolean isEmpty() {
		return bufferInput.isEmpty();
	}

	public EventStream(EventBuffer buffer, Object context) {
		this.buffer = buffer;
		this.contexts.add(context);
	}

	private final List<EventQuerier> directListeners = new LinkedList<EventQuerier>();
	public void notifyDirectListeners(Element event) throws Exception {
		synchronized (directListeners) {
			for(EventQuerier q : directListeners) {
				q.addEvent(event);
			}
		}
	}
	public synchronized void addDirectListener(EventQuerier q) {
		if(!directListeners.contains(q)) {
			directListeners.add(q);
		}
	}
	public List<EventQuerier> getListenersCopy() {
		return new LinkedList<EventQuerier>();
	}
	public int getBufferedEvents() {
		return buffer.getBufferedItems();
	}
}
