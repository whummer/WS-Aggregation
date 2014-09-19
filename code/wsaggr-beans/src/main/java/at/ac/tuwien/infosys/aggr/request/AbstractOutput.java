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
import io.hummer.util.xml.XMLUtil;

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

import at.ac.tuwien.infosys.aggr.request.AbstractInput.DependencyUpdateMode;
import at.ac.tuwien.infosys.monitoring.config.LoggerOutput;
import at.ac.tuwien.infosys.monitoring.config.MonitoringConfigSubscriptions;
import at.ac.tuwien.infosys.monitoring.config.NodeConfigChangeOutput;
import at.ac.tuwien.infosys.monitoring.config.NodeConfigOutput;
import at.ac.tuwien.infosys.monitoring.config.NonConstantOutput;
import at.ac.tuwien.infosys.monitoring.config.SOAPEventOutput;

@XmlSeeAlso({ 
	EventingOutput.class, 
	SOAPEventOutput.class,
	NodeConfigChangeOutput.class,
	LoggerOutput.class,
	NodeConfigOutput.class
})
@XmlJavaTypeAdapter(AbstractOutput.Adapter.class)
@XmlType(name = "abstractOutput")
public abstract class AbstractOutput {

	private static final Logger LOGGER = Util.getLogger(AbstractOutput.class);

	/** the parent request object that this output belongs to */
	@XmlTransient
	public int generatedOutputID = 0;

	private final Util util = new Util();

	/**
	 * ID used for correlation with the preparation queries.
	 */
	@XmlAttribute(name = "id")
	private String externalID;

	@XmlElement(name=MonitoringConfigSubscriptions.NAME_ELEMENT)
	private MonitoringConfigSubscriptions subscriptions;
	
	@XmlAttribute
	public String contentType = "text/xml";
	@XmlAttribute
	public DependencyUpdateMode updateDependencies = DependencyUpdateMode.always;
	@XmlAnyElement
	@XmlMixed
	public final List<Object> content = new LinkedList<Object>();

	@XmlTransient
	public String getExternalID() {
		return externalID;
	}
	public void setExternalID(String externalID) {
		this.externalID = externalID;
	}
	
	@XmlTransient
	public MonitoringConfigSubscriptions getSubscriptions() {
		return subscriptions;
	}
	public void setSubscriptions(MonitoringConfigSubscriptions subscriptions) {
		this.subscriptions = subscriptions;
	}
	
	public static class Adapter extends XmlAdapter<Object, Object> {
		public static Util util = new Util();

		private static Map<String, Class<?>> MAPPING = new HashMap<String, Class<?>>();
		
		static {
			MAPPING.put(EventingOutput.JAXB_ELEMENT_NAME, EventingOutput.class);
			MAPPING.put(SOAPEventOutput.JAXB_ELEMENT_NAME, SOAPEventOutput.class);
			MAPPING.put(LoggerOutput.JAXB_ELEMENT_NAME, LoggerOutput.class);
			MAPPING.put(NodeConfigOutput.JAXB_ELEMENT_NAME, NodeConfigOutput.class);
			MAPPING.put(NodeConfigChangeOutput.JAXB_ELEMENT_NAME, NodeConfigChangeOutput.class);
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
				LOGGER.warn("Unable to unmarshal JAXB object.", e);
			}
			return v;
		}

		public Object marshal(Object v) {
			return v;
		}
	}

	@XmlRootElement
	/* (name="output") */
	public static class OutputWrapper {
		@XmlAnyElement
		@XmlMixed
		public AbstractOutput output;
		@XmlTransient
		private Util util = new Util();
		@XmlTransient
		private String outputAsCanonicalXML;

		@Deprecated
		public OutputWrapper() {
		}

		public OutputWrapper(AbstractOutput in) {
			output = in;
		}

		/**
		 * We use this equals(..) method to test whether two outputs are
		 * actually equal with respect to the target service and the output data
		 * which they define. For this purpose we cannot use the equals(..)
		 * methods of AbstractOutput, NonConstantOutput or similar, because
		 * those methods only test for equal 'id' attributes and whether two
		 * outputs belong to the same aggregation request...
		 */
		@Override
		public boolean equals(Object o) {
			AbstractOutput i = null;
			if (o instanceof AbstractOutput)
				i = (AbstractOutput) o;
			else if (o instanceof OutputWrapper)
				i = ((OutputWrapper) o).output;
			if (i == null) {
				return false;
			}
			if (output instanceof NonConstantOutput) {
				if (!(i instanceof NonConstantOutput)) {
					return false;
				}
				NonConstantOutput nc1 = (NonConstantOutput) output;
				NonConstantOutput nc2 = (NonConstantOutput) i;
				if (nc1.serviceURL != null
						&& !nc1.serviceURL.equals(nc2.serviceURL)) {
					return false;
				}
			}
			try {
				String c1 = getOutputAsCanonicalXML();
				String c2 = null;
				if (o instanceof OutputWrapper) {
					c2 = ((OutputWrapper) o).getOutputAsCanonicalXML();
				} else {
					c2 = new OutputWrapper((AbstractOutput) i)
							.getOutputAsCanonicalXML();
				}
				if (!c1.equals(c2))
					return false;
			} catch (Exception e) {
				LOGGER.error(
						"Unable to determine equals(..) for two output objects.",
						e);
				return false;
			}
			return true;
		}

		public String getOutputAsCanonicalXML() throws Exception {
			if (outputAsCanonicalXML == null) {
				Element e1 = output.getTheContentAsElement();
				e1 = util.xml.cloneCanonical(e1);
				outputAsCanonicalXML = util.xml.toString(e1);
			}
			return outputAsCanonicalXML;
		}

		@Override
		public int hashCode() {
			int hc = 0;
			if (output instanceof NonConstantOutput) {
				NonConstantOutput nc = (NonConstantOutput) output;
				if (nc.serviceURL != null)
					hc += nc.serviceURL.hashCode();
			}
			return hc;
		}

		@Override
		public String toString() {
			return "[W " + output + "]";
		}
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof AbstractOutput))
			return false;
		AbstractOutput r = (AbstractOutput) o;
		boolean eq = (r.externalID == null && externalID == null)
				|| (r.externalID != null && r.externalID.equals(externalID));
		return eq;
	}

	@Override
	public int hashCode() {
		int result = 0;
		result += (externalID == null) ? 0 : externalID.hashCode();
		return result;
	}

	@XmlRootElement(name = "outputs")
	public static class RequestOutputs {
		@XmlAnyElement
		@XmlMixed
		private List<AbstractOutput> output;

		public RequestOutputs() {
			this.output = new LinkedList<AbstractOutput>();
		}

		public RequestOutputs(List<? extends AbstractOutput> outputs) {
			this.output = new LinkedList<AbstractOutput>();
			for (AbstractOutput i : outputs)
				this.output.add(i);
		}

		@XmlTransient
		public List<AbstractOutput> getOutputsCopy() {
			if (output != null) {
				for (int i = 0; i < output.size(); i++) {
					Object o = output.get(i);
					if ((o instanceof String) && ((String) o).trim().isEmpty()) {
						LOGGER.debug("Removing empty string from list of outputs...");
						output.remove(i--);
					}
				}
				return Collections.unmodifiableList(output);
			}
			return null;
		}

		public boolean addOutput(AbstractOutput i) {
			return output.add(i);
		}

		public boolean addAllOutputs(List<AbstractOutput> i) {
			return output.addAll(i);
		}

		public void clearOutputs() {
			output.clear();
		}

		@Override
		public String toString() {
			try {
				return XMLUtil.getInstance().toString(this);
			} catch (Exception e) {
				LOGGER.warn("Unexpected error.", e);
				return null;
			}
		}
	}
	
	public abstract <T extends AbstractOutput> T copy() throws Exception;

	public void copyFrom(AbstractOutput toClone) {
		this.externalID = toClone.externalID;
		this.contentType = toClone.contentType;
		this.content.addAll(toClone.content);
		this.generatedOutputID = toClone.generatedOutputID;
	}

	public List<AbstractOutput> generateOutputs() throws Exception {
		// List<AbstractOutput> result = new LinkedList<AbstractOutput>();
		// result.add(this);
		// return result;

		List<AbstractOutput> outputs = new LinkedList<AbstractOutput>();
		outputs.add(this);

		return outputs;
	}

	@SuppressWarnings("all")
	public <T extends AbstractOutput> T deepCopy() throws Exception {
		return (T) util.xml.toJaxbObject(getClass(), util.xml.toElement(this));
	}

	@XmlTransient
	public void setTheContent(Object o) {
		content.clear();
		content.add(o);
	}

	public Object getTheContent() {
		for (int i = 0; i < content.size(); i++) {
			Object o = content.get(i);
			if (o instanceof Text) {
				Text t = (Text) o;
				if (t.getTextContent().trim().equals(""))
					content.remove(i--);
			} else if (o instanceof String) {
				if (((String) o).trim().equals(""))
					content.remove(i--);
			}
		}

		if (content.size() == 1)
			return content.get(0);
		else if (content.size() == 0)
			return null;
		return content;
	}

	public Element getTheContentAsElement() throws Exception {
		Object o = getTheContent();
		if (o instanceof Element)
			return (Element) o;
		if (o instanceof List<?> && ((List<?>) o).size() == 1)
			return (Element) ((List<?>) o).get(0);
		if (o instanceof String)
			return util.xml.toElement((String) o);
		throw new Exception("Cannot convert to Element (class "
				+ (o != null ? o.getClass() : o) + "): " + o);
	}

	public AbstractOutput getFirstInListByExternalID(
			List<AbstractOutput> toSearch) {
		for (AbstractOutput in : toSearch) {
			if (in.externalID.equals(externalID)) {
				return in;
			}
		}
		return null;
	}

	public boolean searchInListByID(List<AbstractOutput> toSearch) {
		return getFirstInListByExternalID(toSearch) != null;
	}

	@Override
	public String toString() {
		try {
			return util.xml.toString(this);
		} catch (Exception e) {
			LOGGER.warn("Could not convert output object to String.", e);
			return null;
		}
	}

}
