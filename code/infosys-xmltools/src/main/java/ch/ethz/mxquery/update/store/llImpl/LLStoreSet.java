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

package ch.ethz.mxquery.update.store.llImpl;

import java.io.IOException;

import ch.ethz.mxquery.MXQueryGlobals;
import ch.ethz.mxquery.util.PrintStream;
import java.util.Enumeration;
import ch.ethz.mxquery.util.Hashtable;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import ch.ethz.mxquery.bindings.WindowBuffer;
import ch.ethz.mxquery.contextConfig.Context;
import ch.ethz.mxquery.datamodel.Identifier;
import ch.ethz.mxquery.datamodel.Source;
import ch.ethz.mxquery.exceptions.DynamicException;
import ch.ethz.mxquery.exceptions.ErrorCodes;
import ch.ethz.mxquery.exceptions.MXQueryException;
import ch.ethz.mxquery.exceptions.QueryLocation;
import ch.ethz.mxquery.exceptions.StaticException;
import ch.ethz.mxquery.model.DataflowAnalysis;
import ch.ethz.mxquery.model.Iterator;
import ch.ethz.mxquery.model.Window;
import ch.ethz.mxquery.model.XDMIterator;
import ch.ethz.mxquery.model.updatePrimitives.ExtendedAxisStore;
import ch.ethz.mxquery.model.updatePrimitives.UpdateableStore;
import ch.ethz.mxquery.sms.StoreFactory;
import ch.ethz.mxquery.sms.StreamStoreSettings;
import ch.ethz.mxquery.sms.MMimpl.MaterializingTokenBufferStore;
import ch.ethz.mxquery.sms.MMimpl.SeqFIFOStore;
import ch.ethz.mxquery.sms.ftstore.FullTextStore;
import ch.ethz.mxquery.sms.interfaces.ActiveStore;
import ch.ethz.mxquery.sms.interfaces.MaterializingStore;
import ch.ethz.mxquery.sms.interfaces.StreamStore;
import ch.ethz.mxquery.util.IOLib;
import ch.ethz.mxquery.util.LinkedList;
import ch.ethz.mxquery.util.PlatformDependentUtils;
import ch.ethz.mxquery.util.Set;
import ch.ethz.mxquery.util.URIUtils;
import ch.ethz.mxquery.xdmio.StoreSet;
import ch.ethz.mxquery.xdmio.XDMSerializer;
import ch.ethz.mxquery.xdmio.XDMSerializerSettings;

/**
 * A simple implementation of a store set.
 * 
 * @author David Alexander Graf
 * 
 */
@SuppressWarnings("all")
public class LLStoreSet implements StoreSet {
	private Hashtable stores;
	int urilessCounter;

	private Hashtable transactionLocalStores;
	
	private int atomicStack;
	private LinkedList rollbackList = new LinkedList();
	
	private Vector storesToSerialize;
	boolean serializeStores = false;
	
	/* added by hummer@dsg.tuwien.ac.at */
	public final AtomicInteger granularity = new AtomicInteger(MXQueryGlobals.granularity);

	private Hashtable collections;
	
	static class StoreURIMapping {
		public UpdateableStore store;
		public String uri;
		public StoreURIMapping(UpdateableStore store, String uri) {
			super();
			this.store = store;
			this.uri = uri;
		}
	}
	
	public LLStoreSet() {
		this.stores = new Hashtable();
		this.transactionLocalStores = new Hashtable();
		this.urilessCounter = 0; 
		this.atomicStack = 0;
		this.storesToSerialize = new Vector();
		collections = new Hashtable();
	}

	public Source createStore(String uri, XDMIterator initialDataIterator, boolean maybeSerialized) throws MXQueryException {
		return createStore(uri,initialDataIterator,true, maybeSerialized);
	}

	private Source createStore(String uri, XDMIterator initialDataIterator, boolean newIds, boolean mayBeSerialized) throws MXQueryException {
		if (this.stores.containsKey(uri)) {
			return (Source) this.stores.get(uri);
		}
		
		if (initialDataIterator instanceof Window) {
			Window wnd = (Window) initialDataIterator;
			return wnd.getStore();
		}

		// Streaming Store
		/** modified by hummer@dsg.tuwien.ac.at */
		//WindowBuffer strBuf = new WindowBuffer(initialDataIterator,false, 0,10000,false);
		WindowBuffer strBuf = new WindowBuffer(initialDataIterator,false, 0, granularity.get(), false);
		this.stores.put(uri, strBuf);
		return strBuf;
	}
	
	
	
	public Source createStore(String uri, XDMIterator initialDataIterator,
		boolean maybeSerialized, int requiredStorageOptions)
		throws MXQueryException {
		
		if (initialDataIterator instanceof Window) {
			Window wnd = (Window) initialDataIterator;
			return wnd.getStore();
		}

		if ((requiredStorageOptions & DataflowAnalysis.MATERIALIZATION_SUPPORT)!= 0 && (requiredStorageOptions &(DataflowAnalysis.FT_SUPPORT | DataflowAnalysis.UPDATE_SUPPORT)) == 0) 
			return (Source) createMaterializedStore(uri, initialDataIterator);
			
		if ((requiredStorageOptions & DataflowAnalysis.FT_SUPPORT) != 0 && (requiredStorageOptions & DataflowAnalysis.UPDATE_SUPPORT) != 0){
			throw new StaticException(ErrorCodes.A0009_EC_EVALUATION_NOT_POSSIBLE,"full-text and updates/scripting can not be performed on the same XDM instance",QueryLocation.OUTSIDE_QUERY_LOC);
		}

		
		if ((requiredStorageOptions & DataflowAnalysis.UPDATE_SUPPORT)!= 0) {
			LLStore ds = new LLStore(uri, this, initialDataIterator);
			ds.setAssignNewIds(true);
			this.stores.put(uri, ds);
			if (serializeStores && maybeSerialized && !uri.startsWith("SimpleStore_"))
				addStoreToSerialize(ds, uri);
			return ds;
		}
		//TODO: use language settings
		if ((requiredStorageOptions & DataflowAnalysis.FT_SUPPORT)!= 0) {
			WindowBuffer ftBuf = new WindowBuffer(initialDataIterator,"en",uri);
			this.stores.put(uri, ftBuf);
			return ftBuf;
		}
		
		if ((requiredStorageOptions & DataflowAnalysis.NODEID_ORDER) != 0)
		    initialDataIterator = initialDataIterator.require(DataflowAnalysis.NODEID_ORDER);
		
		// Streaming Store
		/** modified by hummer@dsg.tuwien.ac.at */
//		WindowBuffer strBuf = new WindowBuffer(initialDataIterator,false, 0,10000,false);
		WindowBuffer strBuf = new WindowBuffer(initialDataIterator,false, 0, granularity.get(), false);
		this.stores.put(uri, strBuf);
		return strBuf;
	}

	public UpdateableStore createTransactionStore(long transactionID) {
		Long tId = new Long(transactionID);
		Set transStores = (Set)transactionLocalStores.get(tId);
		if (transStores == null) {
			transStores = new Set();
			transactionLocalStores.put(tId, transStores);
		}
		final String uri = "SimpleStore_"
			+ Integer.toString(++this.urilessCounter);
		transStores.add(uri);
		return this.createUpdateableStore(uri, null,true, false);	
	}
	
	public void cleanTransationStores(long transactionID) {
		Long tId = new Long(transactionID);
		Set transStores = (Set)transactionLocalStores.get(tId);
		if (transStores != null) {
			Enumeration storeEnum = transStores.elements();
			while (storeEnum.hasMoreElements()) {
				String storeURI = (String)storeEnum.nextElement();
				stores.remove(storeURI);
			}
			transactionLocalStores.remove(tId);
		}
	}

	public Source getStore(String uri) {
		if (this.stores.containsKey(uri)) {
			return (Source) this.stores.get(uri);
		} else {
			return null;
		}
	}
	
	public Source [] getAllStores() {
		Source [] res = new Source [stores.size()];
		Enumeration storEnum = stores.elements();
		int pos = 0;
		while (storEnum.hasMoreElements()) {
			Source cur = (Source)storEnum.nextElement();
			res[pos++] = cur;
		}
		return res;
	}

	public UpdateableStore[] getUpdateableStores() {
		Vector stor = new Vector();
		Enumeration storEnum = stores.elements();
		while (storEnum.hasMoreElements()) {
			Object ob = storEnum.nextElement();
			if (ob instanceof UpdateableStore) {
				stor.addElement(ob);
			}
		}
		UpdateableStore [] ret = new UpdateableStore[stor.size()];
		for (int i=0;i<ret.length;i++) 
			ret[i] = (UpdateableStore)stor.elementAt(i);
		return ret;
	}
	
	public Identifier getParentId(Identifier id) throws MXQueryException {
		ExtendedAxisStore store = (ExtendedAxisStore)id.getStore();
		return store.getParentId(id);
	}

	public boolean hasParent(Identifier id) throws MXQueryException {
		Source src = id.getStore();
		if (src instanceof UpdateableStore) {
			UpdateableStore store = (UpdateableStore)id.getStore();
			return store.hasParent(id);
		}
		return false;
	}

	public void removeStore(Source store) {
		this.stores.remove(store.getURI());
		Long tId = new Long(PlatformDependentUtils.getTransactionContextId());
		Set transStores = (Set)transactionLocalStores.get(tId);
		if (transStores != null) {
				transStores.remove(store.getURI());
		}
	}

	public boolean isAtomicMode() {
		return this.atomicStack > 0;
	}

	public void addRollback(RollbackItem ri) {
		this.rollbackList.addFirst(ri);
	}
	
	public void freeRessources() {
		this.stores.clear();
		this.transactionLocalStores.clear();
		this.urilessCounter = 0;
		this.atomicStack = 0;
		this.storesToSerialize = new Vector();
		this.collections = new Hashtable();
		
	}
	
	public String toString() {
		return this.toString(false);
	}

	/**
	 * Gives the possibility to add to each source the number of referencers.
	 * 
	 * @param printReferencers
	 * @return the contents of the store in an XML string
	 */
	public String toString(boolean printReferencers) {
		String str = "";
		Enumeration enumer = this.stores.keys();
		str += "<storeSet>";
		while (enumer.hasMoreElements()) {
			Source ss = (Source) this.stores.get(enumer.nextElement());
			String refInput = "";
//			if (printReferencers) {
//				refInput = "\" referencers=\"" + ss.getNrOfReferencers();
//			}
			str += "<store uri=\"" + ss.getURI() + refInput + "\">";
			if (ss instanceof LLStore) {
			    LLStore ll = (LLStore)ss;
			    str += ll.toString(2);
			} 
			
			str += "</store>";
		}
		str += "</storeSet>";
		try {
		    return XDMSerializer.XMLPrettyPrint(str);
		} catch (MXQueryException e) {
		    return "Could not print store";
		}
	}
	
	public StoreSet copy() {
		return new LLStoreSet();
	}
	
	public UpdateableStore getNewStoreForItem(Identifier item, String uri, boolean serializeable)
	throws MXQueryException {
		UpdateableStore newSource = createUpdateableStore(uri, null, true, serializeable);

		LLToken cur = ((LLStore)item.getStore()).getToken(item);
		
		if (cur == null) {
			return newSource;
		}
		LLRefToken ref;
		if (cur instanceof LLRefToken) {
			ref = new LLRefToken(((LLRefToken) cur).getRef());
		} else {
			ref = new LLRefToken(cur);
		}
		((LLStore)newSource).insertLast(ref);
		//cur = LLToken.getSibling(cur);
		return newSource;
	}	
	/** 
	 * Adds a store to the list of stores to be serialized at PUL apply
	 * @param store Store to serialize
	 * @param uri Location where to store the contents of the store 
	 */
	public void addStoreToSerialize(UpdateableStore store, String uri) {
		 if (serializeStores)
		storesToSerialize.addElement(new StoreURIMapping(store, uri));
	}

	Vector getStoresToSerialize() {
		return storesToSerialize;
	}
	
	public void serializeStores(boolean createBackup, String baseURI) throws MXQueryException {
		Vector serStores = storesToSerialize;
		if (serStores != null)
			for (int i=0; i<serStores.size();i++) {
				StoreURIMapping map = (StoreURIMapping)serStores.elementAt(i);
				if (map.store!= null && map.store.isModified()) {
					String url = null;
					url = URIUtils.resolveURI(baseURI, map.uri,QueryLocation.OUTSIDE_QUERY_LOC);
					
					if (createBackup){
						IOLib.copyFile(url, url+".bak");
					}
					
					try {
						String encoding = "UTF-8";
						Iterator toSerialize = map.store.getIterator(new Context());
						XDMSerializerSettings sets = new XDMSerializerSettings();
						sets.setVersion("1.0");
						sets.setEncoding(encoding);
						sets.setDoctypeSystem(map.store.getSystemID());
						sets.setDoctypePublic(map.store.getPublicID());
						sets.setDoctypeRootElem(map.store.getDoctypeRootElem());
						XDMSerializer ip = new XDMSerializer(sets);
						PrintStream outw =IOLib.getOutput(url, false,encoding);
						ip.eventsToXML(new PrintStream(outw), toSerialize); 
						outw.flush();
						outw.close();
						map.store.setModified(false);
					} catch (IOException io) {
						throw new DynamicException(ErrorCodes.A0007_EC_IO,"Failed to serialize modified stores into files",QueryLocation.OUTSIDE_QUERY_LOC);
					}
				}
			}
		storesToSerialize = new Vector();	
	}

	public void setSerializeStores(boolean serializeStores) {
		this.serializeStores = serializeStores;
	}


	public UpdateableStore createUpdateableStore(String uri, XDMIterator initialDataIterator,
			boolean newIds, boolean serializeable) {
		if (uri == null)
			uri = "SimpleStore_"
				+ Integer.toString(++this.urilessCounter);
		LLStore ds = new LLStore(uri, this, initialDataIterator);
		//System.out.print(""+ds.getNrOfReferencers());
		ds.setAssignNewIds(newIds);
		this.stores.put(uri, ds);
		if (serializeable)
			addStoreToSerialize(ds, uri);
		return ds;
	}

	/* (non-Javadoc)
	 * @see ch.ethz.mxquery.contextConfig.XQDynamicContext#getCollection(java.lang.String)
	 */
	public XDMIterator[] getCollection(String uri) throws MXQueryException{
		return getCollection(uri, 0);
	}

	public XDMIterator[] getCollection(String uri, int requiredOptions) throws MXQueryException{
		XDMIterator [] coll = (XDMIterator []) collections.get(uri);
		if (coll == null)
		    return null;
		XDMIterator [] collContents = new XDMIterator[coll.length];
		for (int i=0;i<coll.length;i++) {
		    String storeURI = uri+"_"+i;
		    Source src = getStore(storeURI);
		    if (src == null) {
			src = createStore(storeURI,coll[i],false,requiredOptions);
			
		    }
		    collContents[i] = src.getIterator(null);
		}
		return collContents;
	}

	
	/* (non-Javadoc)
	 * @see ch.ethz.mxquery.contextConfig.XQDynamicContext#setCollection(java.lang.String, java.util.Vector)
	 */
	public void addCollection(String uri, Vector coll) throws MXQueryException{
		if (collections.contains(uri))
			throw new DynamicException(ErrorCodes.A0009_EC_EVALUATION_NOT_POSSIBLE,"Collection with same identifier already exists "+uri,QueryLocation.OUTSIDE_QUERY_LOC);
		XDMIterator [] collContents = new XDMIterator [coll.size()];
		coll.copyInto(collContents);
		collections.put(uri, collContents);
	}

	public void deleteCollection(String uri) throws MXQueryException {
		collections.remove(uri);
	}

	public FullTextStore createFulltextStore(String uri,
			XDMIterator initialDataIterator, String lang) throws MXQueryException {
	    
	    	if (uri == null)
			uri = "SimpleStore_"
				+ Integer.toString(++this.urilessCounter);
		WindowBuffer ftBuf = new WindowBuffer(initialDataIterator,lang,uri);
		this.stores.put(uri, ftBuf);
		return (FullTextStore)ftBuf.getBuffer();

	}

	/** NOTE: added by hummer@infosys.tuwien.ac.at */
	public long getBufferNodeCount() {
		long c = 0;
		for(Object s : stores.values()) {
			if(s instanceof SeqFIFOStore) {
				SeqFIFOStore store = (SeqFIFOStore)s;
				c += store.getBufferNodeCount();
			} else {
				System.err.println("Store instanceof (expected: SeqFIFOStore): " + s.getClass());
			}
		}
		return c;
	}
	
	/** NOTE: added/modified by hummer@infosys.tuwien.ac.at */
	public StreamStore createStreamStore(int type, String uri) throws MXQueryException {
		return createStreamStore(type, uri, granularity.get());
	}
	public StreamStore createStreamStore(int type, String uri, int blockSize)
			throws MXQueryException {
		/* added by hummer@infosys.tuwien.ac.at */
		if(blockSize <= 0)
			blockSize = granularity.get();
		
		StreamStore sr = StoreFactory.createStore(type, blockSize, null);
		
		/* added by hummer@infosys.tuwien.ac.at */
		if(sr instanceof SeqFIFOStore) {
			((SeqFIFOStore)sr).setOwningStoreSet(this);
		}
		if (!uri.startsWith("EagerWnd")) {
			this.stores.put(uri, sr);
		}
		return sr;
	}

	public StreamStore createStreamStore(StreamStoreSettings sts, String uri) throws MXQueryException{
		StreamStore sr = StoreFactory.createStore(sts, null);
		this.stores.put(uri, sr);
		return sr;
	}

	public FullTextStore[] getFulltextStores() {
		Vector stor = new Vector();
		Enumeration storEnum = stores.elements();
		while (storEnum.hasMoreElements()) {
			Object ob = storEnum.nextElement();
			if (ob instanceof FullTextStore) {
				stor.addElement(ob);
			}
		}
		FullTextStore [] ret = new FullTextStore[stor.size()];
		for (int i=0;i<ret.length;i++) 
			ret[i] = (FullTextStore)stor.elementAt(i);
		return ret;
	}

	public StreamStore[] getStreamStores() {
		Vector stor = new Vector();
		Enumeration storEnum = stores.elements();
		while (storEnum.hasMoreElements()) {
			Object ob = storEnum.nextElement();
			if (ob instanceof StreamStore) {
				stor.addElement(ob);
			}
		}
		StreamStore [] ret = new StreamStore[stor.size()];
		for (int i=0;i<ret.length;i++) 
			ret[i] = (StreamStore)stor.elementAt(i);
		return ret;
	}

	public ActiveStore[] getActiveStores() {
		Vector stor = new Vector();
		Enumeration storEnum = stores.elements();
		while (storEnum.hasMoreElements()) {
			Object ob = storEnum.nextElement();
			if (ob instanceof ActiveStore) {
				stor.addElement(ob);
			}
		}
		ActiveStore [] ret = new ActiveStore[stor.size()];
		for (int i=0;i<ret.length;i++) 
			ret[i] = (ActiveStore)stor.elementAt(i);
		return ret;
	}

	public MaterializingStore createMaterializedStore(String uri,
		XDMIterator initialIterator) throws MXQueryException {
	    if (uri == null)
		uri = "SimpleStore_"
			+ Integer.toString(++this.urilessCounter);
		/** modified by hummer@dsg.tuwien.ac.at */
//	    StreamStore sr = StoreFactory.createStore(StoreFactory.MAT_TOKEN_BUFFER_ID, 10000, null);
	    StreamStore sr = StoreFactory.createStore(StoreFactory.MAT_TOKEN_BUFFER_ID, granularity.get(), null);
	    sr.setIterator(initialIterator);
	    ((MaterializingTokenBufferStore)sr).setUri(uri);
	    this.stores.put(uri, sr);
	    return (MaterializingStore)sr;
	}

}