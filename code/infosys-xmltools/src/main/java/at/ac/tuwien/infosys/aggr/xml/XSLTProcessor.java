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

import java.io.StringReader;

import javax.xml.transform.Source;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.sax.SAXSource;

import net.sf.saxon.s9api.DOMDestination;
import net.sf.saxon.s9api.Destination;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.XsltCompiler;
import net.sf.saxon.s9api.XsltExecutable;
import net.sf.saxon.s9api.XsltTransformer;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

import at.ac.tuwien.infosys.util.Util;

public class XSLTProcessor {
	
	private static Util Util = new Util();
	
	public static Object execute(Source source/*, Source target*/) throws Exception {
		
		Processor p = new Processor(false);
		XsltCompiler comp = p.newXsltCompiler();
		XsltExecutable exec = comp.compile(source);
		XsltTransformer trans = exec.load();
		if(source != null)
			trans.setSource(source);
		Document result = Util.xml.newDocument();
		Destination dest = new DOMDestination(result);
		trans.setDestination(dest);
		trans.transform();
		Util.xml.print(result.getDocumentElement());
		return result;
	}

	public static Object execute(Node source) throws Exception {
		Source theSource = new DOMSource(source);
		return execute(theSource);
	}

	public static Object execute(String xmlSource) throws Exception {
		Source theSource = null;
		if(xmlSource != null)
			theSource = new SAXSource(new InputSource(new StringReader(xmlSource)));
		return execute(theSource);
	}

	public static void main(String[] args) throws Exception {
		
		String a = "-o output.xml -it main src/at/ac/tuwien/infosys/aggr/xml/csvToXML.xslt pathToCSV=file:$HOME/Desktop/csv.csv";
		net.sf.saxon.Transform.main(a.split(" "));
	}
	
}
