package at.ac.tuwien.infosys.bursthandling.strategy.loadshedding;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import org.apache.log4j.Logger;

import at.ac.tuwien.infosys.aggr.monitor.ModificationNotification;
import at.ac.tuwien.infosys.bursthandling.strategy.EventBurstHandling;
import io.hummer.util.Configuration;

@XmlRootElement(name=ProbabilisticLoadShedder.JAXB_ELEMENT_NAME, namespace=Configuration.NAMESPACE)
@XmlJavaTypeAdapter(EventBurstHandling.Adapter.class)
@XmlSeeAlso(EventBurstHandling.class)
public class ProbabilisticLoadShedder extends LoadShedder {
	/** Logger object */
	private static final org.apache.log4j.Logger logger = Logger
			.getLogger(ProbabilisticLoadShedder.class);
	/**
	 * Strategy name.
	 */
	public static final String JAXB_ELEMENT_NAME = "probabilisticLoadShedder";
	
	/**
	 * Probability of an event to be shed.
	 */
	@XmlElement(name="rate")
	private double rate;
	
	/**
	 * Default constructor. (no load shedding)
	 */
	public ProbabilisticLoadShedder() {
		this(0.0);
	}
	
	/**
	 * Constructor with given rate.
	 * 
	 * @param rate probability of an event to be shed.
	 */
	public ProbabilisticLoadShedder(double rate) {
		this.rate = rate;
	}

	/*
	 * (non-Javadoc)
	 * @see at.ac.tuwien.infosys.bursthandling.strategy.EventBurstHandling#handleEvent(at.ac.tuwien.infosys.aggr.monitor.ModificationNotification)
	 */
	@Override
	public boolean handleEvent(ModificationNotification event) {
		double random = Math.random();
		if (random < (this.rate)) {
			logger.info("Event shedded. (Random value: " + random +".)");
			return false;
		}
		return true;
	}
	
	/*
	 * (non-Javadoc)
	 * @see at.ac.tuwien.infosys.bursthandling.strategy.EventBurstHandling#info()
	 */
	@Override
	public String info () {
		return "Probabilistic Load Shedder with rate " + this.rate + ".";
	}
}
