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
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.adapters.XmlAdapter;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import org.apache.log4j.Logger;
import org.w3c.dom.Element;

import at.ac.tuwien.infosys.aggr.flow.FlowManager;
import at.ac.tuwien.infosys.aggr.flow.FlowManager.InputDataDependency;
import at.ac.tuwien.infosys.aggr.request.AbstractInput.RequestInputs;
import at.ac.tuwien.infosys.aggr.request.WAQLQuery.PreparationQuery;
import at.ac.tuwien.infosys.aggr.monitor.MonitoringSpecification;
import at.ac.tuwien.infosys.util.Configuration;
import at.ac.tuwien.infosys.aggr.util.DebugAssertion;
import at.ac.tuwien.infosys.util.Util;
import at.ac.tuwien.infosys.aggr.util.DebugAssertion.AssertionEvaluationTarget;
import at.ac.tuwien.infosys.aggr.util.DebugAssertion.AssertionEvaluationTime;
import at.ac.tuwien.infosys.aggr.waql.DataDependency;
import at.ac.tuwien.infosys.aggr.waql.PreprocessorEngine;
import at.ac.tuwien.infosys.aggr.waql.PreprocessorFactory;
import at.ac.tuwien.infosys.util.Identifiable;
import at.ac.tuwien.infosys.util.xml.XMLUtil;

@XmlSeeAlso({
	RequestInput.class,
	ConstantInput.class
})
@XmlRootElement(name=AggregationRequest.NAME_ELEMENT, namespace=Configuration.NAMESPACE)
@XmlJavaTypeAdapter(AggregationRequest.InputsPreparationXmlAdapter.class)
public class AggregationRequest implements Identifiable {
	
	private static final Logger logger = Util.getLogger(AggregationRequest.class);
	public static final String NAME_ELEMENT = "aggregate";

	@XmlTransient
	private long internalID;
	
	@XmlElement(name="requestID")
	private String requestID;
	@XmlElement(name="topologyID")
	private String topologyID;
	@XmlElement
	private RequestInputs inputs;
	@XmlElement
	private WAQLQuery queries;
	@XmlElement(name="debug")
	private Boolean debug;
	@XmlElement(name="incremental")
	private Boolean incremental;
	/** minimum duration (in ms) between two updates sent to the client. */
	@XmlElement(name="minNotifyInterval")
	private Long minNotifyInterval; 
	@XmlElement(name="assertion")
	private List<DebugAssertion> assertions = new LinkedList<DebugAssertion>();
	@XmlElement(name="monitor")
	private MonitoringSpecification monitor;
	@XmlElement(name="timeout")
	private Boolean timeout;
	@XmlElement(name="creatorUsername")
	private String creatorUsername;
	/**
	 * Circumvent the automatic aggregator selection and directly assign
	 * the URL of the aggregator responsible for handling this request.
	 */
	@XmlElement(name="assignedAggregator")
	private String assignedAggregator;

	@XmlTransient
	private boolean inputsGenerated;
	@XmlTransient
	private FlowManager manager;
	@XmlTransient
	private boolean inputsInitialized = false;
	@XmlTransient
	private Map<AbstractInput,List<InputDataDependency>> staticDependenciesFromTo = new HashMap<AbstractInput, List<InputDataDependency>>();
	
	public static class InputsPreparationXmlAdapter extends XmlAdapter<Object, Object> {
		private Util util = new Util();
		public Object marshal(Object o) throws Exception {
			if(o == null)
				return null;
			return util.xml.toElement(o);
		}
		public Object unmarshal(Object v) throws Exception {
			Element e = (Element)v;
			if(!e.getLocalName().equals(AggregationRequest.NAME_ELEMENT)) {
				e = util.xml.changeRootElementName(e, "foons1:" + AggregationRequest.NAME_ELEMENT + 
						" xmlns:foons1=\"" + Configuration.NAMESPACE + "\"");
			}
			Object o = util.xml.toJaxbObject(e);
			if(o instanceof AggregationRequest) {
				AggregationRequest r = (AggregationRequest)o;
				for(AbstractInput i : r.getAllInputs()) {
					//System.out.println("======> setting i.request = ");
					i.request = r;
				}
			}
			return o;
		}
	}

	public AggregationRequest(long internalID, String requestID, String topologyID, RequestInputs inputs, WAQLQuery queries) {
		this.internalID = internalID;
		this.setRequestID(requestID);
		this.setTopologyID(topologyID);
		this.inputs = inputs;
		this.queries = queries;
	}

	public AggregationRequest() { 
		this(-1, null, null, null, null);
	}
	
	public AggregationRequest(AggregationRequest toCopy) {
		this.inputs = new RequestInputs();
		this.queries = new WAQLQuery();
		if(toCopy == null) {
			return;
		}
		this.debug = toCopy.debug;
		this.inputs.addAllInputs(toCopy.getAllInputs());
		this.inputsGenerated = toCopy.inputsGenerated;
		this.internalID = toCopy.internalID;
		this.monitor = toCopy.monitor;
		this.creatorUsername = toCopy.creatorUsername;
		this.getQueries().getPreparationQueries().addAll(toCopy.getQueries().getPreparationQueries());
		this.getQueries().getTerminationQueries().addAll(toCopy.getQueries().getTerminationQueries());
		this.getQueries().setQuery(toCopy.getQueries().getQuery());
		this.getQueries().setIntermediateQuery(toCopy.getQueries().getIntermediateQuery());
		this.setTopologyID(toCopy.getTopologyID());
		this.setRequestID(toCopy.getRequestID());
		this.assertions.addAll(toCopy.assertions);
	}
	
	
	public long getIdentifier() {
		return internalID;
	}

	public List<PreparationQuery> getMatchingQueries(AbstractInput input) {
		List<PreparationQuery> result = new LinkedList<PreparationQuery>();
		if(getQueries() != null) {
			for(PreparationQuery q : getQueries().getPreparationQueries())
				if(q.isForInput(input.getExternalID()))
					result.add(q);
		}
		return result;
	}
	
	public List<DebugAssertion> getMatchingAssertions(AbstractInput input, 
			AssertionEvaluationTime time, AssertionEvaluationTarget target) {
		List<DebugAssertion> result = new LinkedList<DebugAssertion>();
		for(DebugAssertion a : assertions) {
			boolean matches = (a.getInputID().equals("E") && input == null) 
								|| (input != null && input.getExternalID().equals(a.getInputID()));
			matches &= a.getAssertTarget() == target;
			matches &= a.getAssertTime() == time;
			if(matches && a.getExpression() != null && !a.getExpression().trim().isEmpty())
				result.add(a);
		}
		return result;
	}

	public boolean containsEventingOrMonitoringInput() {
		if(inputs == null) 
			return false;
		for(AbstractInput input : inputs.getInputsCopy()) {
			if(input instanceof EventingInput)
				return true;
			if(input instanceof RequestInput) {
				Double interval = ((RequestInput)input).getInterval();
				if(interval != null && interval > 0)
					return true;
			}
		}
		return false;
	}
	
	public AggregationRequest deepCopy() throws Exception {
		Util util = new Util();
		return util.xml.toJaxbObject(AggregationRequest.class, util.xml.toElement(this));
	}
	
	@XmlTransient
	public Boolean isIncremental() {
		return incremental != null && incremental;
	}
	public void setIncremental(Boolean incremental) {
		this.incremental = incremental;
	}
	@XmlTransient
	public boolean isDebug() {
		return debug != null && debug;
	}
	public void setDebug(Boolean debug) {
		this.debug = debug;
	}
	public boolean doMonitor() {
		return getMonitor() != null;
	}
	@XmlTransient
	public MonitoringSpecification getMonitor() {
		return monitor;
	}
	public void setMonitor(MonitoringSpecification monitor) {
		this.monitor = monitor;
	}
	@XmlTransient
	public Long getMinNotifyInterval() {
		return minNotifyInterval;
	}
	public void setMinNotifyInterval(Long minNotifyInterval) {
		this.minNotifyInterval = minNotifyInterval;
	}
	@XmlTransient
	public FlowManager getManager() {
		return manager;
	}
	public void setManager(FlowManager manager) {
		this.manager = manager;
	}
	@XmlTransient
	public List<DebugAssertion> getAssertions() {
		return assertions;
	}
	public void setAssertions(List<DebugAssertion> assertions) {
		this.assertions = assertions;
	}
	@XmlTransient
	public RequestInputs getInputs() {
		if(!inputsInitialized) {
			inputsInitialized = true;
			if(inputs == null)
				inputs = new RequestInputs();
			for(AbstractInput i : inputs.getInputsCopy())
				i.request = this;
		}
		return inputs;
	}
	public void setInputs(RequestInputs inputs) {
		this.inputs = inputs;
	}
	
	public List<AbstractInput> getAllInputs() {
		if(inputs == null) {
			inputs = new RequestInputs();
		}
		List<AbstractInput> inputsCopy = inputs.getInputsCopy();
		for(AbstractInput i : inputsCopy) {
			if(i.request == null)
				i.request = this;
		}
		return inputsCopy;
	}

	public int getMaxNumericExternalInputID() {
		int max = -1;
		if(inputs != null) {
			for(AbstractInput i : inputs.getInputsCopy()) {
				if(i.getExternalID() != null) {
					int temp = -1;
					try {
						temp = Integer.parseInt(i.getExternalID());
						if(temp > max)
							max = temp;
					} catch (Exception e) { }
				}
			}
		}
		return max;
	}
	
	public AbstractInput getInputByID(String ID) {
		List<AbstractInput> ins = getInputsByID(ID);
		if(ins.isEmpty())
			return null;
		if(ins.size() > 1)
			logger.warn("Expected only a single input with ID '" + ID + "', but found " + ins.size());
		return ins.get(0);
	}
		
	public List<AbstractInput> getInputsByID(String ID) {
		List<AbstractInput> list = new LinkedList<AbstractInput>();
		for(AbstractInput in : inputs.getInputsCopy()) {
			if(in.getExternalID() != null && in.getExternalID().equals(ID))
				list.add(in);
		}
		return list;
	}
	
	public List<DataDependency> getAllDataDependencies(AbstractInput i) {
		List<DataDependency> result = new LinkedList<DataDependency>();
		try {
			result.addAll(i.getDataDependencies());
			for(PreparationQuery prep : getMatchingQueries(i)) {
				String query = prep.getValue();
				if(query != null && !query.trim().isEmpty()) {
					PreprocessorEngine engine = PreprocessorFactory.getEngine();
					try {
						engine.parse(new ByteArrayInputStream(query.getBytes()));
					} catch (Exception e) {
						logger.info("Unable to parse preprocessing query '" + query + "'", e);
						throw e;
					}
					result.addAll(engine.getDependencies());
				}
			}
		} catch (Exception e) {
			if(logger.isDebugEnabled()) logger.debug("Unable to extract data dependencies.", e);
			if(e instanceof RuntimeException)
				throw (RuntimeException)e;
			throw new RuntimeException(e);
		}
		return result;
	}
	
	public List<InputDataDependency> getInputsStaticallyDependentFrom(AbstractInput providing) {
		List<InputDataDependency> result = staticDependenciesFromTo.get(providing);
		if(result != null) {
			return result;
		}
		result = new LinkedList<InputDataDependency>();
		staticDependenciesFromTo.put(providing, result);
		for(AbstractInput depending : getAllInputs()) {
			for(DataDependency d : depending.getDataDependencies()) {
				if(("" + d.getIdentifier()).equals(providing.getExternalID())) {
					InputDataDependency dep = new InputDataDependency();
					dep.from = providing;
					dep.to = depending;
					dep.xpath = d.getRequest();
					result.add(dep);
				}
			}
			for(PreparationQuery prep : getMatchingQueries(depending)) {
				String query = prep.getValue();
				if(query != null && !query.trim().isEmpty()) {
					PreprocessorEngine engine = PreprocessorFactory.getEngine();
					try {
						engine.parse(new ByteArrayInputStream(query.getBytes()));
					} catch (Exception e) {
						logger.info("Unable to parse preprocessing query '" + query + "'", e);
						throw new RuntimeException(e);
					}
					for(DataDependency d : engine.getDependencies()) {
						if(d.getIdentifier() != null) {
							InputDataDependency dep = new InputDataDependency();
							dep.from = providing;
							dep.xpath = d.getRequest();
							dep.to = getInputsByID("" + d.getIdentifier()).get(0); // expecting to get exactly one input here..
							result.add(dep);
						}
					}
				}
			}
		}
		return result;
	}
	
	public List<SavedQueryInput> getCompositeQueryInputs() {
		List<SavedQueryInput> result = new LinkedList<SavedQueryInput>();
		for(AbstractInput i : getAllInputs()) {
			if(i instanceof SavedQueryInput) {
				result.add((SavedQueryInput)i);
			}
		}
		return result;
	}
	
	public Map<String, AbstractInput> getInputsMap() {
		Map<String, AbstractInput> result = new HashMap<String, AbstractInput>();
		for(AbstractInput i : getAllInputs()) {
			if(result.containsKey(i.getExternalID())) {
				throw new RuntimeException("Input IDs in request not unique: " + this);
			}
			result.put(i.getExternalID(), i);
		}
		return result;
	}
	
	public AggregationRequest cloneCanonical() throws Exception {
		Util util = new Util();
		return util.xml.toJaxbObject(AggregationRequest.class, 
				util.xml.cloneCanonical(util.xml.toElement(this)));
	}
	
	public boolean equalsContent(AggregationRequest other) {
		return false;
	}

	public void setInternalID(long internalID) {
		this.internalID = internalID;
	}

	public AbstractInput getSingleInput() {
		List<AbstractInput> in = getAllInputs();
		if(in.isEmpty())
			throw new RuntimeException("Found no input in aggregation request " + this);
		if(in.size() > 1)
			logger.warn("Expected a single input for aggregation request, but found " + in.size() + ": " + this);
		return in.get(0);
	}

	@Override
	public int hashCode() {
		if(getRequestID() != null)
			return getRequestID().hashCode();
		return super.hashCode();
	}

	@Override
	public boolean equals(Object o) {
		if(!(o instanceof AggregationRequest))
			return false;
		String r1 = ((AggregationRequest)o).getRequestID();
		String r2 = getRequestID();
		return r1 != null && r2 != null && r1.equals(r2);
	}

	@Override
	public String toString() {
		try {
			XMLUtil util = new XMLUtil();
			Element el = util.toElement(this);
			if(el == null)
				return null;
			return util.toString(el);
		} catch (Exception e) {
			return null;
		}
	}
	public String toString(String elementName) throws Exception {
		XMLUtil util = new XMLUtil();
		Element el = util.toElement(this);
		el = util.changeRootElementName(el, elementName);
		return util.toString(el);
	}
	@XmlTransient
	public String getRequestID() {
		return requestID;
	}
	public void setRequestID(String requestID) {
		this.requestID = requestID;
	}
	@XmlTransient
	public String getTopologyID() {
		return topologyID;
	}
	public void setTopologyID(String topologyID) {
		this.topologyID = topologyID;
	}
	@XmlTransient
	public WAQLQuery getQueries() {
		if(queries == null) {
			queries = new WAQLQuery();
		}
		return queries;
	}
	@XmlTransient
	public Boolean getTimeout() {
		return timeout;
	}
	public void setTimeout(Boolean timeout) {
		this.timeout = timeout;
	}
	@XmlTransient
	public String getCreatorUsername() {
		return creatorUsername;
	}
	public void setCreatorUsername(String creatorUsername) {
		this.creatorUsername = creatorUsername;
	}
	@XmlTransient
	public String getAssignedAggregator() {
		return assignedAggregator;
	}
	public void setAssignedAggregator(String assignedAggregator) {
		this.assignedAggregator = assignedAggregator;
	}

}
