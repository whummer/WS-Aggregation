package at.ac.tuwien.infosys.aggr.events.query;

/**
 * This abstract class represents an event buffer. EventBuffer is used by 
 * {@link EventStream}, which uses the buffer to store incoming events 
 * messages.
 * 
 * @author Waldemar Hummer
 */
public abstract class EventBuffer {

	public abstract long getBufferSizeKB();
	public abstract int getBufferedItems();
	/**
	 * Called if the buffer has exceeded or is about to 
	 * exceed its maximum permitted size. 
	 * @param maxBufferSize maximum number of buffer items 
	 * @return whether the size could be successfully reduced
	 */
	public abstract boolean reduceSize(int maxBufferItems);

}
