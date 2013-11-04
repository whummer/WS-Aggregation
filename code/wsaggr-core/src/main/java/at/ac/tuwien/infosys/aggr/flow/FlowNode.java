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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import at.ac.tuwien.infosys.aggr.waql.DataDependency;
import at.ac.tuwien.infosys.aggr.waql.PreprocessorEngine;
import at.ac.tuwien.infosys.aggr.waql.PreprocessorFactory;
import org.apache.log4j.Logger;
import org.w3c.dom.Element;

import at.ac.tuwien.infosys.aggr.flow.DataFlow.DataSourceType;
import at.ac.tuwien.infosys.aggr.request.AbstractInput;
import at.ac.tuwien.infosys.aggr.request.ConstantInput;
import at.ac.tuwien.infosys.ws.request.InvocationResult;
import at.ac.tuwien.infosys.aggr.request.NonConstantInput;
import at.ac.tuwien.infosys.aggr.request.RequestInput;
import at.ac.tuwien.infosys.aggr.request.AbstractInput.InputWrapper;
import at.ac.tuwien.infosys.aggr.request.WAQLQuery.PreparationQuery;
import at.ac.tuwien.infosys.ws.EndpointReference;
import at.ac.tuwien.infosys.util.xml.XMLUtil;
import at.ac.tuwien.infosys.aggr.xml.XPathProcessor;
import at.ac.tuwien.infosys.aggr.xml.XQueryProcessor;

public class FlowNode {

	private static final Integer ENGINE_KEY_CONTENT = 1;
	private static final Integer ENGINE_KEY_HEADERS = 2;
	private static final Integer ENGINE_KEY_SERVICE_URL = 3;
	private static final Integer ENGINE_KEY_SERVICE_EPR = 4;
	private static final String CDATA_PREFIX = "<a><![CDATA[";
	private static final String CDATA_SUFFIX = "]]></a>";
	
	private static final Logger logger = at.ac.tuwien.infosys.util.Util.getLogger(FlowNode.class);
	
	private final AbstractInput originalInput;
	private final List<InputWrapper> generatedInputs = new LinkedList<InputWrapper>();
	private AbstractInput modifiedInput;
	private int maxCacheSize;
	private List<PreparationQuery> preparationQueries = new LinkedList<PreparationQuery>();
	private List<InvocationResult> receivedResults;
	private XMLUtil xmlUtil = new XMLUtil();
	private List<DataFlow> dataDependencies;
	private Map<Object,PreprocessorEngine> engines;
	private Map<PreprocessorEngine,List<DataDependency>> engineToDependency;
	private Map<DataDependency,List<Object>> dependencyResults;
	private Map<DataDependency,List<Object>> dependencyResultsNew;
	private Map<PreprocessorEngine,String> originalQueries;
	private Set<PreprocessorEngine> queriesWrappedAsCData = new HashSet<PreprocessorEngine>();
	private List<Object> insertedInputs = new LinkedList<Object>();
	private Map<PreprocessorEngine,Class<?>> originalTypes;

	public static class DependencyUpdatedInfo {
		public String xpath;
		public AbstractInput receiver;
		public AbstractInput provider;
	}
	
	public FlowNode(AbstractInput originalInput, int maxCacheSize) {
		this.originalInput = originalInput;
		this.originalQueries = new HashMap<PreprocessorEngine, String>();
		this.originalTypes = new HashMap<PreprocessorEngine, Class<?>>();
		this.receivedResults = new LinkedList<InvocationResult>();
		this.dependencyResults = new HashMap<DataDependency, List<Object>>();
		this.dependencyResultsNew = new HashMap<DataDependency, List<Object>>();
		this.maxCacheSize = maxCacheSize;
		
		try {
			modifiedInput = originalInput.copy();
		} catch (Exception e1) {
			throw new RuntimeException(e1);
		}
		
		this.engines = new HashMap<Object,PreprocessorEngine>();
		Object c = originalInput.getTheContent();
		if(c != null) {
			String content = null;
			if(c instanceof String) {
				content = (String)c;
			} else if(c instanceof Element) {
				content = xmlUtil.toString((Element)c);
			} else {
				throw new RuntimeException("Unexpected request input content type: " + c.getClass());
			}
			PreprocessorEngine engine = constructEngineForWAQLQuery(content, c.getClass());
			if(engine != null)
				engines.put(ENGINE_KEY_CONTENT, engine);
		}
		if(originalInput instanceof RequestInput) {
			String serviceURL = ((RequestInput)originalInput).getServiceURL();
			if(serviceURL != null) {
				PreprocessorEngine engine = constructEngineForWAQLQuery(serviceURL, String.class);
				if(engine != null)
					engines.put(ENGINE_KEY_SERVICE_URL, engine);
			}
			EndpointReference epr = ((RequestInput)originalInput).getServiceEPR();
			if(epr != null) {
				try {
					PreprocessorEngine engine = constructEngineForWAQLQuery(
							xmlUtil.toString(epr), EndpointReference.class);
					if(engine != null)
						engines.put(ENGINE_KEY_SERVICE_EPR, engine);				
				} catch (Exception e) {
					throw new RuntimeException("Error initializing the WAQL preprocessor engine.", e);
				}
			}
		}
	}
	
	private PreprocessorEngine constructEngineForWAQLQuery(String query, Class<?> originalType) {
		try {
			query = query.trim();
			if(query.startsWith("http://") || query.startsWith("https://"))
				return null;
			
			if(logger.isDebugEnabled()) logger.debug("Constructing WAQL Engine for query: " + query);
			PreprocessorEngine engine = PreprocessorFactory.getEngine();
			try {
				engine.parse(new ByteArrayInputStream(query.getBytes()));				
			} catch (Exception e) {
				if(!query.startsWith("<")) {
					engine = PreprocessorFactory.getEngine();
					engine.parse(new ByteArrayInputStream((CDATA_PREFIX + query + CDATA_SUFFIX).getBytes()));
					queriesWrappedAsCData.add(engine);
				} else {
					throw e;
				}
			}
			if(logger.isDebugEnabled()) logger.debug(engine.getDependencies().size() + " dependencies found in query string: " + query);
			if(engine.getDependencies().isEmpty())
				return null;
			originalQueries.put(engine, query);
			originalTypes.put(engine, originalType);
			return engine;
		} catch (Exception e) {
			throw new RuntimeException("Could not construct engine for WAQL query '" + query + "'", e);
		}
	}
	
	/**
	 * Determines whether this flow node is complete, i.e., satisfies all
	 * data dependencies either directly or indirectly (via referenced request outputs).
	 * @return
	 */
	public synchronized boolean isComplete() {
		for(DataFlow r : getDependencies()) {
			if(r.getProvidedBy().size() <= 0)
				return false;
		}
		return true;
	}
	
	public synchronized boolean hasDependencies() {
		Collection<DataDependency> deps = getAllDependencies();
		for(DataDependency dep : deps) {
			if(!dependencyResults.containsKey(dep) 
					|| dependencyResults.get(dep).isEmpty()) {
				if(!dependencyResultsNew.containsKey(dep) 
						|| dependencyResultsNew.get(dep).isEmpty()) {
					//System.out.println("has depdendencies: " + dep + " - " + dependencyResultsNew.get(dep) + " - " + dependencyResults.get(dep) + " - " + dependencyResultsNew + " - " + dependencyResults);
					return true;
				}
			}
		}
		return false;
	}

	private synchronized Map<PreprocessorEngine,List<DataDependency>> getAllDependenciesAsMap() {
		if(engineToDependency == null || engineToDependency.size() < engines.size()) {
			engineToDependency = new HashMap<PreprocessorEngine, List<DataDependency>>();
			for(Object o : engines.keySet()) {
				PreprocessorEngine e = engines.get(o);
				engineToDependency.put(e, new LinkedList<DataDependency>());
				engineToDependency.get(e).addAll(e.getDependencies());
			}
		}
		return engineToDependency;
	}
	private synchronized List<DataDependency> getAllDependencies(DataDependency dep) {
		List<DataDependency> deps = new LinkedList<DataDependency>();
		for(DataDependency d : getAllDependencies()) {
			boolean equals = DataDependency.Comparator.equal(dep, d);
			if(equals) {
				deps.add(d);
			}
		}
		return deps;
	}
	private synchronized List<DataDependency> getAllDependencies() {
		List<DataDependency> deps = new LinkedList<DataDependency>();
		Map<PreprocessorEngine, List<DataDependency>> map = getAllDependenciesAsMap();
		for(Object o : map.keySet()) {
			deps.addAll(map.get(o));
		}
		return deps;
	}
	
	public synchronized List<DataFlow> getDependencies() {
		if(dataDependencies == null) {
			dataDependencies = new LinkedList<DataFlow>();
			
			for(DataDependency dep : getAllDependencies()) {

				DataFlow f = new DataFlow(dep, this);
				f.setSourceType(DataSourceType.HTTP_BODY);
				f.setXPathQuery(dep.getRequest());
				dataDependencies.add(f);

			}
		}
		return dataDependencies;
	}

	public synchronized List<AbstractInput> getInputsWithInjectedValues(boolean onlyNew) throws Exception {

		insertCachedDependencyResults(onlyNew);

		/** preprocess preparation queries... */
		for(Object o : engines.keySet()) {
			if(o instanceof PreparationQuery) {
				PreparationQuery prep = (PreparationQuery)o;
				ByteArrayOutputStream bos = new ByteArrayOutputStream();
				PreprocessorEngine engine = engines.get(prep);
				engine.transform(bos);
				String value = bos.toString();
				value = stripTemporarilyAddedApostrophes(value, engine);
				prep.setValue(value);
			}
		}
		
		
		if(modifiedInput instanceof NonConstantInput) {

			NonConstantInput resultInput = modifiedInput.copy();

			List<AbstractInput> result = new LinkedList<AbstractInput>(Arrays.asList((AbstractInput)resultInput));

			if(engines.containsKey(ENGINE_KEY_HEADERS)) {
				ByteArrayOutputStream bos = new ByteArrayOutputStream();
				PreprocessorEngine engine = engines.get(ENGINE_KEY_HEADERS);
				engine.transform(bos);
				String headers = bos.toString();
				headers = stripTemporarilyAddedApostrophes(headers, engine);
				resultInput.setHttpHeaders(headers);
			}
			if(engines.containsKey(ENGINE_KEY_SERVICE_EPR)) {
				ByteArrayOutputStream bos = new ByteArrayOutputStream();
				PreprocessorEngine engine = engines.get(ENGINE_KEY_SERVICE_EPR);
				engine.transform(bos);
				String epr = bos.toString();
				epr = stripTemporarilyAddedApostrophes(epr, engine);
				resultInput.setServiceEPR(xmlUtil.toJaxbObject(EndpointReference.class, xmlUtil.toElement(epr)));
			}
			if(engines.containsKey(ENGINE_KEY_SERVICE_URL)) {
				ByteArrayOutputStream bos = new ByteArrayOutputStream();
				PreprocessorEngine engine = engines.get(ENGINE_KEY_SERVICE_URL);
				engine.transform(bos);
				String url = bos.toString();
				url = stripTemporarilyAddedApostrophes(url, engine);
				resultInput.setServiceURL(url);
			}


			if(engines.containsKey(ENGINE_KEY_CONTENT)) {
				PreprocessorEngine engine = engines.get(ENGINE_KEY_CONTENT);
				Class<?> originalClazz = originalTypes.get(engine);
				ByteArrayOutputStream bos = new ByteArrayOutputStream();
				engine.transform(bos);
				String content = bos.toString();
				content = stripTemporarilyAddedApostrophes(content, engine);
				if(originalClazz == String.class) {
					resultInput.setTheContent(content);
				} else if(Element.class.isAssignableFrom(originalClazz)) {
					if(!content.trim().startsWith("<")) {
						List<String> usedIDs = new LinkedList<String>();
						for(InputWrapper w : generatedInputs) {
							usedIDs.add(w.input.getUniqueID());
						}

						Object generatedContent = XQueryProcessor.getInstance().execute(content);
						if(!(generatedContent instanceof List<?>)) {
							generatedContent = Arrays.asList(generatedContent);
						}
						result = new LinkedList<AbstractInput>();
						int counter = 1;
						for(Object o : (List<?>)generatedContent) {
							NonConstantInput resultInputCopy = resultInput.copyViaJAXB();
							resultInputCopy.setTheContent(o);
							
							do {
								resultInputCopy.setUniqueID(resultInputCopy.getExternalID() + "." + (counter++));
							} while(usedIDs.contains(resultInputCopy.getUniqueID()));
							usedIDs.add(resultInputCopy.getUniqueID());

							result.add(resultInputCopy);
						}
					} else {
						resultInput.setTheContent(xmlUtil.toElement(content));
					}
				} else
					throw new RuntimeException("Unexpected request input content type: " + originalClazz);
			}

			for(int i = 0; i < result.size(); i ++) {
				AbstractInput in = result.get(i);
				boolean isNew = !generatedInputs.contains(new InputWrapper(in));
				if(onlyNew && !isNew) {
					result.remove(i--);
				} else {
					generatedInputs.add(new InputWrapper(in));
				}
			}
			
			return result;
		} else if(modifiedInput instanceof ConstantInput) {
			ConstantInput result = new ConstantInput((ConstantInput)modifiedInput);

			if(engines.containsKey(ENGINE_KEY_CONTENT)) {
				ByteArrayOutputStream bos = new ByteArrayOutputStream();
				PreprocessorEngine engine = engines.get(ENGINE_KEY_CONTENT);
				engine.transform(bos);
				String content = bos.toString();
				content = stripTemporarilyAddedApostrophes(content, engine);
				result.setTheContent(content);
			}

			boolean isNew = !generatedInputs.contains(new InputWrapper(result));
			if(onlyNew && !isNew) {
				return new LinkedList<AbstractInput>();
			}
				
			generatedInputs.add(new InputWrapper(result));
			return Arrays.asList((AbstractInput)result);
		}
		
		throw new RuntimeException("Unexpected input type: " + modifiedInput);
	}
	
	private String stripTemporarilyAddedApostrophes(String value, PreprocessorEngine fromEngine) {
		if(queriesWrappedAsCData.contains(fromEngine)) {
			if(value.startsWith(CDATA_PREFIX)) value = value.substring(CDATA_PREFIX.length());
			if(value.endsWith(CDATA_SUFFIX)) value = value.substring(0, value.length() - CDATA_SUFFIX.length());
		}
		return value;
	}
	
	private synchronized DataFlow getFlowFromDependency(DataDependency d) {
		for(DataFlow dep : getDependencies()) {
			if(d.equals(dep.getDependency())) {
				return dep;
			}
		}
		return null;
	}

	/**
	 * Inserts data into all data flow nodes which have a matching data dependency and
	 * returns the list of all affected data flow nodes.
	 * @param data
	 * @param provider
	 * @return
	 * @throws Exception
	 */
	public synchronized List<DependencyUpdatedInfo> insertDataIfMatchesDependency(InvocationResult data, FlowNode provider) throws Exception {
		List<DependencyUpdatedInfo> result = new LinkedList<DependencyUpdatedInfo>();
		
		for(Object o : insertedInputs) {
			if(o == data)  // avoid duplicate insertion of *same* (not only equal) object
				return result;
		}
		insertedInputs.add(data);
		while(insertedInputs.size() > maxCacheSize) {
			insertedInputs.remove(0);
		}

		//Map<PreprocessorEngine, List<DataDependency>> map = getAllDependenciesAsMap();
		//for(PreprocessorEngine e : map.keySet()) {

			Collection<DataDependency> depsDone = new LinkedList<DataDependency>();
			for(DataDependency dep : getAllDependencies()) {

				if(dep.getIdentifier() == null || 
						dep.getIdentifier().toString().equals(provider.getOriginalInput().getExternalID())) {

					if(logger.isDebugEnabled()) logger.debug("Dependency ID '" + dep.getIdentifier() + "' matches input ID " + provider.getOriginalInput().getExternalID());

					DataFlow d = getFlowFromDependency(dep);

					//System.out.println("\nprovidedBy.. " + d.getProvidedBy() + " - " + d.getProvidedBy().contains(provider));
					if(d.getProvidedBy().contains(provider)) {
						DependencyUpdatedInfo info = new DependencyUpdatedInfo();
						info.xpath = d.getXPathQuery();
						info.provider = provider.originalInput;
						info.receiver = d.getRequiredBy().originalInput;
						result.add(info);
						
						if(d.isHeader()) {
							String key = d.getHeaderName();
							List<String> headerValues = data.getHeader(key);
							if(headerValues != null) {
								for(String h : headerValues) {
									cacheDependencyResult(dep, h);
								}
							}
						} else {
							Object items = XPathProcessor.evaluate(d.getXPathQuery(), data.getResultAsElement());
							cacheDependencyResult(dep, items);
							depsDone.add(dep);
						}
					}
				}
			//}
		}
		return result;
	}

	private void cacheDependencyResult(DataDependency dep, Object result) {
		
		for(DataDependency d : getAllDependencies(dep)) {

			List<Object> listAll = dependencyResults.get(d);
			List<Object> listNew = dependencyResultsNew.get(d);
			if(listAll == null) {
				listAll = new LinkedList<Object>();
				dependencyResults.put(d, listAll);
			}
			if(listNew == null) {
				listNew = new LinkedList<Object>();
				dependencyResultsNew.put(d, listNew);
			}
			if(!(result instanceof List<?>))
				result = Arrays.asList(result);
			for(Object o : ((List<?>)result)) {
				if(!listNew.contains(o))
					listNew.add(o);
			}
		}
	}
	
	private synchronized void insertCachedDependencyResults(boolean onlyNew) {
		Map<PreprocessorEngine, List<DataDependency>> map = getAllDependenciesAsMap();
		//System.out.println("map: " + map);
		for(PreprocessorEngine e : map.keySet()) {
			for(DataDependency dep : map.get(e)) {
				List<Object> results = dependencyResultsNew.get(dep);
				if(results != null && results.size() > 0) {
					if(onlyNew) {
						// insert only the "new" results (newly added since the last call of this method)
						e.resolveDependency(dep, results);
					} else {
						// insert all results
						List<Object> allResults = new LinkedList<Object>(results);
						allResults.addAll(dependencyResults.get(dep));
						e.resolveDependency(dep, allResults);
					}
					List<Object> depRes = dependencyResults.get(dep);
					depRes.addAll(results);
					results.clear();
					// avoid memory leak:
					while(depRes.size() > maxCacheSize) {
						depRes.remove(0);
					}
				}
			}
		}
	}
	
	public void addPreparationQuery(PreparationQuery prepQuery) {
		this.preparationQueries.add(prepQuery);
		if(prepQuery.getValue() != null) {
			PreprocessorEngine engine = constructEngineForWAQLQuery(prepQuery.getValue(), String.class);
			if(engine != null) {
				engines.put(prepQuery, engine);
			}
			/** re-generate data dependencies. */
			dataDependencies = null;
			dataDependencies = getDependencies();
		}
	}

	protected void addReceivedResult(InvocationResult r) {
		synchronized (receivedResults) {
			receivedResults.add(r);
			while(receivedResults.size() > maxCacheSize)
				receivedResults.remove(0);
		}
	}

	@Override
	public String toString() {
		List<Object> responses = new LinkedList<Object>();
		synchronized (receivedResults) {
			for(InvocationResult r : receivedResults){
				responses.add(r.getResult());
			}
		}
		return "[FN " + responses + "]";
	}
	
	public AbstractInput getOriginalInput() {
		return originalInput;
	}
	public List<InvocationResult> getReceivedResultsCopy() {
		return Collections.unmodifiableList(receivedResults);
	}
	
	
	

//	private synchronized List<DependencyUpdatedInfo> insertDataIfMatchesDependency11(InvocationResult data) throws Exception {
//		List<DependencyUpdatedInfo> result = new LinkedList<DependencyUpdatedInfo>();
//		Map<PreprocessorEngine, List<DataDependency>> map = getAllDependenciesAsMap();
//		logger.debug("FlowNode: " + map);
//		for(PreprocessorEngine e : map.keySet()) {
//			
//			Collection<DataDependency> depsDone = new LinkedList<DataDependency>();
//			for(DataDependency dep : map.get(e)) {
//			
//				System.out.println(dep.getIdentifier());
//				//System.out.println("------~~~~> " + data.get);
//				DataFlow d = getFlowFromDependency(dep);
//				
//				DependencyUpdatedInfo info = new DependencyUpdatedInfo();
//				info.xpath = d.getXPathQuery();
//				info.provider = new ConstantInput(data);
//				info.receiver = d.getRequiredBy().originalInput;
//				
//				if(d.isHeader()) {
//					String key = d.getHeaderName();
//					List<String> headerValues = data.getHeader(key);
//					if(headerValues != null) {
//						for(String h : headerValues) {
//							//e.resolveDependency(dep, h);
//							cacheDependencyResult(dep, h);
//						}
//						result.add(info);
//					}
//				} else {
//					boolean matches = XPathProcessor.matches(dep.getRequest(), data.getResultAsElement());
//					logger.debug("FlowNode -----> " + dep.getRequest() + " matches: " + matches + " - " + data.getResultAsElement());
//					if(matches) {
//						Object items = XPathProcessor.evaluate(d.getXPathQuery(), data.getResultAsElement());
//						//e.resolveDependency(dep, items);
//						cacheDependencyResult(dep, items);
//						depsDone.add(dep);
//						result.add(info);
//					}
//				}
//			}
//			//map.get(e).removeAll(depsDone);
//		}
//		return result;
//	}
		
}
