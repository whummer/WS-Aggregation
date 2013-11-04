package at.ac.tuwien.infosys.bursthandling.strategy.loadshedding;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import org.apache.log4j.Logger;

import at.ac.tuwien.infosys.aggr.monitor.ModificationNotification;
import at.ac.tuwien.infosys.bursthandling.strategy.EventBurstHandling;
import at.ac.tuwien.infosys.util.Configuration;

@XmlRootElement(name = FixedLoadShedder.JAXB_ELEMENT_NAME, namespace=Configuration.NAMESPACE)
@XmlJavaTypeAdapter(EventBurstHandling.Adapter.class)
@XmlSeeAlso(EventBurstHandling.class)
public class FixedLoadShedder extends LoadShedder {
	/** Logger object */
	private static final org.apache.log4j.Logger logger = Logger
			.getLogger(FixedLoadShedder.class);
	/**
	 * Strategy name.
	 */
	public static final String JAXB_ELEMENT_NAME = "fixedLoadShedder";

	/**
	 * Event counter.
	 */
	@XmlTransient
	private int counter;

	/**
	 * Specification when to shed. (After <x> events, one is shed.)
	 */
	@XmlElement(name = "afterX")
	private int afterX;

	/**
	 * Default constructor.
	 */
	public FixedLoadShedder() {
		this(Integer.MAX_VALUE);
	}

	/**
	 * Constructor with specified shedding.
	 * 
	 * @param afterX
	 *            amount after which an event is shed.
	 */
	public FixedLoadShedder(int afterX) {
		this.afterX = afterX;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * at.ac.tuwien.infosys.bursthandling.strategy.EventBurstHandling#handleEvent
	 * (at.ac.tuwien.infosys.aggr.monitor.ModificationNotification)
	 */
	@Override
	public boolean handleEvent(ModificationNotification event) {
		if (this.counter >= this.afterX) {
			this.counter = 0;
			logger.info("Event shedded.");
			return false;
		}
		this.counter++;
		return true;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * at.ac.tuwien.infosys.bursthandling.strategy.EventBurstHandling#info()
	 */
	@Override
	public String info() {
		return "Fixed Load Shedder with after each " + this.afterX + " events.";
	}

	/*
	 * (non-Javadoc)
	 * @see at.ac.tuwien.infosys.bursthandling.strategy.loadshedding.LoadShedder#initialize(java.lang.String)
	 */
	@Override
	public void initialize(String eventStreamID) {
		this.counter = 0;
	}
	
	
}
