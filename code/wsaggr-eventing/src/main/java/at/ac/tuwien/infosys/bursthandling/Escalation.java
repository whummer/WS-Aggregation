package at.ac.tuwien.infosys.bursthandling;

import at.ac.tuwien.infosys.bursthandling.strategy.EventBurstHandling.EventBurstHandlingException;
import at.ac.tuwien.infosys.events.request.EscalationRequest;
import at.ac.tuwien.infosys.events.request.PauseEscalationRequest;
import at.ac.tuwien.infosys.events.request.StopEscalationRequest;

/**
 * @author andrea
 * 
 */
public interface Escalation {

	/**
	 * Defines which strategy should be used to handle an overload situation for
	 * the specified event stream.
	 * 
	 * @param EscalationRequest
	 *            containing the eventStreamId and the strategy.
	 * @throws EventBurstHandlingException
	 */
	public void handleOverloadedQuery(EscalationRequest request)
			throws EventBurstHandlingException;

	public void pauseEscalation(PauseEscalationRequest request);

	public void stopEscalation(StopEscalationRequest request);
}
