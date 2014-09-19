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
import io.hummer.util.persist.AbstractGenericDAO;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;

import at.ac.tuwien.infosys.aggr.util.Constants;

@Entity
public class User {

	@Id @GeneratedValue
	private long ID;
	@Column
	private String username;
	@Column
	private String passhash;
	@Column
	private String email;
	@Column
	private String roles = DEFAULT_ROLE;

	public static String ROLE_ADMIN = "admin";
	public static String ROLE_USER = "user";
	
	public static String DEFAULT_ROLE = ROLE_USER;
	
	private static final Util util = new Util();
	
	static {
		getDefaultUser();
	}

	public User() {}
	public User(String username, String password) {
		this.username = username;
		this.passhash = util.str.md5(password);
	}

	public static User save(User entity) {
		Map<String,Object> identifiers = Collections.singletonMap("username", (Object)entity.username);
		return AbstractGenericDAO.get(Constants.PU).save(entity, identifiers);
	}
	public static User load(String username) {
		Map<String,Object> identifiers = Collections.singletonMap("username", (Object)username);
		return AbstractGenericDAO.get(Constants.PU).load(User.class, identifiers);
	}

	public List<String> getRolesList() {
		List<String> result = new LinkedList<String>();
		if(roles == null || roles.trim().isEmpty()) {
			return Arrays.asList(DEFAULT_ROLE);
		}
		for(String s : roles.split("[ ,;]+")) {
			if(!s.trim().isEmpty())
				result.add(s);
		}
		return result;
	}

	public boolean isAdmin() {
		return hasRoleByRegex(ROLE_ADMIN);
	}
	public boolean hasRoleByRegex(String roleRegex) {
		for(String s : getRolesList()) {
			if(s.matches(roleRegex))
				return true;
		}
		return false;
	}
	
	/** 
	 * TODO: for testing purposes only..
	 * @return
	 */
	public static User getDefaultUser() {
		return save(new User("wsaggr","wsaggr!"));
	}
	
	public long getID() {
		return ID;
	}
	public void setID(long iD) {
		ID = iD;
	}
	public String getPasshash() {
		return passhash;
	}
	public void setPasshash(String passhash) {
		this.passhash = passhash;
	}
	public String getUsername() {
		return username;
	}
	public void setUsername(String username) {
		this.username = username;
	}
	public String getEmail() {
		return email;
	}
	public void setEmail(String email) {
		this.email = email;
	}
	public String getRoles() {
		return roles;
	}
	public void setRoles(String roles) {
		this.roles = roles;
	}
	
	@Override
	public String toString() {
		return "[User '" + username + "']";
	}
}
