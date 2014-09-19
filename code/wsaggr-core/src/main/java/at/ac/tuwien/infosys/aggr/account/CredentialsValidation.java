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

import io.hummer.util.Configuration;
import io.hummer.util.Util;

import java.util.HashMap;
import java.util.Map;

import javax.xml.namespace.QName;

import org.apache.log4j.Logger;

/**
 * This class is responsible for checking whether a service request 
 * contains valid creadentials (e.g., in the form of SOAP headers) for 
 * authentication (username, sessionID, etc).
 */
public class CredentialsValidation {

	private static final Logger logger = Util.getLogger(CredentialsValidation.class);

	private static final QName OPERATION_AGGREGATE = new QName(Configuration.NAMESPACE, "aggregate");
	private static final QName OPERATION_SET_GATEWAY = new QName(Configuration.NAMESPACE, "setGateway");
	private static final QName OPERATION_SET_STRATEGY = new QName(Configuration.NAMESPACE, "setStrategy");
	private static final QName OPERATION_CREATE_TOPOLOGY = new QName(Configuration.NAMESPACE, "createTopology");
	private static final QName OPERATION_UPDATE_TOPOLOGY = new QName(Configuration.NAMESPACE, "updateTopology");
	private static final QName OPERATION_DESTROY_TOPOLOGY = new QName(Configuration.NAMESPACE, "destroyTopology");
	private static final QName OPERATION_PERSIST_REQUEST = new QName(Configuration.NAMESPACE, "persistAggregationRequest");

	private static final Map<QName,String> PROTECTED_OPERATION_ROLES = new HashMap<QName,String>();

	static {
		PROTECTED_OPERATION_ROLES.put(OPERATION_AGGREGATE, ".*");
		PROTECTED_OPERATION_ROLES.put(OPERATION_PERSIST_REQUEST, ".*");
		PROTECTED_OPERATION_ROLES.put(OPERATION_CREATE_TOPOLOGY, ".*");
		PROTECTED_OPERATION_ROLES.put(OPERATION_UPDATE_TOPOLOGY, ".*");
		PROTECTED_OPERATION_ROLES.put(OPERATION_DESTROY_TOPOLOGY, ".*");
		PROTECTED_OPERATION_ROLES.put(OPERATION_SET_GATEWAY, "admin");
		PROTECTED_OPERATION_ROLES.put(OPERATION_SET_STRATEGY, "admin");
	}

	public static String getErrorForMessage(
			QName operation, String username, String sessionID) {

		if(logger.isDebugEnabled()) logger.debug("Validating username/sessionID: " + username + "/" + sessionID);
		
		String requiredRole = null;
		if(!PROTECTED_OPERATION_ROLES.containsKey(operation)) {
			/* session ID not required, but check at least whether the 
			 * session ID (if one was provided) matches the claimed username.
			 * And: update the session activity timestamp if the data are valid. */

			return checkAndUpdateSessionIfPresent(username, sessionID, true, null, operation);
		}
		
		requiredRole = PROTECTED_OPERATION_ROLES.get(operation);
		return checkAndUpdateSessionIfPresent(username, sessionID, false, requiredRole, operation);
	}

	private static String checkAndUpdateSessionIfPresent(String username, String sessionID,
			boolean onlyErrorIfUsernameAndNoValidSession, String requiredRoleRegex, QName operation) {

		if(username == null) {
			return onlyErrorIfUsernameAndNoValidSession ? null :
				"No username provided for protected WS operation: " + operation;
		}
		if(sessionID == null) {
			return "No sessionID provided for protected WS operation: " + operation;
		}

		username = username.trim();
		User user = User.load(username);
		if(user == null) {
			return "Invalid username provided: '" + username + "'";
		}
		if(requiredRoleRegex != null && !user.hasRoleByRegex(requiredRoleRegex)) {
			return onlyErrorIfUsernameAndNoValidSession ? null :
				"User '" + username + "' does not have the required role '" + requiredRoleRegex + "' to access the operation.";
		}
		UserSession s = UserSession.load(sessionID);
		if(s == null) {
			return "Invalid session ID: '" + sessionID + "'";
		}
		if(!username.equals(s.getActiveUser().getUsername())) {
			return "Session user does not match claimed user: '" + 
					s.getActiveUser().getUsername() + "' != '" + username + "'";
		}
		if(s.isExpired()) {
			return onlyErrorIfUsernameAndNoValidSession ? null :
				"Session expired: '" + sessionID + "'";
		}
		logger.debug("Authentication was successful.");

		/* update session activity timestamp */
		s.updateActivityTimestamp();

		return null;
	}

}
