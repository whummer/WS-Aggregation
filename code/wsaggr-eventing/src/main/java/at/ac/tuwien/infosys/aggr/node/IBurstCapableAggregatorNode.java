package at.ac.tuwien.infosys.aggr.node;

import io.hummer.util.Configuration;

import javax.jws.WebMethod;
import javax.jws.WebParam;
import javax.jws.WebService;
import javax.jws.soap.SOAPBinding;
import javax.jws.soap.SOAPBinding.ParameterStyle;
import javax.jws.soap.SOAPBinding.Style;
import javax.jws.soap.SOAPBinding.Use;

import at.ac.tuwien.infosys.bursthandling.Escalation;
import at.ac.tuwien.infosys.bursthandling.strategy.EventBurstHandling.EventBurstHandlingException;
import at.ac.tuwien.infosys.events.request.EscalationRequest;
import at.ac.tuwien.infosys.events.request.PauseEscalationRequest;
import at.ac.tuwien.infosys.events.request.StopEscalationRequest;

@WebService(targetNamespace = Configuration.NAMESPACE)
public interface IBurstCapableAggregatorNode extends Escalation,
		IAggregatorNode {
	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * at.ac.tuwien.infosys.bursthandling.Escalation#handleOverloadedQuery(at
	 * .ac.tuwien.infosys.bursthandling.strategy.EventBurstHandling)
	 */
	@Override
	@SOAPBinding(style = Style.DOCUMENT, use = Use.LITERAL, parameterStyle = ParameterStyle.BARE)
	@WebMethod(operationName = "OverloadedQuery")
	public void handleOverloadedQuery(
			@WebParam(name = "escalationRequest") EscalationRequest request)
			throws EventBurstHandlingException;

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * at.ac.tuwien.infosys.bursthandling.Escalation#stopEscalation(java.lang
	 * .String)
	 */
	@Override
	@SOAPBinding(style = Style.DOCUMENT, use = Use.LITERAL, parameterStyle = ParameterStyle.BARE)
	@WebMethod(operationName = "StopEscalation")
	public void stopEscalation(@WebParam(name = "stopEscalationRequest") StopEscalationRequest request);
	
	/*
	 * (non-Javadoc)
	 * @see at.ac.tuwien.infosys.bursthandling.Escalation#pauseEscalation(java.lang.String)
	 */
	@Override
	@SOAPBinding(style = Style.DOCUMENT, use = Use.LITERAL, parameterStyle = ParameterStyle.BARE)
	@WebMethod(operationName = "PauseEscalation")
	public void pauseEscalation(@WebParam(name = "pauseEscalationRequest") PauseEscalationRequest request);
}
