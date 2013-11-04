package at.ac.tuwien.infosys.events.request;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.XmlTransient;

import at.ac.tuwien.infosys.bursthandling.strategy.EventBurstHandling;
import at.ac.tuwien.infosys.util.Configuration;

@XmlRootElement(name="escalationRequest", namespace=Configuration.NAMESPACE)
@XmlSeeAlso(EventBurstHandling.class)
public class EscalationRequest {

	@XmlElement
	private String eventStreamID;
	
	@XmlElement(name="eventBurstHandling", namespace=Configuration.NAMESPACE)
	private EventBurstHandling strategy;
	
	public EscalationRequest() {
		
	}

	public EscalationRequest(String eventStreamID, EventBurstHandling strategy) {
		this.eventStreamID = eventStreamID;
		this.strategy = strategy;
	}
	
	@XmlTransient
	public String getEventStreamID() {
		return eventStreamID;
	}

	@XmlTransient
	public EventBurstHandling getStrategy() {
		return strategy;
	}

}
