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

import io.hummer.util.persist.AbstractGenericDAO;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.ManyToOne;

import at.ac.tuwien.infosys.aggr.util.Constants;

@Entity
public class UserSession {

	/** Default session timeout is 20 minutes. */
	public static final long DEFAULT_SESSION_DURATION_MS = 1000*60*20;

	@Id @GeneratedValue
	private long ID;
	@ManyToOne
	private User activeUser;
	@Column
	private String sessionID;
	@Column
	private long startTime;
	@Column
	private long lastActiveTime;
	@Column
	private long expiryTime;

	public UserSession() {
		this(null);
	}
	public UserSession(User user) {
		sessionID = UUID.randomUUID().toString();
		startTime = System.currentTimeMillis();
		lastActiveTime = System.currentTimeMillis();
		expiryTime = lastActiveTime + DEFAULT_SESSION_DURATION_MS;
		activeUser = user;
	}

	public static UserSession save(UserSession entity) {
		Map<String,Object> identifiers = new HashMap<String,Object>();
		identifiers.put("sessionID", (Object)entity.sessionID);
		identifiers.put("lastActiveTime", (Object)entity.lastActiveTime);
		return AbstractGenericDAO.get(Constants.PU).save(entity, identifiers);
	}

	public static UserSession load(String sessionID) {
		Map<String,Object> identifiers = new HashMap<String, Object>();
		identifiers.put("sessionID", sessionID);
		return AbstractGenericDAO.get(Constants.PU).load(UserSession.class, identifiers);
	}
	
	public boolean isExpired() {
		return System.currentTimeMillis() > expiryTime;
	}

	public void updateActivityTimestamp() {
		lastActiveTime = System.currentTimeMillis();
		expiryTime = lastActiveTime + DEFAULT_SESSION_DURATION_MS;
		save(this);
	}

	public User getActiveUser() {
		return activeUser;
	}
	public void setActiveUser(User activeUser) {
		this.activeUser = activeUser;
	}
	public String getSessionID() {
		return sessionID;
	}
	public void setSessionID(String sessionID) {
		this.sessionID = sessionID;
	}
	public long getExpiryTime() {
		return expiryTime;
	}
	public void setExpiryTime(long expiryTime) {
		this.expiryTime = expiryTime;
	}
	public long getID() {
		return ID;
	}
	public void setID(long iD) {
		ID = iD;
	}
	public long getLastActiveTime() {
		return lastActiveTime;
	}
	public void setLastActiveTime(long lastActiveTime) {
		this.lastActiveTime = lastActiveTime;
	}
	public long getStartTime() {
		return startTime;
	}
	public void setStartTime(long startTime) {
		this.startTime = startTime;
	}
	
}
