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

import java.util.Map;

import org.apache.log4j.Logger;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public abstract class XQueryProcessor {

	public static final Logger logger = Util.getLogger(XQueryProcessor.class);
	
	public abstract Object execute(Node target, String expression, boolean forceSingleResultElement, Map<String,Object> variables) throws EmptyQueryResultException, Exception;
	
	public static class EmptyQueryResultException extends Exception {
		private static final long serialVersionUID = 1L;
		public EmptyQueryResultException() {}
		public EmptyQueryResultException(String msg) {
			super(msg);
		}
	}
	
	public Object execute(Node target, String expression, boolean forceSingleResultElement) throws EmptyQueryResultException, Exception {
		return execute(target, expression, forceSingleResultElement, null);
	}

	public boolean evaluatesToTrue(String expression, Element element) throws Exception {
		Object o = execute(element, "boolean(" + expression + ")", false);
		if(o == null)
			return false;
		return (Boolean)o;
	}
	
	public Object execute(String query) throws Exception {
		return execute(null, query, false);
	}

	public static XQueryProcessor getInstance() {
		// TODO: make configurable..
		return new XQueryProcessorSaxon();
		//return new XQueryProcessorMXQuery();
	}
}
