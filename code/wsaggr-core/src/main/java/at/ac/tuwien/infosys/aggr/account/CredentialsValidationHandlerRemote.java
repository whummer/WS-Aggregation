/*
 * Project 'WS-Aggregation':
 * http://www.infosys.tuwien.ac.at/prototype/WS-Aggregation/
 *
 * Copyright 2010-2012 Vienna University of Technology
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package at.ac.tuwien.infosys.aggr.account;

import io.hummer.util.Util;

import java.util.Collections;
import java.util.Set;

import javax.xml.namespace.QName;
import javax.xml.ws.handler.MessageContext;
import javax.xml.ws.handler.soap.SOAPMessageContext;

import org.apache.log4j.Logger;
import org.biojava.bio.program.tagvalue.Aggregator;

import at.ac.tuwien.infosys.aggr.node.Gateway;
import at.ac.tuwien.infosys.aggr.node.Gateway.CheckAccessResponse;
import at.ac.tuwien.infosys.aggr.node.Registry;

/**
 * This JAX-WS message handler is responsible for intercepting
 * invocations to the gateway and checking whether the request contains
 * valid SOAP headers for authentication (username, sessionID, etc).
 * 
 * The handler works REMOTELY, that is, it invokes the {@link Gatway} service 
 * (which stores the user information) in order to check the provided 
 * credentials. This message handler is mainly used for the {@link Registry} 
 * and possible for {@link Aggregator} nodes.
 */
public class CredentialsValidationHandlerRemote extends CredentialsValidationHandler {

	private static final Logger logger = Util.getLogger(CredentialsValidationHandlerRemote.class);

	@Override
	public boolean handleMessage(SOAPMessageContext ctx) {
		/* check only inbound messages */
		if(isOutboundMessage(ctx))
			return true;
		
		AuthInfo auth = extractAuthInfoFromMessage(ctx);
		CheckAccessResponse r = null;
		try {
			r = Gateway.getGatewayProxy().checkAccess(
					auth.operation, auth.username, auth.sessionID);
			if(r.isSuccess()) {
				return true;
			}
		} catch (Exception e) {
			logger.warn("Unable to authorize access.", e);
			return false;
		}
		logger.info(r.getMessage());
		return false;
	}
	
	@Override
	public void close(MessageContext ctx) {
	}
	@Override
	public boolean handleFault(SOAPMessageContext ctx) {
		return true;
	}
	@Override
	public Set<QName> getHeaders() {
		return Collections.emptySet();
	}

}
