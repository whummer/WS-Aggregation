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

import java.util.HashSet;
import java.util.Set;

import ch.ethz.mxquery.bindings.WindowBuffer;
import ch.ethz.mxquery.sms.interfaces.RandomRead;

public class SeqFIFOStore extends FIFOStore implements RandomRead {

	private BufferItem first = null;
	private BufferItem last = null;

	private boolean endOfSequence = false;

	BufferItem cache = null;

	public SeqFIFOStore(int id, int blockSize, WindowBuffer container) {
		super(id, container);
		granularity.set(blockSize);
	}

	/** added by hummer@dsg.tuwien.ac.at */
	public long getBufferNodeCount() {
		long c = 0;
		Set<BufferItem> set = new HashSet<BufferItem>();
		BufferItem i = first;
		while (i != null && !set.contains(i)) {
			set.add(i);
			int temp = i.getBufferNodeCount();
			c += temp;
			i = i.getNext();
		}
		return c;
	}

	public void start() {

		// this.readThread = new ReadDataThread(this.iterator);
		this.readThread.init(this);
		this.readThread.setName("MAIN-READ-DATA-THREAD_" + getMyId());
		acquireLocks();
		this.readThread.start();
	}

	protected void addNewBufferNode() {

		if (getOwningStoreSet() == null) {
			System.err.println("No owner found for SeqFIFOStore!");
		}

		int crtItem = 0;
		int crtToken = 0;
		if (current != null) {
			crtItem = current.getLastNodeId();
			crtToken = current.getLastTokenId();
		}
		BufferItem tb = new BufferItem(crtItem, crtToken, granularity.get());

		/* added by hummer@infosys.tuwien.ac.at */
		tb.owningStore = this;

		if (first == null) {
			first = tb;
			last = tb;
		} else {
			last.setNext(tb);
			last = tb;
		}
		tb.setNext(first);

		current = tb;
		size++;
	}

	protected boolean endOfSequence(int activeTokenId) {
		if (endOfSequence) {
			endOfSequence = false;
			return true;
		}
		return false;
	}

	protected BufferItem bufferToToken(int activeTokenId) {
		BufferItem currentBuffer = null;

		currentBuffer = cache;

		if (currentBuffer == null)
			currentBuffer = first;

		if (!(activeTokenId >= currentBuffer.getFirstTokenId() && activeTokenId < currentBuffer
				.getLastTokenId())) {
			BufferItem tmpBuff = currentBuffer;
			currentBuffer = currentBuffer.getNext();

			while (!(activeTokenId >= currentBuffer.getFirstTokenId() && activeTokenId < currentBuffer
					.getLastTokenId()) && currentBuffer != tmpBuff) {
				// System.out.println("go to next");
				currentBuffer = currentBuffer.getNext();
				
				/* BEGIN added by andrea.floh@gmail.com */
				if (currentBuffer == null) {
					break;
				}
				/* END added by andrea.floh@gmail.com */
			}

			// cacheList.put(name,currentBuffer);

			cache = currentBuffer;
		}
		return currentBuffer;
	}

	@SuppressWarnings("all")
	protected BufferItem bufferToItem(int nodeId) {
		BufferItem currentBuffer = null;

		currentBuffer = cache;

		if (currentBuffer == null)
			currentBuffer = first;

		//System.out.println("bufferToItem: " + currentBuffer.getFirstItemId() + " <? " + nodeId + " <? " + currentBuffer.getLastNodeId()); // TODO (hummer@infosys.tuwien.ac.at)
		if (!(nodeId >= currentBuffer.getFirstItemId() && nodeId < currentBuffer
				.getLastNodeId())) {

			currentBuffer = currentBuffer.getNext();

			/* TODO added by hummer@infosys.tuwien.ac.at */
			currentBuffer = first;
			while (!(nodeId >= currentBuffer.getFirstItemId() && nodeId < currentBuffer.getLastNodeId())) {
				currentBuffer = currentBuffer.getNext();
				
				/* BEGIN added by andrea.floh@gmail.com */
				if (currentBuffer == null) {
					break;
				}
				/* END added by andrea.floh@gmail.com */
			}
			/* TODO end added by hummer@infosys.tuwien.ac.at */
			
			cache = currentBuffer;
		}
		if (currentBuffer == null)
			System.out.print("");

		return currentBuffer;
	}

	/* added by hummer@dsg.tuwien.ac.at */
	public void forceGC(int numBufferItemsToLeave) {
		// TODO needed?
//		if(numBufferItemsToLeave < 0)  {
//			numBufferItemsToLeave = 0;
//		}
//		deleteFrom = Math.max(0, last.getLastNodeId() - numBufferItemsToLeave);
//		System.out.println("force GC deleteFrom: " + deleteFrom);
//		freeBuffers();
	}
	
	protected void freeBuffers() {
		
		if (deleteFrom <= 1 || first == last)
			return;

		boolean firstTime = true;

		while (first.getLastNodeId() < deleteFrom && first != last) {

			if (!firstTime) {
				first.clear();

				/* added by hummer@dsg.tuwien.ac.at */
				first.owningStore = null;

				first = first.getNext();
				last.setNext(first);
				size--;
			} else {

				int minNodeId = last.getLastNodeId();
				int minTokId = last.getLastTokenId();

				first = first.getNext();
				last = last.getNext();

				current = last;
				current.clear();
				current.setFirstNodeId(minNodeId);
				current.setFirstTokenId(minTokId);
				current.setLastNodeId(minNodeId);
				current.setLastTokenId(minTokId);

				firstTime = false;
				isFreeBuffer = true;

			}
		}
	}

}
