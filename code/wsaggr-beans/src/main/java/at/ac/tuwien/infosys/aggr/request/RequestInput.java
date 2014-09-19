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

import io.hummer.util.Util;
import io.hummer.util.ws.request.InvocationRequest;
import io.hummer.util.ws.request.RequestType;
import io.hummer.util.xml.XMLUtil;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import org.apache.log4j.Logger;
import org.w3c.dom.Element;

import at.ac.tuwien.infosys.aggr.waql.QueryPreprocessor;
import at.ac.tuwien.infosys.aggr.xml.XQueryProcessor;

@XmlRootElement(name=RequestInput.JAXB_ELEMENT_NAME)
@XmlJavaTypeAdapter(AbstractInput.Adapter.class)
//@Getter @Setter
public class RequestInput extends NonConstantInput {
	
	public static final String JAXB_ELEMENT_NAME = "input";
	
	private static final Logger logger = Util.getLogger(RequestInput.class);
	
	public static enum TargetType {ONE, ALL}

	/**
	 * type="HTTP_GET" or type="HTTP_POST" or type="SOAP" (default)
	 */
	@XmlAttribute
	private RequestType type = RequestType.SOAP; 
	/**
	 * optional; 
	 * used for aggregation query composition, i.e., if this input 
	 * uses some other aggregation query.
	 */
	@XmlAttribute
	private String query;
	@XmlAttribute(required=false)
	private Integer featureServiceFirst;
	@XmlAttribute(required=false)
	private Integer featureServiceLast;
	/**
	 * to="ONE" (default) or to="ALL"
	 */
	@XmlAttribute
	private TargetType to = TargetType.ONE;
	@XmlAttribute
	private Boolean excludeFromOutput;

	/** Monitoring interval, in seconds */
	@XmlAttribute
	private Double interval;
	
	@XmlTransient
	private List<Element> soapHeaders;
	@XmlTransient
	public String topologyID;
	
	public RequestInput() { }
	public RequestInput(Element message) {
		super(message);
	}
	public RequestInput(RequestInput toClone) {
		copyFrom(toClone);
	}
	public void copyFrom(RequestInput toClone) {
		super.copyFrom(toClone);
		this.setTo(toClone.getTo());
		this.setType(toClone.getType());
		this.setQuery(toClone.getQuery());
		this.setInterval(toClone.getInterval());
		this.setFeatureServiceFirst(toClone.getFeatureServiceFirst());
		this.setFeatureServiceLast(toClone.getFeatureServiceLast());
		this.setExcludeFromOutput(toClone.getExcludeFromOutput());
	}
	@Override
	@SuppressWarnings("all")
	public <T extends AbstractInput> T copy() throws Exception {
		RequestInput c = new RequestInput(this);
		return (T)c;
	}
	
	public void convertContentToElementIfPossible() {
		Object content = getTheContent();
		if(content == null)
			return;
		else if(content instanceof Element)
			return;
		else if(content instanceof List<?> && ((List<?>)content).size() == 1) {
			setTheContent((Element)((List<?>)content).get(0));
			return;
		} else if(!(content instanceof String))
			throw new RuntimeException("Unexpected input content: " + content + " - " + content.getClass());
		String s = ((String)content).trim();
		setTheContent(s.trim());
		if(!s.startsWith("<")) {
			logger.debug("String does not start with '<': " + s);
			return;
		}
		try {
			setTheContent(XMLUtil.getInstance().toElement(s));
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Returns a list of generated inputs, if this input contains some expression for
	 * generated inputs. Otherwise, if the input is fixed, i.e., does not contain 
	 * expressions for generating inputs, the input itself is returned as the single
	 * element of a list.
	 * @return
	 * @throws Exception
	 */
	public List<AbstractInput> generateInputs() throws Exception {
		List<AbstractInput> inputs = super.generateInputs();
		
		// now, additionally check for WAQL constructs in other parts of the request, e.g., serviceURL:
		if(getServiceURL() != null) {
			if(logger.isDebugEnabled()) logger.debug("Preprocessing serviceURL: " + getServiceURL());
			String serviceURLPreprocessed = QueryPreprocessor.preprocess(getServiceURL());
			Object result = null;
			if(serviceURLPreprocessed != null) {
				serviceURLPreprocessed = serviceURLPreprocessed.trim();
				
				int counter = 1;
				List<? extends AbstractInput> temp = inputs;
				inputs = new LinkedList<AbstractInput>();
				
				if(!serviceURLPreprocessed.startsWith("http://")) {
					try {
						result = XQueryProcessor.getInstance().execute(serviceURLPreprocessed);
			
						if(result instanceof String) {
							result = Arrays.asList((String)result);
						}
						if(result instanceof List<?>) {
							for(Object o : (List<?>)result) {
								String url = (String)o;
								if(o instanceof String)
									url = (String)o;
								else
									throw new RuntimeException("Unexpected item in XQuery result list: " + o + ", " + o.getClass());
								for(AbstractInput ai : temp) {
									RequestInput copy = ai.copy();
									copy.generatedInputID = counter++;
									copy.setServiceURL(url);
									inputs.add(copy);
								}
							}
						}
					} catch(Exception e) {
						logger.info("Unable to preprocess service URL with WAQL/XQuery engine: " + serviceURLPreprocessed);
					}
				}
			}
		}
		
		if(inputs.isEmpty()) {
			inputs.add(this);
		}
		return inputs;
	}
	
	public static synchronized List<RequestInput> extractRequests(List<AbstractInput> inputs, String feature) {
		List<RequestInput> result = new LinkedList<RequestInput>();
		for(AbstractInput input : inputs) {
			if(!(input instanceof RequestInput))
				continue;
			if(((RequestInput)input).getFeature().equals(feature)) {
				result.add(((RequestInput)input));
			}
		}
		return result;
	}
	
	public static synchronized List<RequestInput> extractRequestsWithoutFeature(List<AbstractInput> inputs) {
		List<RequestInput> result = new LinkedList<RequestInput>();
		for(AbstractInput input : inputs) {
			if(!(input instanceof RequestInput))
				continue;
			if(((RequestInput)input).getFeature() == null || ((RequestInput)input).getFeature().trim().equals("")) {
				result.add(((RequestInput)input));
			}
		}
		return result;
	}
	
	public static synchronized String[] getFeaturesFromInputs(List<AbstractInput> inputs) {
		List<String> features = new LinkedList<String>();
		for(AbstractInput input : inputs) {
			if(!(input instanceof RequestInput))
				continue;
			if(!features.contains(((RequestInput)input).getFeature()))
				features.add(((RequestInput)input).getFeature());
		}
		return features.toArray(new String[]{});
	}

	public void addSoapHeader(Element h) {
		getSoapHeaders().add(h);
	}
	public List<Element> getSoapHeaders() {
		if(soapHeaders == null)
			soapHeaders = new LinkedList<Element>();
		return soapHeaders;
	}

	public InvocationRequest getRequest() {
		List<String> hHeaders = new LinkedList<String>();
		if(getHttpHeaders() != null) {
			hHeaders = Arrays.asList(getHttpHeaders().split("\n|\\\\n"));
		}
		return new InvocationRequest(getType(), getTheContent(), hHeaders, soapHeaders);
	}

	public boolean doExcludeFromOutput() {
		return getExcludeFromOutput() == null ? false : getExcludeFromOutput();
	}

	public boolean hasMonitoringInterval() {
		return interval != null && interval > 0;
	}

	@Override
	public String toString() {
		try {
			return XMLUtil.getInstance().toString(this);
		} catch (Exception e) {
			logger.warn("Cannot convert request input to string.", e);
			return null;
		}
	}
	
	@Override
	public final boolean equals(Object o) {
		if(o == null)
			return false;
		if(!(o instanceof RequestInput))
			return false;
		RequestInput r = (RequestInput)o;
		boolean eq = (r.getExternalID() == null && getExternalID() == null) || (r.getExternalID() != null && r.getExternalID().equals(getExternalID()));
		eq &= (r.generatedInputID <= 0 && generatedInputID <= 0) || (r.generatedInputID == generatedInputID);
		return eq;
	}
	@XmlTransient
	public RequestType getType() {
		return type;
	}
	public void setType(RequestType type) {
		this.type = type;
	}
	@XmlTransient
	public String getQuery() {
		return query;
	}
	public void setQuery(String query) {
		this.query = query;
	}
	@XmlTransient
	public Integer getFeatureServiceFirst() {
		return featureServiceFirst;
	}
	public void setFeatureServiceFirst(Integer featureServiceFirst) {
		this.featureServiceFirst = featureServiceFirst;
	}
	@XmlTransient
	public Integer getFeatureServiceLast() {
		return featureServiceLast;
	}
	public void setFeatureServiceLast(Integer featureServiceLast) {
		this.featureServiceLast = featureServiceLast;
	}
	@XmlTransient
	public TargetType getTo() {
		return to;
	}
	public void setTo(TargetType to) {
		this.to = to;
	}
	@XmlTransient
	public Boolean getExcludeFromOutput() {
		return excludeFromOutput;
	}
	public void setExcludeFromOutput(Boolean excludeFromOutput) {
		this.excludeFromOutput = excludeFromOutput;
	}
	@XmlTransient
	public Double getInterval() {
		return interval;
	}
	public void setInterval(Double interval) {
		this.interval = interval;
	}
}
