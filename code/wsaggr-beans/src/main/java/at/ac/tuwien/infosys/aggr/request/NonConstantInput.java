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

import io.hummer.util.ws.EndpointReference;

import java.util.List;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import org.w3c.dom.Element;

import at.ac.tuwien.infosys.aggr.waql.DataDependency;

@XmlJavaTypeAdapter(AbstractInput.Adapter.class)
@XmlType(name=NonConstantInput.JAXB_ELEMENT_NAME)
public abstract class NonConstantInput extends AbstractInput {
	
	public static final String JAXB_ELEMENT_NAME = "nonConstantInput";

	/**
	 * optional; 
	 * used if a particular target service should be used (instead of all services of a feature)
	 */
	@XmlAttribute
	private String serviceURL;
	/**
	 * optional; used if a particular target service should 
	 * be used (instead of all services of a feature)
	 */
	@XmlElement
	private EndpointReference serviceEPR;
	/**
	 * optional; take care of correct escaping of the newline \n
	 * httpHeaders="Cookie: key1=\"value2\"\nCookie: key2=\"value2\"\n"
	 */
	@XmlAttribute
	private String httpHeaders;
	/**
	 * optional; if this attribute is set, the concrete service endpoints 
	 * implementing the given feature will be queried from the service registry.
	 */
	@XmlAttribute
	private String feature;
	/**
	 * optional; if this attribute is set to false, the platform 
	 * will not perform internal caching of external data.
	 */
	@XmlAttribute
	private boolean cache = true;
	
	public NonConstantInput() { }
	public NonConstantInput(Element request) {
		this.getContent().add(request);
	}
	
	public void copyFrom(NonConstantInput toClone) {
		super.copyFrom(toClone);
		this.setFeature(toClone.getFeature());
		this.setHttpHeaders(toClone.getHttpHeaders());
		this.setServiceEPR(toClone.getServiceEPR());
		this.setServiceURL(toClone.getServiceURL());
		this.setCache(toClone.isCache());
	}
	
	// see InputTargetExtractor.extractDataSourceNode(..)
	//public abstract AbstractNode extractDataSourceNode(AbstractNode owner) throws Exception;
	
	@Override
	public List<DataDependency> getDataDependencies() {
		List<DataDependency> result = super.getDataDependencies();
		result.addAll(getDependenciesSafe(serviceURL));
		result.addAll(getDependenciesSafe(httpHeaders));
		return result;
	}
	
	@XmlTransient
	public String getServiceURL() {
		return serviceURL;
	}
	public void setServiceURL(String serviceURL) {
		this.serviceURL = serviceURL;
	}
	@XmlTransient
	public EndpointReference getServiceEPR() {
		return serviceEPR;
	}
	public void setServiceEPR(EndpointReference serviceEPR) {
		this.serviceEPR = serviceEPR;
	}
	@XmlTransient
	public String getHttpHeaders() {
		return httpHeaders;
	}
	public void setHttpHeaders(String httpHeaders) {
		this.httpHeaders = httpHeaders;
	}
	@XmlTransient
	public String getFeature() {
		return feature;
	}
	public void setFeature(String feature) {
		this.feature = feature;
	}
	@XmlTransient
	public boolean isCache() {
		return cache;
	}
	public void setCache(boolean cache) {
		this.cache = cache;
	}

}
