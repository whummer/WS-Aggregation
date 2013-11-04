/*   Copyright 2006 - 2009 ETH Zurich 
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */
package ch.ethz.mxquery.bindings;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicInteger;

import ch.ethz.mxquery.MXQueryGlobals;
import ch.ethz.mxquery.contextConfig.Context;
import ch.ethz.mxquery.datamodel.MXQueryNumber;
import ch.ethz.mxquery.datamodel.Source;
import ch.ethz.mxquery.datamodel.xdm.TokenInterface;
import ch.ethz.mxquery.exceptions.MXQueryException;
import ch.ethz.mxquery.iterators.VariableIterator;
import ch.ethz.mxquery.model.DataflowAnalysis;
import ch.ethz.mxquery.model.Iterator;
import ch.ethz.mxquery.model.VariableHolder;
import ch.ethz.mxquery.model.WindowVariable;
import ch.ethz.mxquery.model.Window;
import ch.ethz.mxquery.model.XDMIterator;
import ch.ethz.mxquery.sms.MMimpl.FIFOStore;
import ch.ethz.mxquery.sms.MMimpl.MXQueryAppendUpdate;
import ch.ethz.mxquery.sms.MMimpl.MaterializingTokenBufferStore;
import ch.ethz.mxquery.sms.ftstore.FTTokenBufferStore;
import ch.ethz.mxquery.sms.interfaces.ActiveStore;
import ch.ethz.mxquery.sms.interfaces.StreamStore;
import ch.ethz.mxquery.util.IntegerList;

/**
 * Based on a TokenBuffer this class is a factory for window iterators. Each
 * window iterator which uses the same WindowBuffer operates on the same
 * materialized stream. Additionally this class provides some access
 * functionalities for window iterators.
 * 
 * <br>
 * Important definitions:<br>
 * item: Is a item in a sequence <br>
 * position: Position as in XQuery. First position is 1. <br>
 * nodeId: Like Position but starting with 0 <br>
 * tokenId: Id of each token. Also starting with 0 <br>
 * <br>
 * 
 * @author Tim Kraska (<a href="mailto:mail@tim-kraska.de">mail@tim-kraska.de</a>)
 * 
 */
@SuppressWarnings("all")
public final class WindowBuffer implements Source {

	/** modified by hummer@dsg.infosys.tuwien.ac.at */
//	private int garbCollTreshold = 10000;
	private AtomicInteger garbCollTreshold = MXQueryGlobals.garbCollThreshold;

	private Map winds = new HashMap();

	private final WindowIterator emptySequence = new WindowEmptySequenceIterator(this, -1);

	//ch.ethz.mxquery.model.Store tokenBuffer;

	ch.ethz.mxquery.sms.MMimpl.MXQueryAppendUpdate tokenBuffer;
	
	private int windowId = 0;
	private int lastMemorizedWindowId = 0;

	private static int runningBufferId = 1;
	
	private int bufferId;

	private int garbTime = 0;

	private Object lock = new Object();

	private boolean debug = false;
	
	private XDMIterator source;
	
	private boolean sync = false;
	
	
	//private LinkedHashMap debugWinds;

	public int getBufferId(){
		return this.bufferId;
	}
	
	public StreamStore getBuffer(){
		return tokenBuffer;
	}

	public WindowBuffer(StreamStore streamStore) {
		tokenBuffer = (MXQueryAppendUpdate)streamStore;
		tokenBuffer.setContainer(this);
		
	}
	
	public WindowBuffer(XDMIterator sourceIter, boolean sync, int size, int garbColl, boolean sameSchema) throws MXQueryException{		
		if (debug){
			//this.debugWinds = new LinkedHashMap();
		}

		/* modified by hummer@dsg.tuwien.ac.at */
		//garbCollTreshold = garbColl;
		if(garbCollTreshold.get() > garbColl)
			garbCollTreshold.set(garbColl);
		
		source = sourceIter;
		
		this.bufferId = runningBufferId;
		runningBufferId++;
		this.sync = sync;
		
		if (sync) {
			System.out.println("Create block buffer store");
			tokenBuffer = (FIFOStore)ch.ethz.mxquery.sms.StoreFactory.createStore(ch.ethz.mxquery.sms.StoreFactory.RANDOM_FIFO, size,this);
			tokenBuffer.setIterator(sourceIter);
			((ActiveStore)tokenBuffer).start();
		} else{
			tokenBuffer = (FIFOStore)ch.ethz.mxquery.sms.StoreFactory.createStore(ch.ethz.mxquery.sms.StoreFactory.TOKEN_BUFFER, size,this);
			tokenBuffer.setIterator(sourceIter);
			tokenBuffer.setScoring(sourceIter.isScoring());
		}
	
	}
	
	public WindowBuffer(XDMIterator sourceIter, String language , String uri) throws MXQueryException {
		
		this.bufferId = runningBufferId;
		runningBufferId++;
		source = sourceIter;
		
		tokenBuffer = (FIFOStore)ch.ethz.mxquery.sms.StoreFactory.createFTStore(this, language);
		tokenBuffer.setIterator(sourceIter);
		((FTTokenBufferStore)tokenBuffer).setUri(uri);

	}


	/**
	 * Returns a emptySequence iterator. Especially useful if you ask for a non
	 * existing node
	 * 
	 * @return a WindowIterator representing an empty Sequence
	 */
	public WindowIterator getEmptySequence() {
		return emptySequence;
	}

	/**
	 * Returns a new window iterator for the given range
	 * 
	 * @param startPosition
	 * @param endPosition
	 * @return a WindowIterator for the specified range
	 */
	public WindowIterator getNewWindowIterator(int startPosition, int endPosition) {
		return getNewWindowIteratorWithNodeIds(startPosition - 1, endPosition - 1);
	}

	/**
	 * Return a new Window for the given positions  
	 * @param positions
	 * @return A WindowIterator representing the given positions
	 */
	public WindowIterator getNewWindowIterator(IntegerList positions) {
		WindowIterator window = null;

		synchronized (lock) {
			window = new WindowItemIterator(this, windowId, positions);
			winds.put(new Integer(windowId), window);
			if(debug){
				//debugWinds.put(new Integer(windowId), window);				
			}
			
			windowId++;
		}
		return window;
	}
	
	public WindowEarlyBinding getNewEarlyWindowInterface(int startPosition, XDMIterator startExpr, WindowVariable[] startVars, VariableHolder[] startVarHolders, XDMIterator endExpr, WindowVariable[] endVars, VariableHolder[] endVarHolders){
		WindowEarlyBinding window = null;
		synchronized (lock) {
			window = new WindowEarlyBinding(this, windowId, startPosition, startExpr, startVars, startVarHolders, endExpr, endVars, endVarHolders);
			winds.put(new Integer(windowId), window);
			windowId++;
		}
		return window;
	}

	public WindowEarlyBindingParallel getNewEarlyParallelWindowInterface(int startPosition, WindowVariable[] startVars, VariableHolder[] startVarHolders, XDMIterator endExpr, WindowVariable[] endVars, VariableHolder[] endVarHolders){
		WindowEarlyBindingParallel window = null;
		synchronized (lock) {
			window = new WindowEarlyBindingParallel(this, windowId, startPosition, startVars, startVarHolders, endExpr, endVars, endVarHolders);
			winds.put(new Integer(windowId), window);
			windowId++;
		}
		return window;
	}	
	
	
	/**
	 * Returns a new window iterator for the given range with node id's (node
	 * id's start with 0 not 1)
	 * 
	 * @param startNodeId
	 * @param endNodeId
	 * @return
	 */
	protected WindowIterator getNewWindowIteratorWithNodeIds(int startNodeId, int endNodeId) {
		WindowSequenceIterator window = null;
		synchronized (lock) {
			window = new WindowSequenceIterator(this, windowId, startNodeId, endNodeId);
			//System.out.print("Creating new window: "+winds.size());
 
			winds.put(new Integer(windowId), window);
			//System.out.println(" "+winds.size());

			windowId++;
		}
		return window;
	}

	// TODO: Destroy window is not completly integrated
	/**
	 * Destroys a window.
	 * 
	 * @param id
	 */
	void destroyWindow(int id) {
		synchronized (lock) {
		    	//System.out.print(winds.size()+"   ");
			//WindowIterator window = (WindowIterator)winds.remove(new Integer(id));
			winds.remove(new Integer(id));
			//System.out.println(winds.size());
		}
	}

//	int getAttributePosFromTokenId(String attrName, int activeTokenId) throws MXQueryException {
//		return tokenBuffer.getAttributePosFromTokenId(attrName, activeTokenId);
//	}
//
//	int getAttributePosFromNodeId(String attrName, int nodeId) throws MXQueryException {
//		return tokenBuffer.getAttributePosFromNodeId(attrName, nodeId);
//
//	}

	/**
	 * Returns the current NodeId for the activeTokenId. To make the search
	 * faster, this method use the last known nodeId.
	 * 
	 * @param iter
	 * @return
	 */
	protected int getNodeIdFromTokenId(int activeTokenId) throws MXQueryException {
		return getNodeIdFromTokenId(activeTokenId, 0);
	}

	/**
	 * Returns the current NodeId for the activeTokenId. To make the search
	 * faster, this method use the last known nodeId.
	 * 
	 * @param iter
	 * @return
	 */
	protected int getNodeIdFromTokenId(int activeTokenId, int lastKnownNodeId) throws MXQueryException {
		return tokenBuffer.getNodeIdFromTokenId(lastKnownNodeId, activeTokenId);
	}

	protected int getTokenIdFromNodeId(int nodeId) throws MXQueryException {
		return tokenBuffer.getTokenIdForNode(nodeId);
	}

	/**
	 * Tests if the underlying source iterator has the node
	 * 
	 * @param node
	 * @return
	 * @throws MXQueryException
	 */
	boolean hasNode(int node) throws MXQueryException {

		return tokenBuffer.hasNode(node);
	}

	/**
	 * Sets the context. Especially used for external vars.
	 * 
	 * @param context
	 */
	public void setContext(Context context) throws MXQueryException {
		tokenBuffer.setContext(context);
	}

	/**
	 * Returns the next Token for the active window
	 * 
	 * @param window
	 *            Asking window
	 * @param endNode
	 *            Max node (range)
	 * @param restart
	 *            Restarts for a new node. Can happend if you jump over tokens
	 * @return
	 * @throws MXQueryException
	 */
	TokenInterface next(WindowIterator window, int activeTokenId, int endNode) throws MXQueryException {
		
		// commented out by hummer@dsg.infosys.tuwien.ac.at
		//garbTime++;

		/* modified by hummer@dsg.tuwien.ac.at */
		//if (garbTime > garbCollTreshold) {
		if (garbTime > garbCollTreshold.get()) {
			synchronized (lock) {
				int windsSize = 0;
				windsSize = winds.size();
				if (windsSize > 0) {
					doGarbageCollection();
				}
				garbTime = 0;
			}
		}

		TokenInterface i = tokenBuffer.get(activeTokenId, endNode);
		
		/* added by hummer@dsg.tuwien.ac.at */
		if(i.getEventType() == 34) { // close XML element event
			garbTime++;
		}
		
		return i;
	}

	/**
	 * This method returns a new Window Iterator. If the underlying iterator is
	 * already a window iterator, the same window buffer is used.
	 * !!! Make sure that the sourceIter is reseted! Otherwise you might get
	 * strange results!
 
	 * @param sourceIter
	 * @param startPosition
	 * @param endPosition
	 * @param sync
	 * @param size
	 * @param garbColl
	 * @param sameSchema
	 * @return Creates a new Window for this iterator
	 * @throws MXQueryException
	 */
		
	public static Window getNewWindowInterface(XDMIterator sourceIter, int startPosition, int endPosition,
			boolean sync, int size, int garbColl, boolean sameSchema) throws MXQueryException {
		
		if (sourceIter instanceof VariableIterator) {
			VariableIterator var = (VariableIterator)sourceIter;
			XDMIterator under = var.getUnderlyingIterator();
			if (under instanceof Window) {
			    Window window = (Window)under; 
			    if (!(startPosition == 1 && endPosition == Window.END_OF_STREAM_POSITION) || var.isResettable())
				window = window.getNewWindow(startPosition, endPosition);
			    if(var.isResettable()){
				window.setResettable(true);
			    }
			    return window;
			}			
		} else if(sourceIter instanceof WindowIterator){
			WindowIterator iter = (WindowIterator)sourceIter;
			return iter.getNewWindow(startPosition, endPosition);
		}
		WindowBuffer buffer = new WindowBuffer(sourceIter, sync, size, garbColl, sameSchema);
		runningBufferId++;
		return buffer.getNewWindowIterator(startPosition, endPosition);
	}
		
	private int call; 
	private int debugBuffer= 0;
	private boolean garbDebugPrint=false;
	private void doGarbageCollection() throws MXQueryException {
		
		if (debug) {
			System.out.println("\n!!!! Buffer: " + bufferId + " TokenBuffer:" + tokenBuffer.getMyId()
					+ " should be garbage collected - Call " + call);
			garbDebugPrint=true;
		}

		int lowestNodeId = Window.END_OF_STREAM_POSITION;
			Set keySet = winds.keySet();

			java.util.Iterator iter = keySet.iterator();
			while (iter.hasNext()) {
				WindowIterator queryIterator = (WindowIterator) winds.get(iter.next());
				if (queryIterator.isResettable()) {
					lowestNodeId = min(queryIterator.getStartNode(), lowestNodeId);
					if(garbDebugPrint && debugBuffer == this.bufferId)
						System.out.println("B" + this.bufferId + 
								": Window:" + queryIterator.getWindowId() + 
								" inUse:" + queryIterator.isWindowInUse() + 
								" startNode:" + queryIterator.getStartNode() +
								" resetable:" + queryIterator.isResettable() +
								" class:" + queryIterator.getClass().getName() + 
								" lowestNodeId:" + lowestNodeId);
				} else {
					lowestNodeId = min(queryIterator.getNodeId(), lowestNodeId);
					
					if(garbDebugPrint && debugBuffer == this.bufferId)
						System.out.println("B" + this.bufferId + 
								": Window:" + queryIterator.getWindowId() + 
								" inUse:" + queryIterator.isWindowInUse() + 
								" lowestNodeId:" + queryIterator.getNodeId() + 
								" resetable:" + queryIterator.isResettable() + 
								" class:" + queryIterator.getClass().getName() + 
								" lowestNodeId:" + lowestNodeId);
				}
	
		}
			
			
		
		if (lowestNodeId >= 2) {
//			try {
//				if (call % 10 == 0){
//					System.out.println("~~~~ Buffer: " + bufferId + " Delete tokens until:" + lowestNodeId + " ~~~~");
//				}
				//tokenBuffer.deleteTokens(lowestNodeId - 1);
				tokenBuffer.deleteItems(lowestNodeId - 1);
//			} catch (MXQueryException e) {
//				e.printStackTrace();
//			}
		}else{
//			if (call % 10 == 0){
//				System.out.println("~~~~ Buffer: " + bufferId + " NOT DELETED ~~~~");
//			}
		}
		call++;
	}

	private int min(int a, int b) {

		if (a < b) {
			return a;
		} else {
			return b;
		}
	}
	public WindowBuffer copy(Context context, Vector nestedPredCtxStack) throws MXQueryException {
		// TODO: Should the same token buffer be shared/the source be used?
	    WindowBuffer cp;
		if (tokenBuffer instanceof FTTokenBufferStore)
			cp = new WindowBuffer(source.copy(context, null, false, nestedPredCtxStack), ((FTTokenBufferStore)tokenBuffer).getLang() , tokenBuffer.getURI());
		else if (tokenBuffer instanceof MaterializingTokenBufferStore) {
		    MaterializingTokenBufferStore tokCp = ((MaterializingTokenBufferStore)tokenBuffer).copy();
		    cp = new WindowBuffer(tokCp);
		    tokCp.setIterator(source.copy(context, null, false, nestedPredCtxStack));
		} else 
			/* modified by hummer@dsg.tuwien.ac.at */
		    //cp = new WindowBuffer(source.copy(context, null, false, nestedPredCtxStack), sync, 0, garbCollTreshold, false);
			cp = new WindowBuffer(source.copy(context, null, false, nestedPredCtxStack), sync, 0, garbCollTreshold.get(), false);
		cp.tokenBuffer.setScoring(source.isScoring());
		return cp;
		
	}

	public int compare(Source store) {
		return tokenBuffer.compare(store);
	}

	public Source copySource(Context ctx, Vector nestedPredCtxStack)
			throws MXQueryException {
		return null;
	}

	public Iterator getIterator(Context ctx) throws MXQueryException {
		return tokenBuffer.getIterator(ctx);
	}

	public String getURI() {
		return tokenBuffer.getURI();
	}

	public void require(int requiredOptions) throws MXQueryException{
	    if ((requiredOptions & DataflowAnalysis.FT_SUPPORT) != 0 && tokenBuffer instanceof FTTokenBufferStore)
		return;
	    else if (source != null) {
		source = source.require(requiredOptions);
		tokenBuffer.setIterator(source);
	    }
	    if ((requiredOptions & DataflowAnalysis.SCORING_SUPPORT) != 0) {
		tokenBuffer.setScoring(true);
	    }
	    
	}

	public XDMIterator getSource() {
	    return source;
	}

	public MXQueryNumber getScoreForItem(int nodeId) throws MXQueryException {
	    return tokenBuffer.getScoreForItem(nodeId);
	}

	public void setSource(XDMIterator it) {
	    source = it;
	}	
	public boolean areOptionsSupported(int requiredOptions) {
	    return false;
	}
	    
	public WindowIterator[] getOpenWindows() {
	    WindowIterator[] res = null;
	    synchronized (lock) {
		Set windows = winds.entrySet();
		res = new WindowIterator[winds.size()];
		int i=0;
		java.util.Iterator iter = windows.iterator();
		while (iter.hasNext()) {
			WindowIterator queryIterator = (WindowIterator) winds.get(iter.next());
			res[i++] = queryIterator;
		}
			
	    }
	    return res;
	}

	public int getNumberOfOpenWindows() {
	    synchronized (lock) {
		return winds.size();
	    }
	}
	
	public int getNumberOfOpenWindowsForQuery(Object queryId) {
		int wnds = 0;
		synchronized (lock) {
		    java.util.Iterator it = winds.values().iterator();
		    while (it.hasNext()){
			
			if (queryId.equals(((WindowIterator)it.next()).queryId))
			    wnds++;
		    }
		}
		return wnds;
	}
	
	public int getNumberOfOpenedWindowsTotal() {
	    return windowId;
	}

	public int getNumberOfOpenedWindowsSinceReset() {
	    return windowId - lastMemorizedWindowId;
	}
	
	public void resetOpenedWindowsStat() {
	    lastMemorizedWindowId = windowId;
	}

}
