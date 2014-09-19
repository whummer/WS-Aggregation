package at.ac.tuwien.infosys.events.request;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import io.hummer.util.Configuration;

@XmlRootElement(name = "stopEscalationRequest", namespace = Configuration.NAMESPACE)
public class StopEscalationRequest {

	@XmlElement
	private String eventStreamID;

	public StopEscalationRequest() {

	}

	public StopEscalationRequest(String eventStreamID) {
		this.eventStreamID = eventStreamID;
	}

	@XmlTransient
	public String getEventStreamID() {
		return eventStreamID;
	}

}
