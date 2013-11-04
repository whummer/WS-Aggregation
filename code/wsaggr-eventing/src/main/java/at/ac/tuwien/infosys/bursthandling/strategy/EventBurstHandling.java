package at.ac.tuwien.infosys.bursthandling.strategy;

import java.util.HashMap;
import java.util.Map;

import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.adapters.XmlAdapter;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import org.apache.log4j.Logger;
import org.w3c.dom.Element;

import at.ac.tuwien.infosys.aggr.monitor.ModificationNotification;
import at.ac.tuwien.infosys.aggr.node.AggregatorNode.AggregatorException;
import at.ac.tuwien.infosys.bursthandling.BurstManager;
import at.ac.tuwien.infosys.bursthandling.strategy.forwarding.ForwardStrategy;
import at.ac.tuwien.infosys.bursthandling.strategy.loadshedding.FixedLoadShedder;
import at.ac.tuwien.infosys.bursthandling.strategy.loadshedding.IntervalLoadShedder;
import at.ac.tuwien.infosys.bursthandling.strategy.loadshedding.LoadShedder;
import at.ac.tuwien.infosys.bursthandling.strategy.loadshedding.ProbabilisticLoadShedder;
import at.ac.tuwien.infosys.util.Configuration;
import at.ac.tuwien.infosys.util.Util;

@XmlSeeAlso({ ProbabilisticLoadShedder.class, LoadShedder.class,
		FixedLoadShedder.class, IntervalLoadShedder.class, ForwardStrategy.class })
@XmlJavaTypeAdapter(EventBurstHandling.Adapter.class)
@XmlType(name = "eventBurstHandling")
@XmlRootElement(name=EventBurstHandling.JAXB_ELEMENT_NAME, namespace=Configuration.NAMESPACE)
public abstract class EventBurstHandling {
	/**
	 * Logger object.
	 */
	private static final Logger logger = Logger
			.getLogger(EventBurstHandling.class);
	/**
	 * Strategy name.
	 */
	public static final String JAXB_ELEMENT_NAME = "eventBurstHandling";
	/**
	 * Corresponding burst manager.
	 */
	@XmlTransient
	protected BurstManager burstManager;

	/**
	 * Handling of an event according to the strategy.
	 * 
	 * @param event to be handled.
	 * @return true if further handling is required.
	 * @throws AggregatorException
	 */
	public abstract boolean handleEvent(final ModificationNotification event)
			throws AggregatorException;

	/**
	 * Returns an information string on the applied strategy.
	 * @return
	 */
	public abstract String info();

	/**
	 * Sets the burst manager, that uses the strategy.
	 * 
	 * @param burstManager
	 */
	public void setManager(BurstManager burstManager) {
		this.burstManager = burstManager;
	}

	/**
	 * Adapter for (un)marshaling of EventBurstHandling strategies.
	 * 
	 * @author andrea
	 * 
	 */
	public static class Adapter extends XmlAdapter<Object, Object> {
		public static Util util = new Util();

		private static Map<String, Class<?>> MAPPING = new HashMap<String, Class<?>>();

		static {
			MAPPING.put(ProbabilisticLoadShedder.JAXB_ELEMENT_NAME,
					ProbabilisticLoadShedder.class);
			MAPPING.put(FixedLoadShedder.JAXB_ELEMENT_NAME,
					FixedLoadShedder.class);
			MAPPING.put(IntervalLoadShedder.JAXB_ELEMENT_NAME,
					IntervalLoadShedder.class);
			MAPPING.put(ForwardStrategy.JAXB_ELEMENT_NAME,
					ForwardStrategy.class);
		}

		public Object unmarshal(Object v) {
			try {
				if (v instanceof Element) {
					Element e = (Element) v;
					Class<?> clazz = MAPPING.get(e.getLocalName());
					if (clazz != null) {
						return util.xml.toJaxbObject(clazz, e);
					}
				}
			} catch (Exception e) {
				logger.warn("Unable to unmarshal JAXB object.", e);
			}
			return v;
		}

		public Object marshal(Object v) {
			return v;
		}
	}

	/**
	 * Initializes the strategy for the given event stream id.
	 * 
	 * @param eventStreamID to be handled by the strategy.
	 * @throws EventBurstHandlingException 
	 */
	public abstract void initialize (final String eventStreamID) throws EventBurstHandlingException;
	
	/**
	 * Stops the execution of the strategy and does a cleanup.
	 */
	public abstract void shutdown ();
	
	
	public static class EventBurstHandlingException extends Exception {
		private static final long serialVersionUID = 1L;
		public EventBurstHandlingException() {}
		public EventBurstHandlingException(Throwable t) {
			super(t);
		}
		public EventBurstHandlingException(String msg, Throwable t) {
			super(msg, t);
		}
		public EventBurstHandlingException(String msg) {
			super(msg);
		}
	}
}
