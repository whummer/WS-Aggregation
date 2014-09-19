package at.ac.tuwien.infosys.bursthandling.strategy.loadshedding;

import io.hummer.util.Configuration;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import org.apache.log4j.Logger;

import at.ac.tuwien.infosys.aggr.monitor.ModificationNotification;
import at.ac.tuwien.infosys.bursthandling.strategy.EventBurstHandling;

@XmlRootElement(name = IntervalLoadShedder.JAXB_ELEMENT_NAME, namespace = Configuration.NAMESPACE)
@XmlJavaTypeAdapter(EventBurstHandling.Adapter.class)
@XmlSeeAlso(EventBurstHandling.class)
public class IntervalLoadShedder extends LoadShedder {
	/** Logger object */
	private static final org.apache.log4j.Logger logger = Logger
			.getLogger(IntervalLoadShedder.class);
	/**
	 * Strategy name.
	 */
	public static final String JAXB_ELEMENT_NAME = "intervalLoadShedder";

	/**
	 * Start time of current interval.
	 */
	@XmlTransient
	private long intervalStart;

	/**
	 * Counter for the current interval.
	 */
	@XmlTransient
	private long counter;

	/**
	 * Duration of an interval.
	 */
	@XmlElement(name = "interval")
	private long interval;

	/**
	 * Max events for an interval.
	 */
	@XmlElement(name = "maxEvents")
	private int maxEvents;

	/**
	 * Default constructor.
	 */
	public IntervalLoadShedder() {
		this(Long.MAX_VALUE, Integer.MAX_VALUE);
	}

	/**
	 * Constructor with specified interval duration and max events for an
	 * interval.
	 * 
	 * @param interval
	 *            for calculation.
	 * @param maxEvents
	 *            for an interval.
	 */
	public IntervalLoadShedder(long interval, int maxEvents) {
		this.interval = interval;
		this.maxEvents = maxEvents;
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
		long time = System.currentTimeMillis();
		logger.info("\nIntervalstart: "
				+ this.intervalStart
				+ "\nInterval     : "
				+ this.interval
				+ "\nEndtime      : "
				+ (this.interval + this.intervalStart)
				+ "\nTime         : "
				+ time
				+ "\nAdding       : "
				+ (this.interval + ((time - this.intervalStart) % this.interval)));
		// check if new interval has started
		if (time > (this.intervalStart + this.interval)) {
			this.intervalStart += this.interval
					+ ((time - this.intervalStart) % this.interval);
			this.counter = 0;
		}
		this.counter++;
		// check if event can be handled
		if (this.counter > this.maxEvents) {
			logger.info("Event shedded. (Still "
					+ ((this.intervalStart + this.interval - time) / 1000 + " seconds to wait.)"));
			return false;
		}
		return true;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * at.ac.tuwien.infosys.bursthandling.strategy.loadshedding.LoadShedder#
	 * initialize(java.lang.String)
	 */
	@Override
	public void initialize(String eventStreamID) {
		this.intervalStart = System.currentTimeMillis();
		this.counter = 0;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * at.ac.tuwien.infosys.bursthandling.strategy.EventBurstHandling#info()
	 */
	@Override
	public String info() {
		return "Interval Load Shedder with interval " + this.interval
				+ " and max event count " + this.maxEvents + ".";
	}
}
