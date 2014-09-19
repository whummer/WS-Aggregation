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

import io.hummer.util.Util;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.Text;

import ch.ethz.mxquery.contextConfig.CompilerOptions;
import ch.ethz.mxquery.contextConfig.Context;
import ch.ethz.mxquery.datamodel.Source;
import ch.ethz.mxquery.datamodel.types.Type;
import ch.ethz.mxquery.datamodel.types.TypeDictionary;
import ch.ethz.mxquery.datamodel.xdm.TokenInterface;
import ch.ethz.mxquery.exceptions.QueryLocation;
import ch.ethz.mxquery.model.Iterator;
import ch.ethz.mxquery.model.XDMIterator;
import ch.ethz.mxquery.query.PreparedStatement;
import ch.ethz.mxquery.query.XQCompiler;
import ch.ethz.mxquery.query.impl.CompilerImpl;
import ch.ethz.mxquery.xdmio.XDMInputFactory;
import ch.ethz.mxquery.xdmio.XMLSource;

@Deprecated
public class XQueryProcessorMXQuery extends XQueryProcessor {

	boolean updateFiles = true;

	@Override
	public Object execute(Node target, String expression, boolean forceSingleResultElement, Map<String,Object> variables) throws EmptyQueryResultException, Exception {

		try {
			Util util = new Util();
			
			// Create new (unified) Context
			Context ctx = new Context();
			// Create a compiler options oh
			CompilerOptions co = new CompilerOptions();
			// Enable XQuery 1.1 features
			co.setXquery11(true);
			// Enable schema support
			co.setSchemaAwareness(true);
			// Enable update facility support
			co.setUpdate(true);
			// use updateable stores by default
			ctx.getStores().setSerializeStores(updateFiles);
			// create a XQuery compiler
			XQCompiler compiler = new CompilerImpl();
			PreparedStatement statement;

			// out of the context and the query "text" create a prepared
			// statement, considering the compiler options
			statement = compiler.compile(ctx, expression, co, null, null);
			XDMIterator resultIter;
			// Get an iterator from the prepared statement
			// Set up dynamic context values, e.g., current time
			resultIter = statement.evaluate();

			if(target != null) {
				XMLSource xmlIt = XDMInputFactory.createDOMInput(
						resultIter.getContext(),
						target, QueryLocation.OUTSIDE_QUERY_LOC);
				statement.setContextItem(xmlIt);
			}

			// TODO: is this portable??
			
			Source s = ctx.getStores().createStore(null, resultIter, false);
			Iterator w = s.getIterator(ctx);
			List<Object> resultList = new LinkedList<Object>();
			TokenInterface token;
			while((token = w.next()) != null)  {
//				LLStoreIterator iter = ((LLStoreIterator)item);
//				LLStore store = (LLStore)w.getStore();
//				LLRefToken ref = (LLRefToken)store.getFirstToken();
//				LLNormalToken tok = (LLNormalToken)ref.getRef();
				int type = token.getTypeAnnotation();
				TypeDictionary dict = new TypeDictionary();
				if(type == Type.LONG || type == Type.INT || type == Type.INTEGER) {
					resultList.add(token.getLong());
				// TODO
//				} else if(type == Type.ANY_TYPE) {
//					resultList.add(util.toElement(store.toString()));
				} else if(type == Type.BOOLEAN) {
					resultList.add(token.getBoolean());
				} else if(type == Type.DOUBLE || type == Type.DECIMAL) {
					resultList.add(token.getDouble().getValue());
				} else if(Type.isSubTypeOf(type, Type.STRING, dict)) {
					resultList.add(token.getText());
				} else if(type == 16777839) {
					// 16777839 seems to be the data type ID for "URL"
					resultList.add(token.getText());
				} else {
					logger.error("Cannot determine type of token " + token + ", type ID " + type + "; query was: " + expression);
					//XDMSerializer ip = new XDMSerializer(XDMSerializerSettings.OUTPUT_METHOD_TEXT);
					//String str = ip.eventsToXML(new TokenIterator(ctx, tok.getToken(), QueryLocation.OUTSIDE_QUERY_LOC));
					//System.out.println(str);
					resultList.add(token.getText());
				}
			}

			Object resultObj = resultList;
			if(resultList.size() <= 0)
				resultObj = null;
			else if(resultList.size() == 1)
				resultObj = resultList.get(0);
			
			if(forceSingleResultElement) {
				Element resultEl = util.xml.createElement("result");
				if(resultObj instanceof List<?>) {
					for(Object o : ((List<?>)resultObj)) {
						if(o instanceof Element) {
							util.xml.appendChild(resultEl, (Element)o);
						} else {
							throw new Exception("Unexpected type of result list item: " + o);
						}
					}
				} else if(resultObj instanceof Text) {
					resultEl.setTextContent(resultEl.getTextContent() + ((Text)resultObj).getData());
				} else if(resultObj instanceof Element) {
					resultEl = (Element)resultObj;
				} else if((resultObj instanceof String) 
						|| (resultObj instanceof Double)
						|| (resultObj instanceof Boolean)) {
					resultEl.setTextContent(resultEl.getTextContent() + "" + resultObj);
				} else if(resultObj == null) {
					throw new EmptyQueryResultException("Received empty result for query: " + expression);
				} else {
					throw new Exception("Expected result type: single element, string, or list of elements/strings. Got: " + resultObj + "; XQuery was: " + expression);
				}
				return resultEl;
			}
			
			return resultObj;
		} catch (Throwable t) {
			logger.error(t);
			// TODO
			return new XQueryProcessorSaxon().execute(target, expression, forceSingleResultElement);
		}

	}

	public static void main(String[] args) throws Exception {

		XQueryProcessorMXQuery x = new XQueryProcessorMXQuery();

		System.out.println(x.execute(
				"forseq $i in (1,2,3,4,5,6,7) sliding window "
				+ "start position $x when true() "
				+ "force end position $y when ($y - $x)=2 "
				+ "return <a foo=\"bar\">{$i[1]}</a>"));
		System.out.println(x.execute(
				"forseq $i in (1,2,3,4,5,6,7) sliding window "
				+ "start position $x when true() "
				+ "force end position $y when ($y - $x)=2 "
				+ "return $i"));
		System.out.println(x.execute(
				"for $i in (1,2,3,4,5,6,7)  "
				+ "return <a foo=\"bar\">{$i}</a>"));
		System.out.println(x.execute(
				"for $i in (1,2,3,4,5,6,7)  "
				+ "return <a foo=\"bar\"><b><c>{$i}</c></b></a>"));
		System.out.println(x.execute(
				"for $i in (1,-2,'3',0.4,false(),true(),7)  "
				+ "return $i"));
		System.out.println(x.execute(null,
				"for $i in (1,-2,'3',0.4,false(),true(),7)  "
				+ "return $i", false));
		System.out.println(x.execute(null,
				"for $i in (1,2,3,4,5,6,7)  "
				+ "return <a foo=\"bar\">{$i}</a>", true));

	}

}
