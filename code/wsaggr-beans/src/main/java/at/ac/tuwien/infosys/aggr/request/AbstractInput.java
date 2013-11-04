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

import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.xml.bind.annotation.XmlAnyElement;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlMixed;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.adapters.XmlAdapter;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import org.apache.log4j.Logger;
import org.w3c.dom.Element;
import org.w3c.dom.Text;

import at.ac.tuwien.infosys.aggr.waql.DataDependency;
import at.ac.tuwien.infosys.aggr.waql.PreprocessorEngine;
import at.ac.tuwien.infosys.aggr.waql.PreprocessorFactory;
import at.ac.tuwien.infosys.aggr.waql.QueryPreprocessor;
import at.ac.tuwien.infosys.aggr.xml.XQueryProcessor;
import at.ac.tuwien.infosys.monitoring.config.MonitoringConfigPublications;
import at.ac.tuwien.infosys.monitoring.config.NodeConfigInput;
import at.ac.tuwien.infosys.monitoring.config.SOAPEventInput;
import at.ac.tuwien.infosys.util.Util;
import at.ac.tuwien.infosys.util.xml.XMLUtil;
import at.ac.tuwien.infosys.ws.request.RequestType;

@XmlSeeAlso({
	RequestInput.class,
	NonConstantInput.class,
	ConstantInput.class,
	EventingInput.class,
	NotifyInput.class,
	WebSearchInput.class,
	SavedQueryInput.class,
	SOAPEventInput.class,
	NodeConfigInput.class
})
@XmlJavaTypeAdapter(AbstractInput.Adapter.class)
@XmlType(name="abstractInput")
public abstract class AbstractInput {

	private static final Logger logger = Util.getLogger(AbstractInput.class);

	/** the parent request object that this input belongs to */
	@XmlTransient
	public Object request; // type is usually AggregationRequest
	@XmlTransient
	public int generatedInputID = 0;
	
	private final Util util = new Util();
	
	/**
	 * ID used for correlation with the preparation queries.
	 */
	@XmlAttribute(name="id")
	private String externalID;
	/**
	 * UID used for internal, unanimous identification within an aggregation request.
	 */
	@XmlAttribute(name="uid")
	private String uniqueID;
	@XmlAttribute
	private String contentType = "text/xml";
	@XmlAttribute
	private DependencyUpdateMode updateDependencies = DependencyUpdateMode.always;
	
	@XmlElement(name=MonitoringConfigPublications.NAME_ELEMENT)
	private MonitoringConfigPublications publications;
	
	@XmlAnyElement
	@XmlMixed
	private	final List<Object> content = new LinkedList<Object>();
	
	
	public abstract <T extends AbstractInput> T copy() throws Exception;
	
	@XmlTransient
	public MonitoringConfigPublications getPublications() {
		return publications;
	}
	public void setPublications(MonitoringConfigPublications publications) {
		this.publications = publications;
	}
	
	@SuppressWarnings("all")
	public <T extends AbstractInput> T copyViaJAXB() throws Exception {
		return (T)util.xml.toJaxbObject(getClass(), util.xml.toElement(this));
	}

	@XmlTransient
	public void setTheContent(Object o) {
		getContent().clear();
		getContent().add(o);
	}
	public Object getTheContent() {
		for(int i = 0; i < getContent().size(); i ++) {
			Object o = getContent().get(i);
			if(o instanceof Text) {
				Text t = (Text)o;
				if(t.getTextContent().trim().equals(""))
					getContent().remove(i--);
			} else if(o instanceof String) {
				if(((String)o).trim().equals(""))
					getContent().remove(i--);
			}
		}
			
		if(getContent().size() == 1)
			return getContent().get(0);
		else if(getContent().size() == 0)
			return null;
		return getContent();
	}

	public Element getTheContentAsElement() throws Exception {
		Object o = getTheContent();
		if(o == null)
			return null;
		if(o instanceof Element)
			return (Element)o;
		if(o instanceof List<?> && ((List<?>)o).size() == 1)
			return (Element)((List<?>)o).get(0);
		if(o instanceof String && ((String)o).trim().startsWith("<")) 
			return util.xml.toElement((String)o);
		throw new Exception("Cannot convert to Element (class " + (o != null ? o.getClass() : o) + "): " + o);
	}
	
	public AbstractInput getFirstInListByExternalID(List<AbstractInput> toSearch) {
		for(AbstractInput in : toSearch) {
			if(in.getExternalID().equals(getExternalID())) {
				return in;
			}
		}
		return null;
	}
	
	public boolean searchInListByID(List<AbstractInput> toSearch) {
		return getFirstInListByExternalID(toSearch) != null;
	}
	
	public List<DataDependency> getDataDependencies() {
		List<DataDependency> result = new LinkedList<DataDependency>();
		try {
			Object content = getTheContent();
			if(content == null)
				return result;
			String contentString = null;
			if(content instanceof Element)
				contentString = util.xml.toString((Element)content);
			else if(content instanceof String)
				contentString = (String)content;
			result.addAll(getDependenciesSafe(contentString, isContentTypeWAQL()));
		} catch (Exception e) {
			if(logger.isDebugEnabled()) logger.debug("Unable to extract data dependencies.", e);
			if(e instanceof RuntimeException)
				throw (RuntimeException)e;
			throw new RuntimeException(e);
		}
		return result;
	}

	protected List<DataDependency> getDependenciesSafe(String contentString) {
		return getDependenciesSafe(contentString, false);
	}
	protected List<DataDependency> getDependenciesSafe(
			String contentString, boolean forceWAQL) {
		String originalContentString = contentString;
		List<DataDependency> result = new LinkedList<DataDependency>();
		if(util.str.isEmpty(contentString))
			return result;
		PreprocessorEngine engine = PreprocessorFactory.getEngine();
		try {
			/* first try */
			engine.parse(new ByteArrayInputStream(contentString.getBytes()));
			result.addAll(engine.getDependencies());
		} catch (Exception e) {
			/* second try: replace '&' with '&amp;' */
			contentString = QueryPreprocessor.replaceAmpersands(contentString);
			engine = PreprocessorFactory.getEngine();
			try {
				engine.parse(new ByteArrayInputStream(contentString.getBytes()));
				result.addAll(engine.getDependencies());
			} catch (Exception e1) {
				if(forceWAQL) {
					/* last try: wrap string in a dummy element <a>...</a> */
					contentString = "<a>" + contentString + "</a>";
					engine = PreprocessorFactory.getEngine();
					try {
						engine.parse(new ByteArrayInputStream(contentString.getBytes()));
						result.addAll(engine.getDependencies());
					} catch (Exception e2) {
						throw new RuntimeException(
								"Not a valid WAQL query expression: " + originalContentString);
					}
				}
			}
		}
		return result;
	}
	
	public boolean isContentTypeWAQL() {
		return getContentType() != null && getContentType().trim().equalsIgnoreCase("WAQL");
	}

	@XmlTransient
	public String getUniqueID() {
		return uniqueID;
	}
	
	public void setUniqueID(String uniqueID) {
		this.uniqueID = uniqueID;
	}
	
	@XmlTransient
	public String getContentType() {
		return contentType;
	}
	
	public void setContentType(String contentType) {
		this.contentType = contentType;
	}
	
	@XmlTransient
	public DependencyUpdateMode getUpdateDependencies() {
		return updateDependencies;
	}
	
	public void setUpdateDependencies(DependencyUpdateMode updateDependencies) {
		this.updateDependencies = updateDependencies;
	}
	
	@XmlTransient
	public List<Object> getContent() {
		return content;
	}
	
	@XmlTransient
	public String getExternalID() {
		return externalID;
	}
	
	public void setExternalID(String externalID) {
		this.externalID = externalID;
	}
	
	@Override
	public boolean equals(Object o) {
		if(!(o instanceof AbstractInput))
			return false;
		AbstractInput r = (AbstractInput)o;
		boolean eq = (r.getExternalID() == null && getExternalID() == null) || (r.getExternalID() != null && r.getExternalID().equals(getExternalID()));
		eq &= (r.request == null && request == null) || (r.request != null && r.request.equals(request));
		return eq;
	}
	
	@Override
	public int hashCode() {
		int result = 0;
		result += (getExternalID() == null) ? 0 : getExternalID().hashCode();
		result += (request == null) ? 0 : request.hashCode();
		return result;
	}
	
	@Override
	public String toString() {
		try {
			return util.xml.toString(this);
		} catch (Exception e) {
			logger.warn("Could not convert input object to String.", e);
			return null;
		}
	}
	
	
	public static class Adapter extends XmlAdapter<Object,Object> {
		public static Util util = new Util();
		
		private static Map<String, Class<?>> MAPPING = new HashMap<String, Class<?>>();
		
		static {
			MAPPING.put(RequestInput.JAXB_ELEMENT_NAME, RequestInput.class);
			MAPPING.put(SavedQueryInput.JAXB_ELEMENT_NAME, SavedQueryInput.class);
			MAPPING.put(ConstantInput.JAXB_ELEMENT_NAME, ConstantInput.class);
			MAPPING.put(EventingInput.JAXB_ELEMENT_NAME, EventingInput.class);	
			MAPPING.put(NotifyInput.JAXB_ELEMENT_NAME, NotifyInput.class);	
			MAPPING.put(WebSearchInput.JAXB_ELEMENT_NAME, WebSearchInput.class);
			MAPPING.put(SOAPEventInput.JAXB_ELEMENT_NAME, SOAPEventInput.class);
			MAPPING.put(NodeConfigInput.JAXB_ELEMENT_NAME, NodeConfigInput.class);	
		}
		
		public Object unmarshal(Object v) {
			try {
				if (v instanceof Element) {
					Element e = (Element) v;
					Class<?> clazz = MAPPING.get(e.getLocalName());
					if(clazz != null){
						return util.xml.toJaxbObject(clazz, e);
					}				
				}
			} catch (Exception e) {
				logger.warn("Unable to unmarshal JAXB object.", e);
			}
			return v;
		}
		
	    public Object marshal(Object v) {
	    	return v; 
	    }
	}
	
	@XmlRootElement/*(name="input")*/
	public static class InputWrapper {
		@XmlAnyElement
		@XmlMixed
		public AbstractInput input;
		@XmlTransient
		private Util util = new Util();
		@XmlTransient
		private String inputAsCanonicalXML;
		
		@Deprecated
		public InputWrapper() { }
		public InputWrapper(AbstractInput in) { 
			input = in;
		}
		/** 
		 * We use this equals(..) method to test whether two inputs are actually equal
		 * with respect to the target service and the input data which they define. For this
		 * purpose we cannot use the equals(..) methods of AbstractInput, NonConstantInput or
		 * similar, because those methods only test for equal 'id' attributes and whether two 
		 * inputs belong to the same aggregation request...
		 */
		@Override
		public boolean equals(Object o) {
			AbstractInput i = null;
			if(o instanceof AbstractInput)
				i = (AbstractInput)o;
			else if(o instanceof InputWrapper)
				i = ((InputWrapper)o).input;
			if(i == null) {
				return false;
			}
			if(input instanceof NonConstantInput) {
				if(!(i instanceof NonConstantInput)) {
					return false;
				}
				NonConstantInput nc1 = (NonConstantInput)input;
				NonConstantInput nc2 = (NonConstantInput)i;
				if(nc1.getServiceURL() != null && !nc1.getServiceURL().equals(nc2.getServiceURL())) {
					return false;
				}
				if(nc1.getServiceEPR() != null && !nc1.getServiceEPR().equals(nc2.getServiceEPR())) {
					return false;
				}
			}
			try {
				String c1 = getInputAsCanonicalXML();
				String c2 = null;
				if(o instanceof InputWrapper) {
					c2 = ((InputWrapper)o).getInputAsCanonicalXML();
				} else {
					c2 = new InputWrapper((AbstractInput)i).getInputAsCanonicalXML();
				}
				if(c1 == null && c2 != null)
					return false;
				else if(c1 != null && !c1.equals(c2))
					return false;
			} catch (Exception e) {
				logger.warn("Unable to determine equals(..) for two input objects.", e);
				return false;
			}
			return true;
		}
		private String getInputAsCanonicalXML() throws Exception {
			if(inputAsCanonicalXML == null) {
				Element e1 = null;
				try {
					e1 = input.getTheContentAsElement();
				} catch (Exception e) {
					Object content = input.getTheContent();
					if(content instanceof String) {
						content = ((String)content).trim();
					}
					e1 = util.xml.toElement("<input><![CDATA[" + content + "]]></input>");
				}
				if(e1 == null)
					return null;
				e1 = util.xml.cloneCanonical(e1);
				inputAsCanonicalXML = util.xml.toString(e1);
			}
			return inputAsCanonicalXML;
		}
		@Override
		public int hashCode() {
			int hc = 0;
			if(input instanceof NonConstantInput) {
				NonConstantInput nc = (NonConstantInput)input;
				if(nc.getServiceURL() != null) hc += nc.getServiceURL().hashCode();
				if(nc.getServiceEPR() != null) hc += nc.getServiceEPR().hashCode();
			}
			return hc;
		}
		@Override
		public String toString() {
			return "[W " + input + "]";
		}
	}
	
	@XmlRootElement(name="inputs")
	public static class RequestInputs {
		@XmlAnyElement
		@XmlMixed
		private List<AbstractInput> input = new LinkedList<AbstractInput>();
		
		private static final boolean CHECK_INPUT_ID_UNIQUENESS = false;
		
		public RequestInputs() { 
			this.input = new LinkedList<AbstractInput>();
		}
		public RequestInputs(List<? extends AbstractInput> inputs) {
			this.input = new LinkedList<AbstractInput>();
			for(AbstractInput i : inputs)
				this.input.add(i);
		}
		
		@XmlTransient
		public List<AbstractInput> getInputsCopy() {
			if(input != null) {
				for(int i = 0; i < input.size(); i ++) {
					Object o = input.get(i);
					if((o instanceof String) && ((String)o).trim().isEmpty()) {
						logger.debug("Removing empty string from list of inputs...");
						input.remove(i--);
					}
				}
				return Collections.unmodifiableList(input);
			}
			return null;
		}
		
		public boolean addInput(AbstractInput i) {
			if(CHECK_INPUT_ID_UNIQUENESS) {
				for(AbstractInput existing : input) {
					if(existing.getUniqueID() != null && existing.getUniqueID().equals(i.getUniqueID())) {
						throw new RuntimeException("Cannot add input with same id to request:\n" + i + "\nexisting:\n" + existing);
					}
				}
			}
			return input.add(i);
		}

		public boolean addAllInputs(List<AbstractInput> i) {
			return input.addAll(i);
		}

		public void clearInputs() {
			input.clear();
		}
		
		@Override
		public String toString() {
			try {
				return XMLUtil.getInstance().toString(this);
			} catch (Exception e) {
				logger.warn("Unable to execute toString(..)", e);
				return null;
			}
		}
	}
	
	public static enum DependencyUpdateMode {
		once, always
	}
	



	public void copyFrom(AbstractInput toClone) {
		this.setExternalID(toClone.getExternalID());
		this.setUniqueID(toClone.getUniqueID());
		this.setContentType(toClone.getContentType());
		this.getContent().addAll(toClone.getContent());
		this.request = toClone.request;
		this.generatedInputID = toClone.generatedInputID;
	}
	
	public List<AbstractInput> generateInputs() throws Exception {
		//List<AbstractInput> result = new LinkedList<AbstractInput>();
		//result.add(this);
		//return result;
		
		List<AbstractInput> inputs = new LinkedList<AbstractInput>();
		int counter = 1;
		if(getTheContent() != null && 
				(getContentType().equalsIgnoreCase("xquery") || 
						getContentType().equalsIgnoreCase("waql") ||
						(this instanceof RequestInput && 
								((RequestInput)this).getType() == RequestType.HTTP_GET))) {
			String inputXQuery = null;
			if((getTheContent() instanceof Text)) {
				inputXQuery = ((Text)getTheContent()).getTextContent();
				inputXQuery = inputXQuery.trim();
				setTheContent(inputXQuery);
			} else if((getTheContent() instanceof String)) {
				inputXQuery = (String)getTheContent();
				inputXQuery = inputXQuery.trim();
				setTheContent(inputXQuery);
			} else if((getTheContent() instanceof Element)) {
				// nothing to do here
				inputs.add(this);
				return inputs;
			} else {
				Object theContent = getTheContent();
				throw new IllegalArgumentException("Unknown input type: " + theContent + (theContent != null ? (", class: " + theContent.getClass()) : ""));
			}
			String inputXQueryOld = inputXQuery;
			
			boolean error = false;
			try {
				/* first, replace all '&' by '&amp;' */
				inputXQueryOld = inputXQuery = QueryPreprocessor.replaceAmpersands(inputXQuery).trim();
				
				/* perform preprocessing (this replaces '$(...)' WAQL query constructs) */
				inputXQuery = QueryPreprocessor.preprocess(inputXQuery);
				
				/* then, re-replace all '&amp;' by '&' for HTTP GET inputs */
				if((this instanceof RequestInput && 
						((RequestInput)this).getType() == RequestType.HTTP_GET)) {
					inputXQuery = inputXQuery.replace("&amp;", "&");
				}
			} catch (Exception e) {
				error = true;
				logger.info("Unable to preprocess as WAQL input: " + e);
			}
			
			if(!error) {
				if(getContentType().equalsIgnoreCase("xquery") || 
						getContentType().equalsIgnoreCase("waql") || 
						(inputXQuery != null && !inputXQuery.equals(inputXQueryOld))) {
					Object result = null;
					try {
						result = XQueryProcessor.getInstance().execute(inputXQuery);
					} catch (Exception e) {
						if(inputXQuery.startsWith("<")) {
							logger.info("Unable to preprocess input " + this + ", cause: " + e);
						} else {
							try {
								inputXQuery = "<a>" + "<![CDATA[" + inputXQuery + "]]>" + "</a>/text()";
								result = XQueryProcessor.getInstance().execute(inputXQuery);
							} catch (Exception e2) {
								logger.info("Unable to preprocess input " + this + ", cause: " + e2);
							}
						}
					}
					if(result != null) {
						if(logger.isDebugEnabled()) logger.debug("Preprocessed input: " + result);
						if(!(result instanceof List<?>)) {
							result = Arrays.asList(result);
						}
						for(Object o : (List<?>)result) {
							AbstractInput temp = this.getClass().getConstructor(this.getClass()).newInstance(this);
							//RequestInput temp = new RequestInput(this);
							temp.generatedInputID = counter++;
							temp.setTheContent(o);
							inputs.add(temp);
						}
					} else {
						inputs.add(this);
					}
				} else {
					inputs.add(this);
				}
			}
		} else {
			inputs.add(this);
		}
		
		if(inputs.isEmpty()) {
			inputs.add(this);
		}
		return inputs;
	}
	
	
}
