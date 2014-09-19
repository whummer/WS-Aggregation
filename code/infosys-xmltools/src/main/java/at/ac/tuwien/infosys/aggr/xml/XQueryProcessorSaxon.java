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

package at.ac.tuwien.infosys.aggr.xml;

import io.hummer.util.Configuration;
import io.hummer.util.Util;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.xml.transform.ErrorListener;
import javax.xml.transform.Source;
import javax.xml.transform.TransformerException;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.sax.SAXSource;

import net.sf.saxon.dom.NodeOverNodeInfo;
import net.sf.saxon.dom.NodeWrapper;
import net.sf.saxon.s9api.DOMDestination;
import net.sf.saxon.s9api.Destination;
import net.sf.saxon.s9api.DocumentBuilder;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.XQueryCompiler;
import net.sf.saxon.s9api.XQueryEvaluator;
import net.sf.saxon.s9api.XQueryExecutable;
import net.sf.saxon.s9api.XdmItem;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.tree.tiny.TinyDocumentImpl;
import net.sf.saxon.tree.tiny.TinyElementImpl;
import net.sf.saxon.tree.tiny.TinyTextImpl;
import net.sf.saxon.value.BooleanValue;
import net.sf.saxon.value.StringValue;

import org.apache.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.Text;
import org.xml.sax.InputSource;

public class XQueryProcessorSaxon extends XQueryProcessor {

	private static final Util util = new Util();
	private static final ErrorListener noopErrorListener = new NoopErrorListener();
	private static class NoopErrorListener implements ErrorListener {
		public void warning(TransformerException exception) throws TransformerException { }
		public void fatalError(TransformerException exception) throws TransformerException { }
		public void error(TransformerException exception) throws TransformerException { }
	};
	
	private static final Logger logger = Util.getLogger(XQueryProcessor.class);
	private static Processor processor;
	private static XQueryCompiler compiler;
	private static final Map<String,XQueryExecutable> cache = new HashMap<String,XQueryExecutable>();
	private static final Map<String,Long> queryUseTimes = new HashMap<String,Long>();
	private static final int MAX_CACHE_DURATION_MS = 1000*60*5;
	
	static {
		processor = new Processor(false);
		processor.registerExtensionFunction(new DataConverters.CSVtoXML());
		processor.registerExtensionFunction(new DataConverters.XMLtoHTMLTable());
		processor.registerExtensionFunction(new DataConverters.JSONtoHTML());
		processor.registerExtensionFunction(new DataConverters.PDFtoHTML());
		compiler = processor.newXQueryCompiler();
		setDoLogOutput(false);
	}
	
	public Object execute(Source source, String query) throws Exception {
		
		XQueryExecutable exec = getExecutable(query);
		
		return execute(source, exec, null);
	}
	
	public Object execute(Source source, XQueryExecutable exec, Map<String,Object> variables) throws Exception {

		List<Object> theResult = new ArrayList<Object>();
		
		XQueryEvaluator eval = exec.load();
		DocumentBuilder builder = processor.newDocumentBuilder();
		if(variables != null) {
			for(String var : variables.keySet()) {
				//Source sourceNode = new DOMSource((Element)variables.get(var));
				if(var.startsWith("$"))
					var = var.substring(1);
				XdmNode sourceDoc = builder.wrap(variables.get(var));
				eval.setExternalVariable(new QName(var), sourceDoc);
			}
		}
		if(source != null)
			eval.setSource(source);
		Document result = util.xml.newDocument();
		Destination dest = new DOMDestination(result);
		eval.setDestination(dest);

		for(XdmItem i : eval) {
			Object item = i.getUnderlyingValue();
			if(item instanceof TinyElementImpl) {
				TinyElementImpl _el = (TinyElementImpl)item;
				Element el = (Element)NodeOverNodeInfo.wrap(_el);
				theResult.add(el);
			} else if(item instanceof StringValue) {
				theResult.add(((StringValue)item).getStringValue());
			} else if(item instanceof TinyTextImpl) {
				theResult.add(((TinyTextImpl)item).getStringValue());
			} else if(item instanceof BooleanValue) {
				theResult.add(((BooleanValue)item).getBooleanValue());
			} else if(item instanceof NodeWrapper) {
				theResult.add((Element)((NodeWrapper)item).getUnderlyingNode());
			} else {
				logger.error("Not specified how to handle type " + item.getClass() + ", instance: " + item);
				throw new Exception("Not specified how to handle type " + item.getClass() + ", instance: " + item);
			}
		}

//		if(theResult.size() <= 0)
//			return null;
		if(theResult.size() == 1)
			return theResult.get(0);
		return theResult;
	}

	private XQueryExecutable getExecutable(String query) throws Exception {
		synchronized (compiler) {
			query = "declare namespace wsaggr=\"" + Configuration.NAMESPACE + "\";\n" + query;
			
			boolean useCaching = true;
			
			if(useCaching) {
				for(String q : new HashSet<String>(queryUseTimes.keySet())) {
					if((System.currentTimeMillis() - queryUseTimes.get(q)) > MAX_CACHE_DURATION_MS) {
						queryUseTimes.remove(q);
						cache.remove(q);
					}
				}
				
				if(cache.containsKey(query)) {
					queryUseTimes.put(query, System.currentTimeMillis());
					return cache.get(query);
				}
			}
			
			try {
				XQueryExecutable exec = compiler.compile(query);
				if(useCaching) {
					cache.put(query, exec);
				}
				return exec;
			} catch (Exception e) {
				throw e;
			}
		}
	}
	
	public static void setDoLogOutput(boolean doLog) {
		synchronized (compiler) {
			if(!doLog)
				compiler.setErrorListener(noopErrorListener);
			else
				compiler.setErrorListener(null);
		}
	}

	public static boolean getDoLogOutput() {
		synchronized (compiler) {
			return compiler.getErrorListener() != null && !(compiler.getErrorListener() instanceof NoopErrorListener);
		}
	}
	
	public Object execute(Node source, XQueryExecutable exec, boolean forceSingleResultElement) throws EmptyQueryResultException, Exception {
		return execute(source, exec, forceSingleResultElement, null);
	}
	public Object execute(Node source, XQueryExecutable exec, boolean forceSingleResultElement, Map<String,Object> variables) throws EmptyQueryResultException, Exception {
		Source theSource = source == null ? null : new DOMSource(source);
		Object result = null;
		try {
			result = execute(theSource, exec, variables);
		} catch (Exception e) {
			theSource = new DOMSource(util.xml.clone((Element)source));
			result = execute(theSource, exec, variables);
		}
		if(forceSingleResultElement) {
			if(!(result instanceof Element)) {
				Element resultEl = util.xml.toElement("<result/>");
				if(result instanceof List<?>) {
					for(Object o : ((List<?>)result)) {
						if(o instanceof Element) {
							util.xml.appendChild(resultEl, (Element)o);
						} else if(o instanceof NodeWrapper) {
							Element e = (Element)((NodeWrapper)o).getUnderlyingNode();
							util.xml.appendChild(resultEl, e);
						} else {
							throw new Exception("Unexpected type of result list item: " + o);
						}
					}
				} else if(result instanceof String) {
					resultEl.setTextContent(resultEl.getTextContent() + ((String)result));
				} else if(result instanceof TinyTextImpl) {
					resultEl.setTextContent(resultEl.getTextContent() + ((TinyTextImpl)result).getStringValue());
				} else if(result instanceof Text) {
					resultEl.setTextContent(resultEl.getTextContent() + ((Text)result).getData());
				} else if(result instanceof NodeWrapper) {
					resultEl = (Element)((NodeWrapper)result).getUnderlyingNode();
				} else if(result instanceof TinyDocumentImpl) {
					resultEl = util.xml.toElement((TinyDocumentImpl)result);
				} else if(result == null) {
					String string = util.xml.toString((Element)source);
					if(string.length() > 1000) {
						string = string.substring(0, 1000) + " ... [truncated]";
					}
					throw new EmptyQueryResultException("Received empty result for query: " + exec.getUnderlyingCompiledQuery().getExpression() + "; Node was: " + (source instanceof Element ? string : source));
				} else {
					throw new Exception("Expected result type: single element, string, or list of elements/strings. Got: " + result + "; XQuery was: " + exec.getUnderlyingCompiledQuery().getExpression() + "; Node was: " + (source instanceof Element ? util.xml.toString((Element)source) : source));
				}
				result = resultEl;
			}
		}
		return result;
	}
	
	@Override
	public Object execute(Node source, String query, boolean forceSingleResultElement, Map<String,Object> variables) throws EmptyQueryResultException, Exception {
		
		if(variables != null) {
			for(String var : variables.keySet()) {
				if(!var.startsWith("$"))
					var = "$" + var;
				query = "declare variable " + var + " external; " + query;
			}
		}
		XQueryExecutable exec = getExecutable(query);
		
		return execute(source, exec, forceSingleResultElement, variables);
	}

	public Object execute(String xmlSource, String query) throws Exception {
		Source theSource = null;
		if(xmlSource != null)
			theSource = new SAXSource(new InputSource(new StringReader(xmlSource)));
		return execute(theSource, query);
	}



	public static void main(String[] args) throws Exception {
		
		XQueryProcessorSaxon x = new XQueryProcessorSaxon();
		
		String ns = "myNS";
		
		Document d = util.xml.newDocument();
		Element request = d.createElementNS(ns, "request");
		request.setPrefix("tns");
		request.setAttribute("xmlns:tns", ns);
		for(int i = 1; i <= 10; i++) {
			Element e = d.createElementNS(ns, "number");
			e.setPrefix("tns");
			e.setTextContent("" + i);
			request.appendChild(e);
		}

		String query = 	"for $num in //*:number " +
						"return " +
							"<result>{$num}</result>";

		Object result1 = x.execute(request.getOwnerDocument(), query, false);
		System.out.println(result1);

		query = "declare namespace tns=\"" + ns + "\";\n" +
				"<a b='c'>{avg(//tns:number)}</a>";
		
//		query = "for $i in (0 to 14) return " +
//		"<tns:render xmlns:tns=\"http://test.aggr.infosys.tuwien.ac.at/\">" +
//		"<source>theSource</source>" +
//		"<start>$i*100</start>" +
//		"<end>(($i+1)*100)</end>" +
//		"</tns:render>";
		
//		String requestString = Util.toString(request);
//		Source theSource = new SAXSource(new InputSource(
//				new ByteInputStream(requestString.getBytes(), 
//						requestString.getBytes().length)));
		query = "for $i in ('a','b') return concat('&amp;q=', $i)";
		query = "declare namespace csv=\"java:at.ac.tuwien.infosys.aggr.CSVtoXML\";\n" +
//				"declare function local:csvToXML($in as xs:string) as element() {\n" +
//				"<a>{$in}</a>\n" +
//				"}; \n" +
//				"declare function local:outtie($v as xs:integer) as xs:integer external;" +
//				"local:csvToXML(/)M;" +
				"csv:toXML('lala,lu,li,loo\nlala,lu,li,loo\n')/csv " +
				"";
		
		query = "for $e in (<a/>,'b',<b>,</b>) return $e";
		
		query = "for $saql2sym in (0 to 1) return " +
				"(for $saql1count in (1 to 2) " +
				"let $saql1syms := (('BIG','GOOG'),('<Symbol>','Symbol1')) \n" +
				"return (<GetGlobalHistoricalQuote xmlns=\"http://www.xignite.com/services/\">" +
				"<Identifier>{$saql1syms[0*2+$saql1count]}</Identifier>" +
				"<IdentifierType>{$saql1syms[1*2+$saql1count]}</IdentifierType>" +
				"<AdjustmentMethod>SplitOnly</AdjustmentMethod>" +
				"<AsOfDate>{$saql2sym}</AsOfDate>" +
				"</GetGlobalHistoricalQuote>))";
		
//		query = "declare namespace tns=\"" + ns + "\";\n" +
//				"<result>{//tns:number}</result>";
//		
//		query = "<prices><avg>{sum(//sum) div sum(//count)}</avg></prices>";
//		request = Util.toElement("<prices><sum>10</sum><count>10</count><sum>15</sum><count>17</count></prices>");
//		
//		request = Util.toElement(Util.toString(request));
		
		query = "declare namespace wsa=\"" + Configuration.NAMESPACE + "\";" +
				"wsaggr:toTable(" + 
				"<tab><header><h>Date</h><h>Price</h></header>" + 
				"{wsa:csvToXML(/)/row}" + 
				"</tab>" + 
				")";
		
		query = "declare namespace wsa=\"" + Configuration.NAMESPACE + "\";" +
				"wsa:csvToXML(/)";
		
		request = util.xml.toElement("<csv>1,2,3\n1,2,3\n1,2,3\n1,2,3</csv");

		query = "declare namespace wsa=\"" + Configuration.NAMESPACE + "\";" +
				"every $x in wsa:csvToXML(/)/row satisfies count($x/col) >= 3";

		List<Long> times = new LinkedList<Long>();
		Object result = null;
		for(int i = 0; i < 100; i ++) {
			String temp = query.replaceAll("<Symbol>", "" + i);
			long before = System.currentTimeMillis();
			result = x.execute(request.getOwnerDocument(), temp, false);
			long after = System.currentTimeMillis();
			times.add(after - before);
		}
		System.out.println(times);
		
		times.clear();
		XQueryExecutable exec = x.getExecutable(query);
		for(int i = 0; i < 100; i ++) {
			long before = System.currentTimeMillis();
			result = x.execute(request.getOwnerDocument(), exec, false);
			long after = System.currentTimeMillis();
			times.add(after - before);
		}
		System.out.println(times);
		
		
		
		if(result instanceof List<?>) {
			for(Object o : ((List<?>)result)) {
				if(o instanceof Element)
					util.xml.print((Element)o);
				else
					System.out.println(o);
			}
		} else if(result instanceof Element) {
			System.out.println(util.xml.toString((Element)result));
		} else if(result instanceof Document) {
			System.out.println(util.xml.toString(((Document)result).getDocumentElement()));
		} else if(result instanceof TinyDocumentImpl) {
			System.out.println(util.xml.toString(util.xml.toElement((TinyDocumentImpl)result)));
		} else if(result instanceof NodeWrapper) {
			System.out.println(util.xml.toString((Element)((NodeWrapper)result).getUnderlyingNode()));
		} else {
			System.out.println(result);
		}

	}
	
}
