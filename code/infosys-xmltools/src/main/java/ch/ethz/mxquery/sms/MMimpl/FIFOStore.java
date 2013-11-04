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

import java.util.HashMap;
import java.util.Vector;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import ch.ethz.mxquery.MXQueryGlobals;
import ch.ethz.mxquery.bindings.WindowBuffer;
import ch.ethz.mxquery.contextConfig.Context;
import ch.ethz.mxquery.datamodel.MXQueryNumber;
import ch.ethz.mxquery.datamodel.Source;
import ch.ethz.mxquery.datamodel.xdm.Token;
import ch.ethz.mxquery.datamodel.xdm.TokenInterface;
import ch.ethz.mxquery.exceptions.MXQueryException;
import ch.ethz.mxquery.model.Window;
import ch.ethz.mxquery.model.XDMIterator;
import ch.ethz.mxquery.sms.activeStore.ReadDataThread;
import ch.ethz.mxquery.sms.interfaces.ActiveStore;
import ch.ethz.mxquery.sms.interfaces.StreamStoreStatistics;
import ch.ethz.mxquery.update.store.llImpl.LLStoreSet;

@SuppressWarnings("all")
public abstract class FIFOStore implements MXQueryAppendUpdate, ActiveStore {

	protected WindowBuffer cont;

	private int id = -1;

	protected BufferItem current = null;

	/** modified by hummer@dsg.tuwien.ac.at */
	protected AtomicInteger granularity = new AtomicInteger(
			MXQueryGlobals.granularity);
	/** added by hummer@dsg.tuwien.ac.at */
	public int sizeLimit = -1;
	/** added by hummer@dsg.tuwien.ac.at */
	private LLStoreSet owningStoreSet;

	public XDMIterator iterator = null;

	protected int size = 0;

	protected HashMap attributes = new HashMap();

	protected final Semaphore newToken = new Semaphore(0, true);
	protected final Semaphore newItem = new Semaphore(0, true);

	protected AtomicInteger currentItem = new AtomicInteger(0);
	protected AtomicInteger currentToken = new AtomicInteger(0);

	public ReadDataThread readThread = null;
	protected GarbageCollector garbColl = null;

	protected AtomicBoolean endOfStream = new AtomicBoolean(false);
	protected boolean createNewBuffer = false;

	protected int deleteFrom = 0;
	protected int itemLength = 12; // ?????

	protected boolean isFreeBuffer = false;

	protected AtomicInteger tokenWaiting = new AtomicInteger(0);
	protected AtomicInteger itemWaiting = new AtomicInteger(0);

	protected boolean doneAttr = false;

	FIFOStore(int id, WindowBuffer container) {
		this.id = id;
		cont = container;
		// FIXME: After a discussion with Irina, this is a temporary solution
		// We should not be locking here, but for "push"-based interaction,
		// the user would have to do this explicitly
		// now reverted
		// acquireLocks();
	}

	public int getMyId() {
		return id;
	}

	public void setContainer(WindowBuffer buf) {
		cont = buf;
	}

	protected abstract void addNewBufferNode();

	protected abstract BufferItem bufferToToken(int activeTokenId);

	protected abstract BufferItem bufferToItem(int nodeId);

	protected abstract void freeBuffers();

	public void setIterator(XDMIterator it) {
		this.iterator = it;
		this.readThread = new ReadDataThread(it);
	}

	public void setContext(Context context) throws MXQueryException {
		this.readThread.setContext(context);
	}

	public TokenInterface get(int activeTokenId) throws MXQueryException {

		while (currentToken.get() <= activeTokenId && !endOfStream.get()) {
			try {
				tokenWaiting.getAndIncrement();
				if (currentToken.get() <= activeTokenId && !endOfStream.get())
					newToken.acquire();
				tokenWaiting.getAndDecrement();
			} catch (InterruptedException e) {
				// e.printStackTrace();
				// edited by hummer@infosys.tuwien.ac.at
				throw new RuntimeException(e);
			}
		}

		if (endOfStream.get() && currentToken.get() <= activeTokenId)
			return Token.END_SEQUENCE_TOKEN;

		BufferItem bi = bufferToToken(activeTokenId);

		if (bi == null
				|| !(activeTokenId >= bi.getFirstTokenId() && activeTokenId < bi
						.getLastTokenId()))
			return Token.END_SEQUENCE_TOKEN;

		return bi.get(activeTokenId);
	}

	public TokenInterface get(int activeTokenId, int endNode)
			throws MXQueryException {

		while (currentToken.get() < activeTokenId && !endOfStream.get()) {
			try {
				tokenWaiting.getAndIncrement();
				if (currentToken.get() <= activeTokenId && !endOfStream.get())
					newToken.acquire();
				tokenWaiting.getAndDecrement();
			} catch (InterruptedException e) {
				// e.printStackTrace();
				// edited by hummer@infosys.tuwien.ac.at
				throw new RuntimeException(e);
			}
		}

		if (currentToken.get() == activeTokenId && currentToken.get() > 0
				&& current.getFirstTokenId() <= activeTokenId
				&& current.getLastTokenId() >= activeTokenId)
			if (current.get(activeTokenId, endNode) == Token.END_SEQUENCE_TOKEN)
				return Token.END_SEQUENCE_TOKEN;

		while (currentToken.get() <= activeTokenId && !endOfStream.get()) {
			try {
				tokenWaiting.getAndIncrement();
				if (currentToken.get() <= activeTokenId && !endOfStream.get())
					newToken.acquire();
				tokenWaiting.getAndDecrement();
			} catch (InterruptedException e) {
				// e.printStackTrace();
				// edited by hummer@infosys.tuwien.ac.at
				throw new RuntimeException(e);
			}
		}

		if (endOfStream.get() && currentToken.get() <= activeTokenId)
			return Token.END_SEQUENCE_TOKEN;

		BufferItem bi = bufferToToken(activeTokenId);

		if (bi == null
				|| !(activeTokenId >= bi.getFirstTokenId() && activeTokenId < bi
						.getLastTokenId()))
			return Token.END_SEQUENCE_TOKEN;

		return bi.get(activeTokenId, endNode);
	}

	public int getNodeIdFromTokenId(int lastKnownNodeId, int activeTokenId)
			throws MXQueryException {

		while (currentToken.get() <= activeTokenId && !endOfStream.get()) {
			try {
				tokenWaiting.getAndIncrement();
				if (currentToken.get() <= activeTokenId && !endOfStream.get())
					newToken.acquire();
				tokenWaiting.getAndDecrement();
			} catch (InterruptedException e) {
				// e.printStackTrace();
				// edited by hummer@infosys.tuwien.ac.at
				throw new RuntimeException(e);
			}
		}

		return bufferToToken(activeTokenId).getNodeIdFromTokenId(
				lastKnownNodeId, activeTokenId);
	}

	public boolean hasNode(int nodeId) throws MXQueryException {

		while (currentItem.get() <= nodeId && !endOfStream.get()) {
			try {
				itemWaiting.getAndIncrement();
				if ((currentItem.get() <= nodeId && !endOfStream.get()))
					newItem.acquire();
				itemWaiting.getAndDecrement();
			} catch (InterruptedException e) {
				// edited by hummer@infosys.tuwien.ac.at
				// e.printStackTrace();
				throw new RuntimeException(e);
			}
		}

		BufferItem bi = bufferToItem(nodeId);

		if (bi == null) {
			return false;
		}

		return bi.hasNode(nodeId);

	}

	public int getTokenIdForNode(int nodeId) throws MXQueryException {

		while (currentItem.get() <= nodeId && !endOfStream.get()) {
			try {
				itemWaiting.getAndIncrement();
				if ((currentItem.get() <= nodeId && !endOfStream.get()))
					newItem.acquire();
				itemWaiting.getAndDecrement();
			} catch (InterruptedException e) {
				// e.printStackTrace();
				// edited by hummer@infosys.tuwien.ac.at
				throw new RuntimeException(e);
			}
		}

		return bufferToItem(nodeId).getTokenIdForNode(nodeId);

	}

	public void deleteItems(int nodeId) throws MXQueryException {
		deleteFrom = nodeId;
	}

	public int getSize() {
		return size;
	}

	public void newItem() {

		if (current == null) {
			addNewBufferNode();
		}

		/* added by hummer@dsg.tuwien.ac.at */
		if (sizeLimit > 0 && owningStoreSet != null) {
			long count = owningStoreSet.getBufferNodeCount();
			if (count % 100 == 0) {
				System.out.println(count + " - " + sizeLimit);
			}
			if (count > sizeLimit) {
				throw new RuntimeException("Buffer limit exceeded (" + count
						+ ")");
			}
		}

		if (createNewBuffer) {
			freeBuffers();

			if (!isFreeBuffer) {
				addNewBufferNode();
			}
			isFreeBuffer = false;
			createNewBuffer = false;
		}

		try {
			current.indexNewNode();
		} catch (Exception e) {

			System.out.println("Thread generating exception : "
					+ Thread.currentThread().getName());
			e.printStackTrace();
		}

		if (currentItem.get() == 1) {
			itemLength = currentToken.get();
		}

		currentItem.getAndIncrement();

		if (currentItem.get() % granularity.get() == 0) {
			createNewBuffer = true;
		}

		if (itemWaiting.get() > 0)
			newItem.release(itemWaiting.get());
	}

	/** added by hummer@dsg.tuwien.ac.at */
	public void setOwningStoreSet(LLStoreSet owningStoreSet) {
		this.owningStoreSet = owningStoreSet;
		this.granularity = owningStoreSet.granularity;
	}

	/** added by hummer@dsg.tuwien.ac.at */
	public LLStoreSet getOwningStoreSet() {
		return owningStoreSet;
	}

	/** added by hummer@dsg.tuwien.ac.at */
	public boolean reduceBufferSize(int maxSize) {
		try {
			int lastID = current.getLastNodeId();
			deleteItems(lastID);
			freeBuffers();
			return true;
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
	}

	public void endStream() {
		newItem.release(1);
	}

	public void buffer(TokenInterface token, int event, boolean isEndOfItem)
			throws MXQueryException {
		if (current == null) {
			addNewBufferNode();
		}

		current.bufferToken(token, isEndOfItem);
		if (event == -1) {
			endOfStream.set(true);
		}
		currentToken.getAndIncrement();

		if (tokenWaiting.get() > 0)
			newToken.release(tokenWaiting.get());

		if (token.isAttribute())
			addAttribute(token.getName());

	}

	private void addAttribute(String attrName) throws MXQueryException {
		if (doneAttr)
			return;
		if (attributes.containsKey(attrName)) {
			doneAttr = true;
			return;
		}

		int lastStartToken = current.getTokenIdForNode(currentItem.get() - 1);
		int offset = currentToken.get() - lastStartToken;
		attributes.put(attrName, new Integer(offset));
	}

	public int getAttributePosFromNodeId(String attrName, int nodeId)
			throws MXQueryException {

		if (!attributes.containsKey(attrName))
			return -1;

		int offset = ((Integer) attributes.get(attrName)).intValue();

		int tokenId = getTokenIdForNode(nodeId);

		TokenInterface tok = get(tokenId + offset, nodeId);

		if (tok == Token.END_SEQUENCE_TOKEN)
			return -1;

		if (tokenId + offset >= currentToken.get())
			return -1;

		return tokenId + offset;

	}

	public int getAttributePosFromTokenId(String attrName, int activeTokenId)
			throws MXQueryException {
		return -1;
	}

	public int getCurrentTokenId() {
		return currentToken.get();
	}

	public Window getIterator(Context ctx) throws MXQueryException {
		Window wnd = cont
				.getNewWindowIterator(1, Window.END_OF_STREAM_POSITION);
		wnd.setContext(ctx, false);
		return wnd;
	}

	public int compare(Source store) {
		// TODO Auto-generated method stub
		return 0;
	}

	public Source copySource(Context ctx, Vector nestedPredCtxStack)
			throws MXQueryException {
		// TODO Auto-generated method stub
		return null;
	}

	public String getURI() {
		// TODO Auto-generated method stub
		return null;
	}

	public MXQueryNumber getScoreForItem(int nodeId) throws MXQueryException {
		// TODO Auto-generated method stub
		return null;
	}

	public boolean isScoring() {
		// TODO Auto-generated method stub
		return false;
	}

	public void setScoring(boolean b) {
		// TODO Auto-generated method stub

	}

	public boolean areOptionsSupported(int requiredOptions) {
		if (requiredOptions == 0)
			return true;
		return false;
	}

	public void acquireLocks() {
		try {
			newToken.acquire();
		} catch (InterruptedException e) {
			// e.printStackTrace();
			// edited by waldemar
			throw new RuntimeException(e);
		}
		try {
			newItem.acquire();
		} catch (InterruptedException e) {
			// e.printStackTrace();
			// edited by waldemar
			throw new RuntimeException(e);
		}
	}

	public StreamStoreStatistics getStoreStatistics() {
		// TODO Auto-generated method stub
		return null;
	}

	public void resetVariantStatistics() {
		// TODO Auto-generated method stub

	}

}
