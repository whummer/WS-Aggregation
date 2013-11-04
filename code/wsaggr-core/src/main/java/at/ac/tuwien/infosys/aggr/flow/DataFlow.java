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
package at.ac.tuwien.infosys.aggr.flow;

import java.util.LinkedList;
import java.util.List;

import at.ac.tuwien.infosys.aggr.waql.DataDependency;

public class DataFlow {

	public static enum DataSourceType { HTTP_HEADER, HTTP_BODY }
	
	private DataSourceType sourceType = DataSourceType.HTTP_BODY;
	private String XPathQuery;
	private final FlowNode requiredBy;
	private final List<FlowNode> providedBy = new LinkedList<FlowNode>();
	private DataDependency dependency;
	
	public DataFlow(DataDependency dep, FlowNode requiredBy) {
		this.dependency = dep;
		this.requiredBy = requiredBy;
	}
	
	public boolean isHeader() {
		return getXPathQuery().trim().startsWith("header(");
	}
	
	public String getHeaderName() {
		String s = getXPathQuery().trim();
		String key = null;
		if(s.indexOf("'") > 0)
			key = s.substring(s.indexOf("'") + 1, s.lastIndexOf("'"));
		else if(s.indexOf("\"") > 0)
			key = s.substring(s.indexOf("\"") + 1, s.lastIndexOf("\""));
		return key;
	}

	@Override
	public String toString() {
		return "[D " + getXPathQuery() + " ]";
	}

	public List<FlowNode> getProvidedBy() {
		return providedBy;
	}
	public DataSourceType getSourceType() {
		return sourceType;
	}
	public void setSourceType(DataSourceType sourceType) {
		this.sourceType = sourceType;
	}
	public String getXPathQuery() {
		return XPathQuery;
	}
	public void setXPathQuery(String XPathQuery) {
		this.XPathQuery = XPathQuery;
	}
	public DataDependency getDependency() {
		return dependency;
	}
	public void setDependency(DataDependency dependency) {
		this.dependency = dependency;
	}
	public FlowNode getRequiredBy() {
		return requiredBy;
	}
}
