package at.ac.tuwien.infosys.bursthandling.strategy.loadshedding;

import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import at.ac.tuwien.infosys.bursthandling.strategy.EventBurstHandling;
import io.hummer.util.Configuration;

@XmlRootElement(name=LoadShedder.JAXB_ELEMENT_NAME, namespace=Configuration.NAMESPACE)
@XmlJavaTypeAdapter(EventBurstHandling.Adapter.class)
@XmlSeeAlso(EventBurstHandling.class)
public abstract class LoadShedder extends EventBurstHandling {
	/**
	 * Strategy name.
	 */
	public static final String JAXB_ELEMENT_NAME = "loadShedder";
	

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * at.ac.tuwien.infosys.bursthandling.strategy.EventBurstHandling#initialize
	 * (java.lang.String)
	 */
	@Override
	public void initialize(String eventStreamID) {
	}
	
	/*
	 * (non-Javadoc)
	 * @see at.ac.tuwien.infosys.bursthandling.strategy.EventBurstHandling#shutdown()
	 */
	@Override
	public void shutdown() {
	}
}
