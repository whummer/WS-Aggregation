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

package at.ac.tuwien.infosys.events.query;

import io.hummer.util.NotImplementedException;
import io.hummer.util.Util;
import io.hummer.util.par.GlobalThreadPool;
import io.hummer.util.perf.MemoryAgent;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.lang.reflect.Field;
import java.util.LinkedList;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.log4j.Logger;
import org.w3c.dom.Element;

import at.ac.tuwien.infosys.aggr.events.query.EventBuffer;
import at.ac.tuwien.infosys.aggr.events.query.EventQuerier;
import at.ac.tuwien.infosys.aggr.events.query.EventStream;
import at.ac.tuwien.infosys.aggr.request.NonConstantInput;
import ch.ethz.mxquery.MXQueryGlobals;
import ch.ethz.mxquery.bindings.WindowBuffer;
import ch.ethz.mxquery.bindings.WindowSequenceIterator;
import ch.ethz.mxquery.contextConfig.CompilerOptions;
import ch.ethz.mxquery.contextConfig.Context;
import ch.ethz.mxquery.datamodel.QName;
import ch.ethz.mxquery.datamodel.Source;
import ch.ethz.mxquery.datamodel.types.Type;
import ch.ethz.mxquery.datamodel.xdm.Token;
import ch.ethz.mxquery.datamodel.xdm.TokenInterface;
import ch.ethz.mxquery.exceptions.MXQueryException;
import ch.ethz.mxquery.exceptions.QueryLocation;
import ch.ethz.mxquery.iterators.GFLWORIterator;
import ch.ethz.mxquery.iterators.LetIterator;
import ch.ethz.mxquery.iterators.forseq.ForseqIterator;
import ch.ethz.mxquery.iterators.forseq.ForseqWindowNaiveIterator;
import ch.ethz.mxquery.model.CurrentBasedIterator;
import ch.ethz.mxquery.model.Iterator;
import ch.ethz.mxquery.model.VariableHolder;
import ch.ethz.mxquery.model.Window;
import ch.ethz.mxquery.model.XDMIterator;
import ch.ethz.mxquery.query.PreparedStatement;
import ch.ethz.mxquery.query.XQCompiler;
import ch.ethz.mxquery.query.impl.CompilerImpl;
import ch.ethz.mxquery.sms.StoreFactory;
import ch.ethz.mxquery.sms.MMimpl.SeqFIFOStore;
import ch.ethz.mxquery.sms.MMimpl.StreamStoreInput;
import ch.ethz.mxquery.sms.MMimpl.TokenBufferStore;
import ch.ethz.mxquery.sms.interfaces.StreamStore;
import ch.ethz.mxquery.update.store.llImpl.LLStoreSet;
import ch.ethz.mxquery.xdmio.XDMInputFactory;
import ch.ethz.mxquery.xdmio.XDMSerializer;
import ch.ethz.mxquery.xdmio.XDMSerializerSettings;
import ch.ethz.mxquery.xdmio.XMLSource;

public class EventQuerierMXQuery implements EventQuerier {

	private static final int MAX_RESULT_BUFFER_SIZE = 5000;
	private static final AtomicInteger ID_COUNTER = new AtomicInteger();
	private static final Logger logger = Util.getLogger(EventQuerierMXQuery.class);
	private static final Element DUMMY_ELEMENT = Util.getInstance().xml.toElementSafe("<foo/>");

	private Context ctx;
	private List<EventQueryListener> listeners = new LinkedList<EventQueryListener>();
	private String query;
	private StreamStore store;
	private EventStream stream;
	private XDMIterator result;
	private Util util = new Util();
	private boolean running = true;
	private WorkerThread worker;
	private AtomicInteger openElements = new AtomicInteger();
	private String originalQuery;
	private Semaphore mutex = new Semaphore(1);
	private NonConstantInput input;
	private final Object lock = new Object();
	private final LinkedBlockingQueue<Element> outQueue = new LinkedBlockingQueue<Element>();

	private final String resultStreamID = "resultStream" + ID_COUNTER.incrementAndGet();

	private class WorkerThread implements Runnable {
		private static final int EVENT_TYPE_START_ELEMENT = Type.START_TAG;
		private static final int EVENT_TYPE_CLOSE_ELEMENT = Type.END_TAG;
		private static final int EVENT_TYPE_END_SEQUENCE = Type.END_SEQUENCE;
		private StreamStore resultStore;
		private StreamStoreInput resultInput;

		public void run() {
			try {
				while(running) {
					try {
						//int receivedResults = 0;

				    	XDMSerializerSettings set = new XDMSerializerSettings();
				    	set.setOutputMethod(XDMSerializerSettings.METHOD_HTML);
						final XDMSerializer ip = new XDMSerializer(set);

						TokenInterface tok = null;
						resultStore = ctx.getStores().createStreamStore(
								StoreFactory.SEQ_FIFO, resultStreamID);
						resultInput = new StreamStoreInput(resultStore);
						openElements.set(0);
						while((tok = result.next()) != null) {

							if(openElements.get() == 0)
								mutex.acquire();

							if(resultInput == null) {
								/* we end up here if the querier has been closed (see close() method) */
								if(!running) {
									return;
								}
							}

							if(tok.getEventType() == EVENT_TYPE_START_ELEMENT) {
								openElements.getAndIncrement();
							} else if(tok.getEventType() == EVENT_TYPE_CLOSE_ELEMENT) {
								openElements.getAndDecrement();
							}
							
							if(tok.getEventType() == EVENT_TYPE_END_SEQUENCE) {
								// throw new RuntimeException("Result stream seems to have ended unexpectedly..");
								logger.warn("!!! Result stream seems to have ended (unexpectedly?)..");
							} else {
								resultInput.bufferNext(tok);
								if(openElements.get() == 0) {
									resultInput.endStream();
									ByteArrayOutputStream bos = new ByteArrayOutputStream();
									XDMIterator wnd = resultStore.getIterator(ctx);
									ip.eventsToXML(new PrintStream(bos), wnd);
									//wnd.destroyWindow();
									wnd.close(true);
									String event = bos.toString();
									if(event != null && !event.trim().isEmpty()) {
										notifyResult(util.xml.toElement(event));
									}
									if(result instanceof GFLWORIterator) {
										for(XDMIterator iter : ((GFLWORIterator)result).getAllSubIters()) {
											
											if(iter instanceof LetIterator) {
												Field f1 = CurrentBasedIterator.class.getDeclaredField("current");
												f1.setAccessible(true);
												WindowSequenceIterator i = (WindowSequenceIterator)f1.get(iter);
												WindowBuffer b = i.getMat();
												Field f2 = TokenBufferStore.class.getDeclaredField("nodeI");
												f2.setAccessible(true);
												int nodeI = (Integer)f2.get(b.getBuffer());
												Field f3 = TokenBufferStore.class.getDeclaredField("nodesDel");
												f3.setAccessible(true);
												int nodesDel = (Integer)f3.get(b.getBuffer());
												logger.info("nodeI / nodesDel: " + nodeI + " / " + nodesDel);
												if((nodeI - nodesDel) > MAX_RESULT_BUFFER_SIZE) {
													logger.warn("Manually deleting " + (nodeI - nodesDel) + " items from buffer!");
													((TokenBufferStore)b.getBuffer()).deleteItems(nodeI - MAX_RESULT_BUFFER_SIZE/10);
												}
												
											} else if(iter instanceof ForseqWindowNaiveIterator) {
												ForseqIterator fsIter = (ForseqIterator)iter;
												Field f1 = ForseqIterator.class.getDeclaredField("seq");
												f1.setAccessible(true);
												Window w = (Window)f1.get(fsIter);
												if(w instanceof WindowSequenceIterator) {
													WindowSequenceIterator i = (WindowSequenceIterator)w;
													WindowBuffer b = i.getMat();
													SeqFIFOStore store = (SeqFIFOStore)b.getBuffer();
													if(store.getBufferNodeCount() > 2) {
														// TODO needed to avoid memory leak?
														//store.forceGC(1);
													}
												}
											}
										}
									}
									ctx.getStores().removeStore(getSourceForName(resultStreamID));
									resultStore = ctx.getStores().createStreamStore(
											StoreFactory.SEQ_FIFO, resultStreamID);
									resultInput = new StreamStoreInput(resultStore);
									ctx.getStores().freeRessources();
	
									mutex.release();
								}
							}
						}
					} catch (Throwable e) {
						if(mutex.availablePermits() <= 0) {
							mutex.release();
						}
						if(e instanceof InterruptedException || e.getCause() instanceof InterruptedException) {
							//logger.info("java.lang.InterruptedException");
						} else if(e instanceof ThreadDeath || e.getCause() instanceof ThreadDeath) {
							//logger.info("java.lang.ThreadDeath");
						} else {
							logger.warn("Exception when executing query: '" + query + "'", e);
							Thread.sleep(2000);
						}
						ctx.getStores().removeStore(getSourceForName(resultStreamID));
						ctx.getStores().freeRessources();
					}
				}
				logger.info("Terminating event querier thread: " + this);
			} catch (Exception e) {
				logger.warn("Unexpected error.", e);
			}
		}
	}
	
	/**
	 * Implementation of the abstract class EventBuffer for MXQuery.
	 */
	public static class EventBufferMXQuery extends EventBuffer {
		private static int DEFAULT_MAX_BUFFER_SIZE = 1000; 
		public final int maxBufferSize;
		private SeqFIFOStore buffer;

		public EventBufferMXQuery(SeqFIFOStore buffer) {
			this(buffer, DEFAULT_MAX_BUFFER_SIZE);
		}
		public EventBufferMXQuery(SeqFIFOStore buffer, int maxBufferSize) {
			this.buffer = buffer;
			this.maxBufferSize = maxBufferSize;
		}
		public long getBufferSizeKB() {
			return MemoryAgent.getSafeDeepSizeOf(buffer);
		}
		public int getBufferedItems() {
			return (int)buffer.getBufferNodeCount();
		}
		public boolean reduceSize(int maxBufferItems) {
			return buffer.reduceBufferSize(maxBufferItems);
		}	
	}

	public EventQuerierMXQuery() {

		/* Add job which takes results from the output queue */
		Runnable jobOUT = new Runnable() {
			public void run() {
				boolean receivedResults = false;
				while(running || !outQueue.isEmpty()) {
					try {
						Element result = outQueue.take();
						if(result != DUMMY_ELEMENT) {
							receivedResults = true;
							synchronized (listeners) {
								for(EventQueryListener l : listeners) {
									l.onResult(EventQuerierMXQuery.this, result);
								}
							}
						}
					} catch (Throwable t) {
						logger.warn("error taking events from result buffer", t);
					}
				}
				if(receivedResults) {
					logger.info("Output thread for querier " + EventQuerierMXQuery.this + " has terminated.");
				}
			}
		};
		GlobalThreadPool.execute(jobOUT);
		
	}
	
	@Override
	public String getOriginalQuery() {
		return originalQuery;
	}

	@Override
	public void initQuery(NonConstantInput input, String query, EventStream eventStream) throws Exception {

		synchronized (lock) {
			if(this.query != null)
				throw new IllegalStateException("Already initialized.");
	
			this.input = input;
			this.originalQuery = query;
			this.stream = eventStream;

			// create new context and add to list
			ctx = new Context();
			eventStream.contexts.add(ctx);
			
			// set a low limit for buffers
			//LLStoreSet stores = (LLStoreSet)ctx.getStores();
			//stores.granularity.set(100);
	
			if(query == null || query.trim().isEmpty())
				query = "declare variable $input external; $input";
			if(!query.contains("declare variable $input external;")) {
				VariableHolder var = ctx.getVariable(new QName("input"));
				if(var == null || !var.isDeclared()) {
					query = "declare variable $input external; " + query;
				}
			}
			this.query = query;
		}
		
		this.store = ((EventBufferMXQuery)stream.buffer).buffer;

		// Compile and run the query
		CompilerOptions co = new CompilerOptions();
		co.setXquery11(true);
		XQCompiler compiler = new CompilerImpl();

		PreparedStatement statement;
		try {
			statement = compiler.compile(ctx, query, co, null, null);
		} catch (Exception e) {
			throw new RuntimeException("Unable to initialize query:\n" + query, e);
		}
		result = statement.evaluate();
		
		
		XDMIterator wnd = store.getIterator(ctx);
		statement.addExternalResource(new QName("input"), wnd);

		worker = new WorkerThread();
		GlobalThreadPool.execute(worker);
	}
	
	
	private void notifyResult(Element event) {
		try {
			outQueue.put(event);
		} catch (InterruptedException e) {
			logger.warn("Unable to put eventing query result to queue for listener notification!", e);
		}
	}

	@Override
	public void addEvent(EventStream stream, Element event) throws Exception {
		synchronized (stream) {
			long buff = stream.getBufferedEvents();
			if(((EventBufferMXQuery)stream.buffer).maxBufferSize > 0 && buff > ((EventBufferMXQuery)stream.buffer).maxBufferSize) {
				logger.info("Permitted stream buffer size exceeded: " + buff + " > " + ((EventBufferMXQuery)stream.buffer).maxBufferSize + ". Attempting clean-up.");
				boolean success = stream.buffer.reduceSize(((EventBufferMXQuery)stream.buffer).maxBufferSize);
				if(!success) {
					throw new RuntimeException(getClass().getSimpleName() +
							": Buffer limit exceeded and unable to delete exsting items from the buffer.");
				}
			}
			stream.put(event);	
		}
	}

	@Override
	public void addListener(EventQueryListener l) throws Exception {
		synchronized (listeners) {
			listeners.add(l);			
		}
	}

	@Override
	@SuppressWarnings("all")
	public void close() throws Exception {
		running = false;
		if(worker != null) {
			outQueue.put(DUMMY_ELEMENT);
			mutex.release();
			mutex.release();
			worker.resultInput = null;
			worker.resultStore = null;
			WorkerThread w = worker;
			worker = null;
			result.close(true);
		}
	}

	@Override
	public EventStream newStream() throws Exception {
		return newStream(-1);
	}
	@Override
	public EventStream newStream(int maxBufferSize) throws Exception {
		Context ctx = new Context();
		
		int defaultBlockSize = MXQueryGlobals.granularity;
		
		if(maxBufferSize > 0 && maxBufferSize < 5) {
			maxBufferSize = 5;
		}

		int blockSize = defaultBlockSize;
		int minNumBocks = 3;
		if(blockSize >= (maxBufferSize / minNumBocks) && maxBufferSize > 0) {
			blockSize = maxBufferSize / minNumBocks; 
								// we do this because of the way how SeqFIFOStore handles
								// buffer sizes/limits. it creates a linked list of buffers
								// with a fixed block size limit, and only starting from
								// the second buffer do we have influence on the size limits.
								// hence, we divide by 2 here..
		}
		if(blockSize > 0 && blockSize < MXQueryGlobals.garbCollThreshold.get()) {
			MXQueryGlobals.garbCollThreshold.set(blockSize);
		}
		LLStoreSet stores = (LLStoreSet)ctx.getStores();
		stores.granularity.set(blockSize);
		SeqFIFOStore buffer = (SeqFIFOStore)stores.createStreamStore(StoreFactory.SEQ_FIFO, 
				"inputStream" + ID_COUNTER.incrementAndGet(), blockSize);
		if(maxBufferSize > 0) {
			buffer.sizeLimit = maxBufferSize;
		}
		EventBufferMXQuery theBuffer = new EventBufferMXQuery(buffer, maxBufferSize);
		final EventStream stream = new EventStream(theBuffer, ctx);
		final StreamStoreInput input = new StreamStoreInput(((EventBufferMXQuery)stream.buffer).buffer);
		
		/* Add job which puts events to the input queue. */
		Runnable jobIN = new Runnable() {
			public void run() {
				boolean receivedItem = false;
				while(stream.isActive.get() || !stream.isEmpty()) {
					try {
						Element event = stream.take();
						receivedItem = true;
						Context ctx = (Context)stream.contexts.get(0);
						XMLSource xmlIt = XDMInputFactory.createXMLInput(ctx,
								new StringReader(util.xml.toString(event)), false, Context.NO_VALIDATION,
								QueryLocation.OUTSIDE_QUERY_LOC);
						
						{
							mutex.acquire();
							if(!stream.isActive.get()) {
								/* we end up in here if the close() method was called on the querier */
								return;
							}
							receivedItem = true;
							synchronized (stream.bufferLock) {
								TokenInterface tok;
								while ((tok = xmlIt.next()) != Token.END_SEQUENCE_TOKEN) {
									input.bufferNext(tok);
								}
								mutex.release();
							}
						}
					} catch (Throwable t) {
						logger.warn("error adding events to buffer", t);
					}
				}
				if(receivedItem) {
					logger.info("Input thread for querier " + EventQuerierMXQuery.this + " has terminated.");
				}
			}
		};
		GlobalThreadPool.execute(jobIN);
		
		return stream;
	}

	public long getNotYetProcessedInputEvents(EventStream s) {
		return stream.getEnqueuedAndNotYetBufferedItems();
	}

	public long getNumberOfBufferedEvents(EventStream s) {
		Context ctx = (Context)s.contexts.get(0);
		LLStoreSet stores = (LLStoreSet)ctx.getStores();
		return stores.getBufferNodeCount();
	}
	
	public String getEventDumpAsString(EventStream stream) throws Exception {
		return (String)getEventDump(stream, -1, true);
	}

	@SuppressWarnings("all")
	public List<Element> getEventDump(final EventStream stream, final int maxItems) throws Exception {
		return (List<Element>)getEventDump(stream, maxItems, false);
	}

	@SuppressWarnings("all")
	private class StreamStoreToXML implements Runnable {
		private int maxItems;
		private boolean asString;
		private Object result;
		private AtomicBoolean running;
		private EventStream stream;
		private String dumpStreamID;
		private StreamStoreInput resultInput;
		public void run() {
			try {
				StreamStore buffer = ((EventBufferMXQuery)stream.buffer).buffer;
				Iterator w = buffer.getIterator(ctx);
				w.close(true);
				dumpStreamID = "dumpStream" + ID_COUNTER.incrementAndGet();
				StreamStore resultStore = ctx.getStores().createStreamStore(
						StoreFactory.SEQ_FIFO, dumpStreamID);
				resultInput = new StreamStoreInput(resultStore);
				XDMSerializerSettings set = new XDMSerializerSettings();
		    	set.setOutputMethod(XDMSerializerSettings.METHOD_HTML);
				final XDMSerializer ip = new XDMSerializer(set);
				TokenInterface tok = null;
				AtomicInteger openElements = new AtomicInteger();
				mutex.acquire();
				if(!running.get()) {
					freeResources();
					return;
				}
				while((tok = w.next()) != null) {
					if(!running.get()) {
						freeResources();
						return;
					}
					resultInput.bufferNext(tok);
					if(tok.getEventType() == 201326592 || tok.getEventType() == 67108906) {
						openElements.getAndIncrement();
					} else if(tok.getEventType() == 34 || tok.getEventType() == 46) {
						openElements.getAndDecrement();
					}
					if(openElements.get() <= 0) {
						resultInput.endStream();
						ByteArrayOutputStream bos = new ByteArrayOutputStream();
						Iterator wnd = resultStore.getIterator(ctx);
						ip.eventsToXML(new PrintStream(bos), wnd);
						//wnd.destroyWindow();
						wnd.close(true);
						String event = bos.toString();
						if(event != null && !event.trim().isEmpty()) {
							if(asString) {
								((StringBuilder)result).append(event);
							} else {
								List<Element> res = ((List<Element>)result);
								res.add(util.xml.toElement(event));
								if(maxItems > 0 && res.size() > maxItems)
									res.remove(0);
							}
						}
						ctx.getStores().removeStore(getSourceForName(dumpStreamID));
						resultStore = ctx.getStores().createStreamStore(
								StoreFactory.SEQ_FIFO, dumpStreamID);
						resultInput = new StreamStoreInput(resultStore);
						ctx.getStores().freeRessources();
					}
				}
				mutex.release();
			} catch (ArrayIndexOutOfBoundsException e) {
				// this happens when the input stream is closed --> swallow
			} catch (ThreadDeath e) {
				logger.info("ThreadDeath.");
			} catch (Throwable e) {
				//logger.info("Unable to create event buffer dump.", e);
			} 
			if(mutex.availablePermits() <= 0)
				mutex.release();
		}
		private void freeResources() {
			try {
				logger.info("returning from stream store serializer...");
				mutex.release();
				resultInput.endStream();
				ctx.getStores().removeStore(getSourceForName(dumpStreamID));
				ctx.getStores().freeRessources();
			} catch (MXQueryException e) {
				e.printStackTrace();
			}
			return;
		}
	};
	
	public EventStream getStream() {
		return stream;
	}

	@SuppressWarnings("all")
	private Object getEventDump(final EventStream stream, final int maxItems, final boolean asString) throws Exception {
		StreamStoreToXML t = new StreamStoreToXML();
		t.maxItems = maxItems;
		t.asString = asString;
		t.stream = stream;
		//t.ctx = (Context)stream.context;
		t.result = asString ? new StringBuilder("<events eventStreamID=\"" + stream.eventStreamID + "\">") : new LinkedList<Element>();
		t.running = new AtomicBoolean(true);
		GlobalThreadPool.execute(t);
		int sizeBefore = 0;
		while(true) {
			Thread.sleep(500);
			int sizeNow = 0;
			if(asString) {
				sizeNow = ((StringBuilder)t.result).length();
			} else {
				sizeNow = ((List<Element>)t.result).size();
			}
			if(sizeNow <= sizeBefore) {
				t.running.set(false);
				if(asString) {
					((StringBuilder)t.result).append("</events>");
					return ((StringBuilder)t.result).toString();
				}
				return t.result;
			}
			sizeBefore = sizeNow;
		}
	}
	
	@SuppressWarnings("all")
	private Source getSourceForName(final String name) {
		return new Source() {
			public String getURI() { return name; };
			public Window getIterator(Context ctx) throws MXQueryException { return null; }
			public Source copySource(Context ctx, Vector nestedPredCtxStack) throws MXQueryException { return null; }
			public int compare(Source store) { return 0; }
			public boolean areOptionsSupported(int requiredOptions) { return false; }
		};
	}
	
	@Override
	public void addEvent(Element event) throws Exception {
		throw new NotImplementedException();
	}
	
	@Override
	public NonConstantInput getInput() {
		return input;
	}
	
	@Override
	public String toString() {
		String s = super.toString();
		return "[Q @" + s.substring(s.indexOf("@") + 1) + "]";
	}

}
