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

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import ch.ethz.mxquery.datamodel.xdm.Token;
import ch.ethz.mxquery.datamodel.xdm.TokenInterface;
import ch.ethz.mxquery.exceptions.ErrorCodes;
import ch.ethz.mxquery.exceptions.MXQueryException;
import ch.ethz.mxquery.exceptions.QueryLocation;

public final class SyncAppendTokenBuffer {
	
	private int initialCapacity = 50;		
	private double capacityIncrement = 1.5;		
	private int nodeIndexCapacity = 10;		
	private double nodeIndexIncrement = 1.5;
	private AtomicInteger tokenI = new AtomicInteger(0);
	private AtomicInteger nodeI = new AtomicInteger(0);
	private int[] nodeIndex;
	private TokenInterface[] tokenBuffer;
	private AtomicBoolean endOfStream = new AtomicBoolean(false);
	private static final int ItemSize = 12;	
	private AtomicInteger lastEndOfItem = new AtomicInteger(-1);
	
	private ReentrantReadWriteLock itemLock = new ReentrantReadWriteLock();
	private ReentrantReadWriteLock tokenLock = new ReentrantReadWriteLock();
	

	/** added by hummer@dsg.tuwien.ac.at */ 
	public BufferItem owningBufferItem;
	
	public SyncAppendTokenBuffer(int gran) {
		this.initialCapacity = gran*ItemSize;
		this.nodeIndexCapacity = gran;
		tokenBuffer = new TokenInterface[initialCapacity];
		nodeIndex = new int[nodeIndexCapacity];
	}

	public int getTokenIdForNode(int nodeId) throws MXQueryException {		
		if (nodeId < nodeI.get() ) {
			
			int tokid = 0;
			
			itemLock.readLock().lock();
			tokid = nodeIndex[nodeId];
			itemLock.readLock().unlock();
			
			return tokid;
		} else {
			if (endOfStream.get()) {
				return tokenI.get() - 1;
			} 
			throw new MXQueryException(ErrorCodes.A0009_EC_EVALUATION_NOT_POSSIBLE, "This shouldn't happen",QueryLocation.OUTSIDE_QUERY_LOC);
		}
	}

	public boolean hasNode(int node) {		
		if (node < nodeI.get() ) {
			return true;
		} else {
			return false;
		}
	}
	
	public int getNodeIdFromTokenId(int tokenId) {
		return getNodeIdFromTokenId(0, tokenId);
	}
	
	public int getNodeIdFromTokenId(int minNodeId, int tokenId)  {
		if (endOfStream.get() && (tokenId >= tokenI.get())) {
			return nodeI.get();
		}		
		int i = (minNodeId + 1);
		
		if (i < 0)
			i = 1;
		
		while (i < nodeI.get()){
			int tokid = 0;
			
			itemLock.readLock().lock();
			tokid = nodeIndex[i];
			itemLock.readLock().unlock();
			
			if ( tokid <= tokenId ) {
				i++;
			}
			else
				break;
		}
		return i - 1;
	}
	
	public TokenInterface get(int tokenId) {
		//System.out.println("TokID"+tokenId);
		TokenInterface tok = null;
		
		if ( tokenId < 0 )
		    return Token.END_SEQUENCE_TOKEN;
		
		if (tokenI.get() > tokenId) {
			
			tokenLock.readLock().lock();
			tok = tokenBuffer[tokenId];
			tokenLock.readLock().unlock();
			
			return tok;
		} else {
			
			/* added by hummer@dsg.tuwien.ac.at */
			if(tokenI.get() <= 0) {
				System.err.println("WARNING - likely exception to follow: tokenI.get() / tokenId / tokenBuffer.length : " + 
						tokenI.get() + " / " + tokenId + " / " + tokenBuffer.length);
				//System.out.println("returning");
				/* MXQuery seems to be able to handle a null value returned from this 
				 * method.. let's be optimistic that this will also hold true for future versions. */
			    return null;
			}
			
			tokenLock.readLock().lock();
			tok = tokenBuffer[tokenI.get() - 1];
			tokenLock.readLock().unlock();
			
			return tok;
		}
	}
	
	public TokenInterface getNoWait(int tokenId) throws MXQueryException {
		
		TokenInterface tok = null;
		
		if (tokenI.get() > tokenId) {
			
			tokenLock.readLock().lock();
			tok = tokenBuffer[tokenId];
			tokenLock.readLock().unlock();
			
			return tok;
		}
		
		return null;
	}
	
	public TokenInterface get(int tokenId, int maxNodeId){
	    
//	    	/* Replaces the previous check; detect the end of an item
//	    	 * without materializing any token from the next item; checks
//	    	 * whether the current token id requested for is for a beginning
//	    	 * of item token */
//
//	    	if (maxNodeId +1 < nodeI.get())		  
//		    if (lastEndOfItem.get()!=-1 && tokenId == lastEndOfItem.get()+1 && tokenId!=nodeIndex[maxNodeId])
//			return Token.END_SEQUENCE_TOKEN;
//	    		    	
//	    	if (tokenId >= tokenI.get()) {
//	    	    return Token.END_SEQUENCE_TOKEN;
//	    	}
//	    
//	    	/* Detect the end of an item token request */
//	    	if (maxNodeId+1 < nodeI.get()){
//	    	    int tokid = 0;
//			
//	    	    itemLock.readLock().lock();
//	    	    tokid = nodeIndex[maxNodeId+1];
//	    	    itemLock.readLock().unlock();
//	    	    
//	    	    if (tokid == tokenId){
//	    		return Token.END_SEQUENCE_TOKEN;
//	    	    }
//	    	}
//	    		
//		return get(tokenId);
	    
	    	/* Replaces the previous check; detect the end of an item
	    	 * without materializing any token from the next item; checks
	    	 * whether the current token id requested for is for a beginning
	    	 * of item token */

	    	if (maxNodeId+1 == nodeI.get()){
	    	    if (lastEndOfItem.get()!=-1 && tokenId == lastEndOfItem.get()+1 && tokenId!=nodeIndex[maxNodeId])
	    		return Token.END_SEQUENCE_TOKEN;
	    	}
	    	    
	    
	    	/* Detect the end of an item token request */
	    	if (maxNodeId+1 < nodeI.get()){
	    	    int tokid = 0;
			
	    	    itemLock.readLock().lock();
	    	    tokid = nodeIndex[maxNodeId+1];
	    	    itemLock.readLock().unlock();
	    	    
	    	    if (tokid == tokenId){
	    		return Token.END_SEQUENCE_TOKEN;
	    	    }
	    	}
	    		
		return get(tokenId);

		
	}
	public void indexNewNode() {
		
		if (nodeI.get() == nodeIndex.length) {
			
			System.out.println("INCREASE SIZE FOR INDEX");
			nodeIndexCapacity = (int) (nodeIndexCapacity * nodeIndexIncrement);
			int[] newIndex = new int[nodeIndexCapacity];
			
			itemLock.readLock().lock();
			System.arraycopy(nodeIndex, 0, newIndex, 0, nodeI.get());
			itemLock.readLock().unlock();
			
			itemLock.writeLock().lock();
			nodeIndex = newIndex;
			itemLock.writeLock().unlock();
		}
		
		itemLock.writeLock().lock();
		nodeIndex[nodeI.get()] = tokenI.get();
		itemLock.writeLock().unlock();
		
		nodeI.getAndIncrement();
	}
	
	public void bufferToken(TokenInterface token, boolean isEndOfItem) { 
		
		if (tokenI.get() == tokenBuffer.length) {	
			System.out.println("INCREASE SIZE FOR BUFFER");
			initialCapacity = (int) (initialCapacity * capacityIncrement);
			TokenInterface[] newTokenBuffer = new TokenInterface[initialCapacity];
			
			tokenLock.readLock().lock();
			System.arraycopy(tokenBuffer, 0, newTokenBuffer, 0, tokenI.get());
			tokenLock.readLock().unlock();
			
			tokenLock.writeLock().lock();
			tokenBuffer = newTokenBuffer;
			tokenLock.writeLock().unlock();
		}

		tokenLock.writeLock().lock();
		tokenBuffer[tokenI.get()] = token;
		tokenLock.writeLock().unlock();

		tokenI.getAndIncrement();

		if ( token == Token.END_SEQUENCE_TOKEN ){
			endOfStream.set(true);
		}

		if (isEndOfItem)
		    lastEndOfItem.set(tokenI.get()-1);
		
		/** added by hummer@dsg.tuwien.ac.at */
		// TODO: remove!
//		if(isEndOfItem && owningBufferItem != null) {
//			long c = owningBufferItem.owningStore.getOwningStoreSet().getBufferNodeCount();
//			if(c % 50 == 0) {
//				System.out.println("---> size: " + c);
//			}
//		}
	}
		
	public int getSize(){
		return tokenI.get();
	}
	
	public int getMaxNodeId() {
		return nodeI.get();
	}
	
	public int getMaxTokenId() {
		return tokenI.get();
	}
	
	public boolean isEndOfStream(){
		return endOfStream.get();
	}
	
	public void clear(){
		tokenI = new AtomicInteger(0);
		nodeI =  new AtomicInteger(0);
	}
	
	public int getCurrentTokenId(){
		return tokenI.get();
	}
}


