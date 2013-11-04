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

import java.io.StringReader;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;
import org.jdom.DefaultJDOMFactory;
import org.jdom.JDOMFactory;
import org.jdom.input.DOMBuilder;
import org.jdom.input.SAXBuilder;
import org.jdom.output.Format;
import org.jdom.output.Format.TextMode;
import org.jdom.output.XMLOutputter;
import org.vmguys.vmtools.utils.DiffElement;
import org.vmguys.vmtools.utils.JdomDifferenceFinder;
import org.w3c.dom.Element;

import at.ac.tuwien.infosys.aggr.request.AggregationResponse.DebugInfo;
import at.ac.tuwien.infosys.aggr.request.WAQLQuery.PreparationQuery;
import at.ac.tuwien.infosys.aggr.util.DebugAssertion;
import at.ac.tuwien.infosys.util.Util;
import at.ac.tuwien.infosys.aggr.util.DebugAssertion.AssertionEvaluationTarget;
import at.ac.tuwien.infosys.aggr.util.DebugAssertion.AssertionEvaluationTime;
import at.ac.tuwien.infosys.aggr.util.DebugAssertion.AssertionResult;
import at.ac.tuwien.infosys.aggr.xml.XQueryProcessor;
import at.ac.tuwien.infosys.aggr.xml.XQueryProcessor.EmptyQueryResultException;

public class AggregationResponseConstructor {

	private static final Logger logger = Util.getLogger(AggregationResponseConstructor.class);
	private static final int MAX_CACHE_SIZE = 20;

	private AggregationRequest request;
	private AggregationResponse response;
	private Object result;
	private boolean requiresNewConstructionOfResultElement;
	private final Map<Object, List<Element>> results = new HashMap<Object, List<Element>>();
	private Util util;
	private XQueryProcessor xquery = XQueryProcessor.getInstance();
	
	private int cacheSize = 1; 	// to effectively overwrite each event previously 
								// received from an event stream, set the cache size to 1. 
	private boolean useIncrementalResult = false;	// TODO: make configurable..

	public AggregationResponseConstructor(AggregationRequest request) throws Exception {
		this(request, true);
	}

	public AggregationResponseConstructor(AggregationRequest request, boolean doOverwriteOldResults) throws Exception {
		if(doOverwriteOldResults)
			cacheSize = 1;
		else
			cacheSize = MAX_CACHE_SIZE;
		this.request = request;
		this.util = new Util();
		this.response = new AggregationResponse(request);
	}

	public AggregationResponse getTotalResult() {
		return getTotalResult(true);
	}
	public AggregationResponse getTotalResult(boolean applyFinalizationQuery) {
		return getTotalResult(null, applyFinalizationQuery);
	}
	public AggregationResponse getTotalResult(DebugInfo debug, boolean applyFinalizationQuery) {
		try {
			if (requiresNewConstructionOfResultElement) {
				constructResult(debug, applyFinalizationQuery);
			}
		} catch (Exception e) {
			logger.warn("Unable to construct total result element.", e);
		}
		response.setResult(result);
		return response;
	}

	public Element getTotalResultElement(boolean applyFinalizationQuery) throws Exception {
		return getTotalResultElement(null, applyFinalizationQuery);
	}
	public Element getTotalResultElement(DebugInfo debug, boolean applyFinalizationQuery) throws Exception {
		if (requiresNewConstructionOfResultElement) {
			constructResult(debug, applyFinalizationQuery);
		}
		if(result instanceof Element)
			return (Element)result;
		return null;
	}

	public Element getIncrementResultElement(boolean applyFinalizationQuery) throws Exception {
		Element oldResult = (Element)result;
		return getIncrementResultElement(applyFinalizationQuery, oldResult);
	}
	
	public Element getIncrementResultElement(boolean applyFinalizationQuery, Element oldResult) throws Exception {
		if (requiresNewConstructionOfResultElement || applyFinalizationQuery) {
			constructResult(null, applyFinalizationQuery);
		}
		if(result instanceof Element) {
			if(oldResult != null && useIncrementalResult) {
				DOMBuilder b = new DOMBuilder();
				org.jdom.Element e1 = b.build(oldResult);
				org.jdom.Element e2 = b.build((Element)result);
				SAXBuilder sb = new SAXBuilder();
				
				sb.setFactory((JDOMFactory) new DefaultJDOMFactory());
				//sb.setFactory(new JDOMFactory());
				e1 = sb.build(new StringReader(util.xml.toString(oldResult))).getRootElement();
				e2 = sb.build(new StringReader(util.xml.toString(result))).getRootElement();
				
				org.jdom.Element diffResult = new DiffElement("diff"); //b.build(util.toElement("<diffs/>"));
				JdomDifferenceFinder finder = new JdomDifferenceFinder();
				finder.findDifferences(e1, e2, diffResult);
				
				Format f = Format.getRawFormat();
				//XMLOutputter xmlo = new XMLOutputter("  ", true);
				XMLOutputter xmlo = new XMLOutputter(f);
				f.setTextMode(TextMode.NORMALIZE);
				f.setIndent("  ");
				String result = xmlo.outputString(diffResult);
				return util.xml.toElement(result);
			}
		}
		return getTotalResultElement(null, applyFinalizationQuery);
	}

	public Element addInvocationResponse(final AbstractInput inResponseTo, 
			Element response, final DebugInfo debugInfoToFill,
			boolean applyPrepQueries) throws Exception {

		synchronized (results) {
			if (!results.containsKey(inResponseTo)) {
				results.put(inResponseTo, new LinkedList<Element>());
			}
			requiresNewConstructionOfResultElement = true;
		}

		// check response-specific assertions BEFORE preparation query
		AbstractInput input = (AbstractInput)inResponseTo;
		checkAssertions(request, input, response, AssertionEvaluationTime.B, debugInfoToFill);

		// apply preparation queries
		boolean prepApplied = false;
		if(applyPrepQueries) {
			Element responseAfter = applyPrepQueries(inResponseTo, response);
			prepApplied = response != responseAfter;
			response = responseAfter;
		}
		if(logger.isDebugEnabled()) logger.debug("Adding invocation result (prep. apply: " + applyPrepQueries + "/" + prepApplied + ") " + response + " in response to " + inResponseTo);

		// check response-specific assertions AFTER preparation query
		checkAssertions(request, input, response, AssertionEvaluationTime.A, debugInfoToFill);
		
		// add response to results list
		synchronized (results) {
			List<Element> target = results.get(inResponseTo);
			target.add(response);
			while(target.size() > cacheSize) {
				target.remove(0);
			}
		}
		
		if(debugInfoToFill != null && (inResponseTo instanceof AbstractInput)) {
			// check assertions specific to the total result BEFORE finalization query
			constructResult(debugInfoToFill, false);
			if(result instanceof Element) {
				checkAssertions(request, null, (Element)result, AssertionEvaluationTime.B, debugInfoToFill);
			}
			// check assertions specific to the total result AFTER finalization query
			constructResult(debugInfoToFill, true);
			if(result instanceof Element) {
				checkAssertions(request, null, (Element)result, AssertionEvaluationTime.A, debugInfoToFill);
			}
		}
		
		return response;
	}
	
	public Element applyPrepQueries(AbstractInput input, Element inputResult) throws Exception {
		for(PreparationQuery q : request.getQueries().getPreparationQueries()) {
			if(q.isForInput(input.getExternalID())) {
				Map<String,Object> variables = new HashMap<String, Object>();
				variables.put("request", util.xml.toElement(input));
				inputResult = (Element)xquery.execute(inputResult, q.getValue(), true, variables);
			}
		}
		return inputResult;
	}

	public void setTotalResult(Object o) {
		result = o;
		response.setResult(o);
		requiresNewConstructionOfResultElement = false;
	}

	public void addDebugInfo(DebugInfo info) {
		if(!response.getDebug().contains(info))
			response.getDebug().add(info);
	}

	private synchronized void constructResult(
			DebugInfo debug, boolean applyFinalizationQuery) throws Exception {
		
		if(result != null && !(result instanceof Element)) {
			logger.warn("Unexpected result type: " + result.getClass() + " - " + result);
			return;
		}

		result = util.xml.toElement("<result/>");
		synchronized (results) {
			for (List<Element> list : results.values()) {
				for (Element e : list) {
					util.xml.appendChild((Element) result, (Element) e);
				}
			}
			// apparently, we need to do this, although it may have a serious performance impact..
			result = util.xml.clone((Element)result);
		}
		
		if(applyFinalizationQuery) {
			if(debug != null) {
				debug.setResultBeforeQuery(result);
			}
			
			String query = request.getQueries().getQuery();
			if(request.getQueries().getIntermediateQuery() != null)
				query = request.getQueries().getIntermediateQuery();
			if(logger.isDebugEnabled()) logger.info("Applying finalization query to element (" + results.size() + " partial results): " + util.xml.toString((Element)result));

			Object resultNew = applyQuery((Element)result, query);

			if(resultNew instanceof Exception) {
				result = resultNew;
			} else if(resultNew != null && !(resultNew instanceof Element)) {
				Exception e = new IllegalArgumentException("The finalization query needs to generate a single XML Element, we got: " + result.getClass());
				result = e;
			} else if(resultNew instanceof Element) {
				result = util.xml.clone((Element)resultNew); /* we have to make another clone() here, because afterwards,
					when the final result is returned by the aggregator, JAX-WS/JAXB
					are sometimes unable to parse Saxon XML elements.. :/ */

				if(debug != null) {
					debug.setResultAfterQuery(result);
				}

			}
		}

		if(result != null)
			requiresNewConstructionOfResultElement = false;
		if(!applyFinalizationQuery)
			requiresNewConstructionOfResultElement = true;
	}

	private Object applyQuery(Element e, String q) throws Exception {
		if(q != null && !q.trim().isEmpty()) {
			try {
				e = util.xml.clone(e);
				return (Element)xquery.execute(e, q, true);
			} catch (EmptyQueryResultException e2) {
				if(logger.isDebugEnabled()) logger.debug("Query '" + q.trim() + "' yielded a NULL result. Context element: " + e, e2);
				return e2;
			} catch (Exception e2) {
				logger.info("Could not execute query '" + q.trim() + "'. (This MAY be non-critical, e.g., in the case of eventing if not all required events have been received so far.) Context element: " + e, e2);
				return e2;
			}
		}
		return e;
	}

	public boolean checkAssertions(AggregationRequest request, AbstractInput input, 
			Element toCheck, AssertionEvaluationTime time, DebugInfo debug) throws Exception {

		AssertionEvaluationTarget target = input == null ? AssertionEvaluationTarget.T : AssertionEvaluationTarget.R;

		if (debug == null)
			debug = new DebugInfo();

		boolean someMatching = false;
		for (DebugAssertion ass : request.getMatchingAssertions(input, time, target)) {
			someMatching = true;
			try {
				if (!xquery.evaluatesToTrue(ass.getExpression(), toCheck)) {
					debug.getAssertionResults().add(new AssertionResult(ass.getID(),
							ass.getExpression(), false));
					logger.info("Assertion failed: " + ass.getExpression());
				} else {
					debug.getAssertionResults().add(new AssertionResult(ass.getID(),
							ass.getExpression(), true));
				}
			} catch (Exception e) {
				debug.getAssertionResults().add(new AssertionResult(ass.getID(), ass.getExpression(), false));
				logger.info("Assertion could not be evaluated: "
						+ ass.getExpression() + "; element to check: " + util.xml.toString(toCheck), e);
			}
		}
		return someMatching;
	}

	public AggregationRequest getRequest() {
		return request;
	}

	public List<DebugInfo> getDebugInfos() {
		return response.getDebug();
	}

}