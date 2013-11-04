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
package at.ac.tuwien.infosys.aggr.request;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.XmlValue;

@XmlRootElement(name="queries")
public class WAQLQuery {
	
	@XmlElement(name="query") 
	private String query;
	@XmlElement(name="intermediateQuery") 
	private String intermediateQuery; 
	@XmlElement(name="preparationQuery") 
	private List<PreparationQuery> preparationQueries = new LinkedList<PreparationQuery>();
	@XmlElement(name="terminationQuery") 
	private List<PreparationQuery> terminationQueries = new LinkedList<PreparationQuery>();

	public static class PreparationQuery {
		/**
		 * List of comma (,) or whitespace separated IDs, which refer to the input(s) which
		 * this preparation query should be applied to.
		 */
		@XmlAttribute
		private String forInputs;
		@XmlValue
		private String value;

		public PreparationQuery() { }
		public PreparationQuery(String value) { 
			this(value, null);
		}
		public PreparationQuery(String value, String forInputs) { 
			this.value = value;
			this.forInputs = forInputs;
		}
		
		public boolean isForInput(String id) {
			if(forInputs == null || forInputs.trim().equals(""))
				return true;
			String[] parts = forInputs.split("\\s+|,");
			return Arrays.asList(parts).contains(id);
		}
		
		@XmlTransient
		public String getForInputs() {
			return forInputs;
		}
		public void setForInputs(String forInputs) {
			this.forInputs = forInputs;
		}
		@XmlTransient
		public String getValue() {
			return value;
		}
		public void setValue(String value) {
			this.value = value;
		}
		@Override
		public boolean equals(Object o) {
			if(!(o instanceof PreparationQuery))
				return false;
			PreparationQuery p = (PreparationQuery)o;
			boolean eq = (value == null && p.value == null) || (value != null && value.equals(p.value));
			eq &= (forInputs == null && p.forInputs == null) || (forInputs != null && forInputs.equals(p.forInputs));
			return eq;
		}
	}

	public List<PreparationQuery> getPreparationQueries(String externalInputID) {
		List<PreparationQuery> result = new LinkedList<PreparationQuery>();
		for(PreparationQuery q : preparationQueries) {
			if(q.isForInput(externalInputID)) {
				result.add(q);
			}
		}
		return result;
	}
	public String getPreparationQuery(String externalID) {
		List<PreparationQuery> result = getPreparationQueries(externalID);
		if(result.size() > 1)
			throw new RuntimeException("Unexpected: multiple preparation queries for input ID " + externalID);
		if(result.size() <= 0)
			return null;
		return result.get(0).value;
	}
	public List<PreparationQuery> getPreparationQueries(AbstractInput in) {
		return getPreparationQueries(in.getExternalID());
	}
	public String getPreparationQuery(AbstractInput in) {
		return getPreparationQuery(in.getExternalID());
	}

	public List<PreparationQuery> getTerminationQueries(AbstractInput in) {
		List<PreparationQuery> result = new LinkedList<PreparationQuery>();
		for(PreparationQuery q : terminationQueries) {
			if(q.isForInput(in.getExternalID())) {
				result.add(q);
			}
		}
		return result;
	}

	public void addPreparationQuery(PreparationQuery query) {
		getPreparationQueries().add(query);
	}
	public void addPreparationQuery(String forInputs, String query) {
		PreparationQuery pquery = new PreparationQuery();
		pquery.setValue(query);
		pquery.setForInputs(forInputs);
		getPreparationQueries().add(pquery);
	}
	public void addPreparationQuery(String query) {
		PreparationQuery pquery = new PreparationQuery();
		pquery.setValue(query);
		getPreparationQueries().add(pquery);
	}
	public void clear() {
		query = null;
		intermediateQuery = null;
		if(preparationQueries != null)
			preparationQueries.clear();
		if(terminationQueries != null)
			terminationQueries.clear();
	}

	@XmlTransient
	public String getIntermediateQuery() {
		return intermediateQuery;
	}
	@XmlTransient
	public List<PreparationQuery> getPreparationQueries() {
		return preparationQueries;
	}
	@XmlTransient
	public List<PreparationQuery> getTerminationQueries() {
		return terminationQueries;
	}
	@XmlTransient
	public String getQuery() {
		return query;
	}
	public void setIntermediateQuery(String intermediateQuery) {
		this.intermediateQuery = intermediateQuery;
	}
	public void setPreparationQueries(List<PreparationQuery> preparationQueries) {
		this.preparationQueries = preparationQueries;
	}
	public void setTerminationQueries(List<PreparationQuery> terminationQueries) {
		this.terminationQueries = terminationQueries;
	}
	public void setQuery(String query) {
		this.query = query;
	}
	
	public String toString() {
		StringBuilder result = new StringBuilder("<queries>" +
			(getQuery() != null ? "<query><![CDATA[" + getQuery() + "]]></query>" : "") +
			(getIntermediateQuery() != null ? "<intermediateQuery><![CDATA[" + getIntermediateQuery() + "]]></intermediateQuery>" : ""));
		for(PreparationQuery prep : getPreparationQueries()) {
			result.append("<preparationQuery forInputs=\"" + prep.getForInputs() + "\"><![CDATA[" + prep.getValue() + "]]></preparationQuery>");
		}
		for(PreparationQuery term : getTerminationQueries()) {
			result.append("<terminationQuery forInputs=\"" + term.getForInputs() + "\"><![CDATA[" + term.getValue() + "]]></terminationQuery>");
		}
		result.append("</queries>");
		return result.toString();
	}
}
