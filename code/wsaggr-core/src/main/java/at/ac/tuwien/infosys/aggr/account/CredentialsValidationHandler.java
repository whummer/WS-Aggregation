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

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.namespace.QName;
import javax.xml.ws.handler.MessageContext;
import javax.xml.ws.handler.soap.SOAPHandler;
import javax.xml.ws.handler.soap.SOAPMessageContext;

import org.apache.log4j.Logger;
import org.w3c.dom.Element;

import at.ac.tuwien.infosys.util.Configuration;
import at.ac.tuwien.infosys.util.Util;

import com.sun.xml.ws.message.AbstractHeaderImpl;

/**
 * This JAX-WS message handler is responsible for intercepting
 * invocations to the gateway and checking whether the request contains
 * valid SOAP headers for authentication (username, sessionID, etc).
 * 
 * The handler works LOCALLY, that is, the user information database needs
 * to be available from the host on which the intercepted service is deployed.
 * This message handler is mainly used for the {@link Gateway}.
 */
public class CredentialsValidationHandler implements SOAPHandler<SOAPMessageContext> {

	private static final Logger logger = Util.getLogger(CredentialsValidationHandler.class);

	private static final QName HEADER_USERNAME = new QName(Configuration.NAMESPACE, "username");
	private static final QName HEADER_SESSION_ID = new QName(Configuration.NAMESPACE, "sessionID");
	
	private Util util = new Util();

	@Override
	@SuppressWarnings("all")
	public boolean handleMessage(SOAPMessageContext ctx) {
		/* check only inbound messages */
		if(isOutboundMessage(ctx))
			return true;
		
		AuthInfo auth = extractAuthInfoFromMessage(ctx);
		String msg = CredentialsValidation.getErrorForMessage(
				auth.operation, auth.username, auth.sessionID);
		if(msg == null)
			return true;
		logger.warn(msg + "\nMessage context map was: " + new HashMap(ctx));
		return false;
	}
	
	protected static class AuthInfo {
		String username;
		String sessionID;
		QName operation;
	}
	
	protected boolean isOutboundMessage(SOAPMessageContext ctx) {
		Object outBound = ctx.get("javax.xml.ws.handler.message.outbound");
		try {
			if(outBound != null && 
					Boolean.parseBoolean(outBound.toString().trim())) {
				return true;
			}
		} catch (Exception e) {
			logger.warn("Cannot parse value of 'javax.xml.ws.handler.message.outbound' as Boolean: " + outBound);
		}
		return false;
	}
	
	protected AuthInfo extractAuthInfoFromMessage(SOAPMessageContext ctx) {
		AuthInfo auth = new AuthInfo();
		
		Map<String,Object> map = new HashMap<String, Object>();
		for(String s : ctx.keySet()) {
			map.put(s, ctx.get(s));
		}
		Object operation = ctx.get("javax.xml.ws.wsdl.operation");
		if(operation != null) {
			auth.operation = (QName)operation;
		}

		String username = null;
		String sessionID = null;

		List<?> headers = (List<?>)map.get("com.sun.xml.ws.api.message.HeaderList");
		if(headers != null && !headers.isEmpty()) {
			username = getUsername(headers);
			sessionID = getSessionID(headers);
		}

		auth.username = username;
		auth.sessionID = sessionID;
		
		return auth;
	}

	protected String getUsername(Element soapHeaderEl) {
		return getHeader(soapHeaderEl, HEADER_USERNAME);
	}
	protected String getSessionID(Element soapHeaderEl) {
		return getHeader(soapHeaderEl, HEADER_SESSION_ID);
	}
	private String getUsername(List<?> headers) {
		return getHeader(headers, HEADER_USERNAME);
	}
	private String getSessionID(List<?> headers) {
		return getHeader(headers, HEADER_SESSION_ID);
	}
	private String getHeader(List<?> headers, QName header) {
		for(Object o : headers) {
			AbstractHeaderImpl h = (AbstractHeaderImpl)o;
			QName name = new QName(h.getNamespaceURI(), h.getLocalPart());
			if(name.equals(header)) {
				return h.getStringContent();
			}
		}
		return null;
	}
	private String getHeader(Element soapHeaderEl, QName header) {
		for(Element h : util.xml.getChildElements(soapHeaderEl)) {
			QName name = new QName(h.getNamespaceURI(), h.getLocalName());
			if(name.equals(header)) {
				return h.getTextContent();
			}
		}
		return null;
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
