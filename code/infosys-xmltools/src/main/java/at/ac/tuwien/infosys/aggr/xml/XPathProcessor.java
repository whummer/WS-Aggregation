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

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import net.sf.saxon.dom.ElementOverNodeInfo;
import net.sf.saxon.dom.NodeWrapper;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.ValueRepresentation;
import net.sf.saxon.s9api.DocumentBuilder;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.XPathCompiler;
import net.sf.saxon.s9api.XPathExecutable;
import net.sf.saxon.s9api.XPathSelector;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XdmValue;
import net.sf.saxon.value.BooleanValue;
import net.sf.saxon.value.DoubleValue;
import net.sf.saxon.value.EmptySequence;
import net.sf.saxon.value.Int64Value;
import net.sf.saxon.value.SequenceExtent;
import net.sf.saxon.value.StringValue;

import org.apache.log4j.Logger;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.Text;

import at.ac.tuwien.infosys.util.Util;

public class XPathProcessor {
	
	private static Util Util = new Util();
	private static Logger logger = at.ac.tuwien.infosys.util.Util.getLogger(XPathProcessor.class);
	private static final boolean DEFAULT_BACKWARDS_COMPATIBLE = false;

	public static boolean matches(String XPath, Element element) throws Exception {

		Processor processor = new Processor(false);
		XPathCompiler compiler = processor.newXPathCompiler();
		
		try {
			XPathExecutable exec = compiler.compile(XPath);
			XPathSelector eval = exec.load();
			DocumentBuilder builder = processor.newDocumentBuilder();
			if(element != null)
				eval.setContextItem(builder.wrap(element));
			return eval.effectiveBooleanValue();
		} catch (Exception e) {
			logger.warn("Unable to evaluate XPath expression '" + XPath + "'");
			throw e;
		}
	}

	@SuppressWarnings("all")
	public static <T> T evaluate(String XPath, Element element) throws Exception {
		return (T)evaluate(XPath, element, null);
	}
	public static List<?> evaluateWithPrefixes(String XPath, Element element, 
			Map<String,String> prefixNamespaces) throws Exception {
		return evaluateAsList(XPath, element, DEFAULT_BACKWARDS_COMPATIBLE, null, prefixNamespaces);
	}
	public static Object evaluate(String XPath, Map<String,Object> variables) throws Exception {
		return evaluate(XPath, null, variables);
	}
	@SuppressWarnings("all")
	public static <T> T evaluate(String XPath, Element element, Map<String,Object> variables) throws Exception {
		List<?> result = evaluateAsList(XPath, element, DEFAULT_BACKWARDS_COMPATIBLE, variables, null);
		if(result.size() == 1)
			return (T)result.get(0);
		return (T)result;
	}

	public static <T> List<T> evaluateAsList(String XPath, Element element) throws Exception {
		return evaluateAsList(XPath, element, null);
	}

	public static <T> List<T> evaluateAsList(String XPath, Element element, Map<String,Object> variables) throws Exception {
		return evaluateAsList(XPath, element, DEFAULT_BACKWARDS_COMPATIBLE, variables, null);
	}
	
	public static <T> List<T> evaluateAsList(String XPath, Element element, boolean backwardsCompatible) throws Exception {
		return evaluateAsList(XPath, element, backwardsCompatible, null, null);
	}
	@SuppressWarnings("all")
	public static <T> List<T> evaluateAsList(String XPath, Element element, boolean backwardsCompatible, 
			Map<String,Object> variables, Map<String,String> prefixNamespaces) throws Exception {

		List<T> resultList = new LinkedList<T>();
		Processor processor = new Processor(false);
		XPathCompiler compiler = processor.newXPathCompiler();
		compiler.setBackwardsCompatible(backwardsCompatible);
		
		if(prefixNamespaces != null) {
			for(String prefix : prefixNamespaces.keySet()) {
				compiler.declareNamespace(prefix, prefixNamespaces.get(prefix));
			}
		}
		
		XPathExecutable exec = compiler.compile(XPath);
		XPathSelector eval = exec.load();
		
		if(variables != null) {
			DocumentBuilder builder = processor.newDocumentBuilder();
			for(String var : variables.keySet()) {
				if(var.startsWith("$"))
					var = var.substring(1);
				XdmNode sourceDoc = builder.wrap(variables.get(var));
				eval.setVariable(new QName(var), sourceDoc);
			}
		}
		
		DocumentBuilder builder = processor.newDocumentBuilder();
		
		// to avoid the exception: 
		// 'net.sf.saxon.s9api.SaxonApiException: 
		//		Supplied node must be built using the same or a compatible Configuration'
		if(element instanceof ElementOverNodeInfo)
			element = Util.xml.clone(element); 
		
		if(element != null)
			eval.setContextItem(builder.wrap(element));
		XdmValue result = eval.evaluate();
		
		List<T> res = convertToStdJava(result);
		resultList.addAll(res);
		
		return resultList;
	}

	@SuppressWarnings("all")
	private static <T> List<T> convertToStdJava(XdmValue result) {
		return (List<T>)convertToStdJava(result.getUnderlyingValue());
	}
	private static List<?> convertToStdJava(ValueRepresentation<?> result) {
		if(result instanceof NodeWrapper) {
			Node node = (Node)((NodeWrapper)result).getUnderlyingNode();
			if(node instanceof Text)
				return Arrays.asList(((Text)node).getTextContent());
			else if(node instanceof Element)
				return Arrays.asList((Element)node);
			else if(node instanceof Attr)
				return Arrays.asList(((Attr)node).getTextContent());
			else 
				throw new RuntimeException("Unexpected node returned by XPath evaluator: " + 
						node + ", "  + node.getClass());
		} else if(result instanceof SequenceExtent) {
			SequenceExtent<?> s = ((SequenceExtent<?>)result);
			List<Object> res = new LinkedList<Object>();
			for(int i = 0; i < s.getLength(); i ++) {
				Item<?> item = s.itemAt(i);
				res.addAll(convertToStdJava(item));
			}
			return res;
		} else if(result instanceof BooleanValue) {
			return Arrays.asList(((BooleanValue)result).getBooleanValue());
		} else if(result instanceof Int64Value) {
			return Arrays.asList(((Int64Value)result).getDoubleValue());
		} else if(result instanceof StringValue) {
			return Arrays.asList(((StringValue)result).getStringValue());
		} else if(result instanceof DoubleValue) {
			return Arrays.asList(((DoubleValue)result).getDoubleValue());
		} else if(result instanceof EmptySequence) {
			// nothing to do
			return Arrays.asList();
		} else {
			throw new RuntimeException("Unexpected object returned by XPath evaluator: " + 
					result + (result != null ? (", "  + result.getClass()) : ""));
		}
	}
	
	public static void main(String[] args) throws Exception {
		Element e = Util.xml.toElement("<ns2:getStocksResponse " +
								"xmlns:ns2=\"http://test.aggr.infosys.tuwien.ac.at/\">" +
					        "<return foo=\"bar\" bar=\"\foo\">" +
					            "<stock>s1</stock>" +
					            "<stock>s2</stock>" +
					            "<stock>s3</stock>" +
					        "</return>" +
					    "</ns2:getStocksResponse>");
		System.out.println(XPathProcessor.matches("//stock", e));
		System.out.println(XPathProcessor.evaluate("//stock", e));
		System.out.println(XPathProcessor.evaluate("//return/stock/last()", e));
		System.out.println(XPathProcessor.evaluate("count(//return/stock)", e));
		System.out.println(XPathProcessor.evaluate("return and substring-after(//return/stock[1],'s')", e));
		System.out.println(XPathProcessor.evaluate("string-length(//return/stock[1])", e));
		System.out.println(XPathProcessor.evaluate("//return[@foo][@bar]", e));
		
	}
}
