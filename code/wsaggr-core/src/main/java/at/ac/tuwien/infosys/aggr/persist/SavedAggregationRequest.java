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
package at.ac.tuwien.infosys.aggr.persist;

import io.hummer.util.Util;
import io.hummer.util.persist.AbstractGenericDAO;

import java.util.LinkedList;
import java.util.List;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EntityManager;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.persistence.Query;

import org.apache.log4j.Logger;
import org.hibernate.Criteria;
import org.hibernate.criterion.Restrictions;

import at.ac.tuwien.infosys.aggr.account.User;
import at.ac.tuwien.infosys.aggr.util.Constants;

@Entity
public class SavedAggregationRequest {

	private static final Util util = new Util();
	private static final Logger logger = Util.getLogger(SavedAggregationRequest.class);
	
	@Id
	@GeneratedValue
	private int ID;
	@Column(name="name")
	private String name;
	@Column(name="request", columnDefinition="LONGVARCHAR")
	private String request;
	@ManyToOne
	private User creator;
	@Column
	private boolean isPublic;

	public static SavedAggregationRequest save(SavedAggregationRequest request, boolean overwrite) {
		EntityManager em = AbstractGenericDAO.get(Constants.PU).createEntityManager();
		if(request.getCreator() != null && request.getCreator().getID() <= 0) {
			request.setCreator(User.save(request.getCreator()));
		}
		Object existing = null;
		try {
			existing = em.createQuery("from SavedAggregationRequest where name=:reqName and creator=:creator")
					.setParameter("reqName", request.getName())
					.setParameter("creator", request.getCreator()).getResultList().get(0);
		} catch (IndexOutOfBoundsException e) { /* swallow */ }
		if(existing != null) {
			if(!overwrite)
				throw new RuntimeException("Entity of same name already exists, please choose new name or set 'overwrite' to true...");
			logger.info("Overwriting existing aggregation request named '" + request.getName() + "' for user " + request.getCreator() + ".");
			SavedAggregationRequest s = (SavedAggregationRequest)existing;
			s.setName(request.getName());
			s.setRequest(request.getRequest());
			s.setPublic(request.isPublic());
			s.setCreator(request.getCreator());
			em.getTransaction().begin();
			em.merge(s);
			em.getTransaction().commit();
		} else {
			em.getTransaction().begin();
			em.persist(request);
			em.getTransaction().commit();
		}
		return request;
	}
	public static SavedAggregationRequest load(String name, User creator) {
		EntityManager em = AbstractGenericDAO.get(Constants.PU).createEntityManager();
		Query q = null;
		if(creator == null) {
			q = em.createQuery("from SavedAggregationRequest where name=:reqName and isPublic=true")
                                        .setParameter("reqName", name);
		} else {
			q = em.createQuery("from SavedAggregationRequest where name=:reqName and " +
                                        "(isPublic=true or creator=:creator)")
                                        .setParameter("reqName", name)
                                        .setParameter("creator", creator);
		}
		List<?> result =  q.getResultList();
		if(result.size() <= 0)
			throw new IllegalArgumentException("No query with name '" + name + "' found!");
		return (SavedAggregationRequest)result.get(0);
	}
	public static List<String> loadAllNames(String pattern, String creatorUsername) {
		
		Criteria criteria = AbstractGenericDAO.get(Constants.PU)
				.createCriteria(SavedAggregationRequest.class);

		if(!util.str.isEmpty(pattern)) {
			criteria.add(Restrictions.like("name", pattern));
		}
		if(!util.str.isEmpty(creatorUsername)) {
			User creator = User.load(creatorUsername);

			if(creator != null && creatorUsername != null && !creatorUsername.trim().isEmpty()) {
				criteria.add(Restrictions.or(
						Restrictions.eq("creator", creator), 
						Restrictions.eq("isPublic", true)));
			} else {
				criteria.add(Restrictions.eq("isPublic", true));
			}
		} else {
			criteria.add(Restrictions.eq("isPublic", true));
		}
		
		List<?> result = criteria.list();
		List<String> names = new LinkedList<String>();
		for(Object o : result) {
			names.add(((SavedAggregationRequest)o).getName());
		}
		return names;
	}
	
	public int getID() {
		return ID;
	}
	public void setID(int iD) {
		ID = iD;
	}
	public String getRequest() {
		return request;
	}
	public void setRequest(String request) {
		this.request = request;
	}
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	public User getCreator() {
		return creator;
	}
	public void setCreator(User creator) {
		this.creator = creator;
	}
	public boolean isPublic() {
		return isPublic;
	}
	public void setPublic(boolean isPublic) {
		this.isPublic = isPublic;
	}
	
	public static void main(String[] args) {
		SavedAggregationRequest request = new SavedAggregationRequest();
		request.setName("name3");
		request.setRequest("foo123");
		save(request, false);
	}

}
