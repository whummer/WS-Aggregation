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

package ch.ethz.mxquery.sms.MMimpl;

import java.util.Vector;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import ch.ethz.mxquery.MXQueryGlobals;
import ch.ethz.mxquery.bindings.WindowBuffer;
import ch.ethz.mxquery.contextConfig.Context;
import ch.ethz.mxquery.datamodel.Source;
import ch.ethz.mxquery.datamodel.types.Type;
import ch.ethz.mxquery.datamodel.xdm.LongToken;
import ch.ethz.mxquery.datamodel.xdm.TokenInterface;
import ch.ethz.mxquery.exceptions.MXQueryException;
import ch.ethz.mxquery.model.Iterator;
import ch.ethz.mxquery.model.Window;
import ch.ethz.mxquery.model.XDMIterator;
import ch.ethz.mxquery.sms.interfaces.StreamStore;
import ch.ethz.mxquery.sms.interfaces.StreamStoreStatistics;
import ch.ethz.mxquery.util.IntegerList;

@SuppressWarnings("all")
public class UpdateTokenBuffer implements StreamStore{
	
	private int id = 0;

	/** modified by hummer@dsg.tuwien.ac.at */
	private int initialCapacity = MXQueryGlobals.granularity;
	
	private double capacityIncrement = 1.5;
	private TokenInterface[] currentTokenBuffer = null;
	private int tokenI = 0;
	private ReentrantReadWriteLock lock = null;
	private boolean sync = false;
	
	WindowBuffer cont;
	
	public UpdateTokenBuffer(boolean sync, WindowBuffer container){
		currentTokenBuffer = new TokenInterface[initialCapacity];
		this.sync = sync;
		if (sync)
			lock = new ReentrantReadWriteLock();
		cont = container;
	}
	
	public void buffer(TokenInterface tok, int event, boolean isEndOfSeq){
		bufferToken(tok);
	}
	
	public void newItem(){
		
	}
	
	public void endStream() {
	    
	}
		 
	public void bufferToken(TokenInterface tok){
		
		if ( tokenI == currentTokenBuffer.length ) {			
			initialCapacity = (int) (initialCapacity * capacityIncrement);
			TokenInterface[] newTokenBuffer = new TokenInterface[initialCapacity];
			
			if (sync) 
				lock.readLock().lock();
			System.arraycopy(currentTokenBuffer, 0, newTokenBuffer, 0, tokenI);
			if (sync) 
				lock.readLock().unlock();
			
			if (sync) 
				lock.writeLock().lock();
			currentTokenBuffer = newTokenBuffer;
			if (sync) 
				lock.writeLock().unlock();
		}
		
		if (sync) 
			lock.writeLock().lock();
		currentTokenBuffer[tokenI] = tok;
		tokenI++;
		if (sync) 
			lock.writeLock().unlock();
	}
	
	public int getCurrentTokenId(){
		return tokenI;
	}
	
	public void bufferItem(TokenInterface[] items, int size){
		for ( int i=0; i<size; i++ ){
			bufferToken(items[i]);
		}
	}
	
	public boolean update(TokenInterface[] tokens, IntegerList positions, int itemS, boolean sum) throws MXQueryException{
		//-- hack ( token positions in the item starting from 0)
		//boolean sum hack too		
		int POS_BAL = 3; 
		int POS_TOLL = 4;
		//-- hack
		
		int pos = positions.get(0);		
		if ( (pos+itemS) > tokenI )
			return false;		
		for ( int i=pos; i<(pos+itemS); i++){			

			if ( (i == (pos + POS_BAL)) && sum ){
				if (sync)
					lock.readLock().lock();
				
				int prevToll = (int)currentTokenBuffer[pos + POS_TOLL].getLong();
				int prevBal = (int)currentTokenBuffer[i].getLong();
				
				if (sync) 
					lock.readLock().unlock();
				
				if (sync)
					lock.writeLock().lock();
				
				currentTokenBuffer[i] = new LongToken(Type.INT,null,prevBal + prevToll);
				
				if (sync) 
					lock.writeLock().unlock();
			}
			else{
				if (sync) 
					lock.writeLock().lock();
				currentTokenBuffer[i] = tokens[i-pos];
				if (sync) 
					lock.writeLock().unlock();
			}
		}
		return true;
	}
	
	public int size(){
		return tokenI;
	}
	
	public TokenInterface get(int pos){
		if ( pos >= tokenI )
			return null;
		
		if (sync) 
			lock.readLock().lock();
		
		TokenInterface retTok = currentTokenBuffer[pos];
		
		if (sync) 
			lock.readLock().unlock();
		return retTok;
	}
	
	public int getMyId(){
		return id;
	}

	public int compare(Source store) {
		// TODO Auto-generated method stub
		return 0;
	}

	public Source copySource(Context ctx, Vector nestedPredCtxStack) throws MXQueryException {
		// TODO Auto-generated method stub
		return null;
	}

	public String getURI() {
		// TODO Auto-generated method stub
		return null;
	}
	public Iterator getIterator(Context ctx) throws MXQueryException{
		Window wnd =  cont.getNewWindowIterator(1, Window.END_OF_STREAM_POSITION);
		wnd.setContext(ctx, false);
		return wnd;
	}

	public void setIterator(XDMIterator it) {
	}
	public boolean areOptionsSupported(int requiredOptions) {
	    return false;
	}
	    public StreamStoreStatistics getStoreStatistics() {
		// TODO Auto-generated method stub
		return null;
	    }

	    public void resetVariantStatistics() {
		// TODO Auto-generated method stub
		
	    }

}
