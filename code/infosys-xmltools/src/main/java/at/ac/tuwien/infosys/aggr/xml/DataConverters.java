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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.util.UUID;

import javax.xml.transform.dom.DOMSource;

import net.sf.json.JSON;
import net.sf.json.JSONSerializer;
import net.sf.json.xml.XMLSerializer;
import net.sf.saxon.Configuration;
import net.sf.saxon.dom.DocumentWrapper;
import net.sf.saxon.dom.ElementOverNodeInfo;
import net.sf.saxon.dom.NodeWrapper;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.lib.ExtensionFunctionCall;
import net.sf.saxon.lib.ExtensionFunctionDefinition;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.AxisIterator;
import net.sf.saxon.tree.iter.SingleNodeIterator;
import net.sf.saxon.tree.tiny.TinyDocumentImpl;
import net.sf.saxon.tree.tiny.TinyElementImpl;
import net.sf.saxon.value.SequenceType;
import net.sf.saxon.value.StringValue;

import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import at.ac.tuwien.infosys.util.Util;
import at.ac.tuwien.infosys.util.xml.XMLUtil;

public class DataConverters {

	private static final Util util = new Util();
	private static final Logger logger = Util.getLogger(DataConverters.class);

	public static class CSVtoXML extends ExtensionFunctionDefinition {
		private static final long serialVersionUID = 2299520668946316101L;

		@Override
		public SequenceType[] getArgumentTypes() {
			return new SequenceType[]{SequenceType.SINGLE_STRING};
		}

		@Override
		public StructuredQName getFunctionQName() {
			return new StructuredQName("wsaggr", at.ac.tuwien.infosys.util.Configuration.NAMESPACE, "csvToXML");
		}

		@Override
		public int getMinimumNumberOfArguments() {
			return 1;
		}

		@Override
		public SequenceType getResultType(SequenceType[] arg0) {
			return SequenceType.ANY_SEQUENCE;
		}

		@Override
		@SuppressWarnings("all")
		public ExtensionFunctionCall makeCallExpression() {
			ExtensionFunctionCall c = new ExtensionFunctionCall() {
				private static final long serialVersionUID = -4674165664025171362L;

				public SequenceIterator call(SequenceIterator[] s, XPathContext c)
						throws XPathException {
					String csv = null;
					try {
						Item i = s[0].next();
						csv = ((StringValue)i).getStringValue();
					} catch (Exception e) {
						throw new IllegalArgumentException("Expecting string value for csv:toXML");
					}
					NodeInfo node = null;
					try {
						Element result = doConvert(csv);
						node = new DocumentWrapper(result.getOwnerDocument(), null, new Configuration()).wrap(result);
					} catch (Exception e) {
						logger.warn("Unexpected error.", e);
					}
					AxisIterator iter = (AxisIterator)SingleNodeIterator.makeIterator(node);
					return iter;
				}
			};
			return c;
		}
		
		// TODO: optimize code or use third-party implementation!
		private Element doConvert(String csv) throws Exception {
			StringBuilder b = new StringBuilder("<csv>");
			int row = 1;
			for(String line : csv.split("\n")) {
				b.append("<row num=\"");
				b.append(row++);
				b.append("\">");
				int col = 1;
				for(String el : line.split(",")) {
					b.append("<col num=\"");
					b.append(col++);
					b.append("\">");
					b.append(el);
					b.append("</col>");
				}
				b.append("</row>");
			}
			b.append("</csv>");
			Element el = XMLUtil.getInstance().toElement(b.toString());
			return el;
		}
		
	}
	
	
	public static class XMLtoHTMLTable extends ExtensionFunctionDefinition {
		private static final long serialVersionUID = 2299520668946316101L;

		@Override
		public SequenceType[] getArgumentTypes() {
			return new SequenceType[]{SequenceType.SINGLE_ELEMENT_NODE};
		}

		@Override
		public StructuredQName getFunctionQName() {
			return new StructuredQName("wsaggr", at.ac.tuwien.infosys.util.Configuration.NAMESPACE, "toTable");
		}

		@Override
		public int getMinimumNumberOfArguments() {
			return 1;
		}

		@Override
		public SequenceType getResultType(SequenceType[] arg0) {
			return SequenceType.ANY_SEQUENCE;
		}

		@Override
		@SuppressWarnings("all")
		public ExtensionFunctionCall makeCallExpression() {
			ExtensionFunctionCall c = new ExtensionFunctionCall() {
				private static final long serialVersionUID = -4674165664025171362L;

				public SequenceIterator call(SequenceIterator[] s, XPathContext c)
						throws XPathException {
					Element xml = null;
					try {
						Item i = s[0].next();
						if(i instanceof TinyElementImpl) {
							TinyElementImpl ei = (TinyElementImpl)i;
							ElementOverNodeInfo e = (ElementOverNodeInfo)ElementOverNodeInfo.wrap(ei);
							xml = ((Element)e);
						} else if(i instanceof NodeWrapper) {
							xml = (Element)((NodeWrapper)i).getUnderlyingNode();
						} else {
							throw new Exception("Unexpected parameter passed to table:toTable : " + i);
						}
						//Util.getInstance().print(e);
					} catch (Exception e) {
						logger.warn("Unexpected error.", e);
						throw new IllegalArgumentException("Expecting single element as argument to table:toTable");
					}
					NodeInfo node = null;
					try {
						Element result = doConvert(xml); // Util.getInstance().toElement("<a/>");
						node = c.getConfiguration().buildDocument(
								new DOMSource(result.getOwnerDocument())).getRoot();
						node = ((TinyDocumentImpl)node).getDocumentRoot();
						
					} catch (Exception e) {
						logger.warn("Unexpected error.", e);
					}
					return SingleNodeIterator.makeIterator(node);
				}
			};
			return c;
		}
		
		private Element doConvert(Element xml) throws Exception {
			StringBuilder b = new StringBuilder("<table class=\"xmlToHtml\">");
			int rowCount = 0;
			for(Element row : util.xml.getChildElements(xml)) {
				b.append("<tr class=\"row" + (rowCount + 1) + " row2_" + ((rowCount%2) + 1) + " row3_" + ((rowCount%3) + 1) + "\">");
				rowCount++;
				int colCount = 1;
				for(Element col : util.xml.getChildElements(row)) {
					String content = col.getTextContent();
					b.append("<td class=\"col" + (colCount++) + "\">" + content + "</td>");
				}
				b.append("</tr>");
			}
			b.append("</table>");
			Element el = util.xml.toElement(b.toString());
			//Util.getInstance().print(el);
			return el;
		}
		
	}
	
	public static class JSONtoHTML extends ExtensionFunctionDefinition {
		private static final long serialVersionUID = 2299520668946316101L;
		
		private Util util = new Util();

		@Override
		public SequenceType[] getArgumentTypes() {
			return new SequenceType[]{SequenceType.ANY_SEQUENCE};
		}

		@Override
		public StructuredQName getFunctionQName() {
			return new StructuredQName("wsaggr", at.ac.tuwien.infosys.util.Configuration.NAMESPACE, "jsonToXML");
		}

		@Override
		public int getMinimumNumberOfArguments() {
			return 1;
		}

		@Override
		public SequenceType getResultType(SequenceType[] arg0) {
			return SequenceType.SINGLE_NODE;
		}

		@Override
		@SuppressWarnings("all")
		public ExtensionFunctionCall makeCallExpression() {
			ExtensionFunctionCall c = new ExtensionFunctionCall() {
				private static final long serialVersionUID = -4674165664025171362L;

				public SequenceIterator call(SequenceIterator[] s, XPathContext c)
						throws XPathException {
					String string = null;
					try {
						Object i = s[0].next();
						
						if(i instanceof DocumentWrapper) {
							if(((DocumentWrapper)i).getRealNode() instanceof Document) {
								Element root = util.xml.toElement(((Document)((DocumentWrapper)i).getRealNode()).getDocumentElement());
								string = root.getTextContent();
							}
						} else if(i instanceof StringValue) {
							string = ((StringValue)i).getStringValue();
						} else {
							throw new Exception("Unexpected parameter passed to jsonToXML(..): " + i);
						}
					} catch (Exception e) {
						logger.warn("Unexpected error.", e);
						throw new IllegalArgumentException("Expecting single element as argument to jsonToXML(..)");
					}
					NodeInfo node = null;
					try {
						Element result = doConvert(string);
						node = c.getConfiguration().buildDocument(
								new DOMSource(result.getOwnerDocument())).getRoot();
						if(node instanceof TinyDocumentImpl) {
							node = ((TinyDocumentImpl)node).getDocumentRoot();
						}
						if(node instanceof TinyDocumentImpl) {
							AxisIterator iter = node.getRoot().iterateAxis(net.sf.saxon.om.Axis.CHILD);
							node = (NodeInfo)iter.next();
						}
					} catch (Exception e) {
						logger.warn("Unexpected error.", e);
					}
					return SingleNodeIterator.makeIterator(node);
				}
			};
			return c;
		}
		
		private Element doConvert(String jsonString) throws Exception {
			String jsonData = IOUtils.toString(new ByteArrayInputStream(jsonString.getBytes()));
			XMLSerializer serializer = new XMLSerializer(); 
			JSON json = JSONSerializer.toJSON( jsonData ); 
			String xml = serializer.write(json);
			return util.xml.toElement(xml);
		}
		
	}

	public static class PDFtoHTML extends ExtensionFunctionDefinition {
		private static final long serialVersionUID = 2299520668946316101L;

		@Override
		public SequenceType[] getArgumentTypes() {
			return new SequenceType[]{SequenceType.SINGLE_STRING};
		}

		@Override
		public StructuredQName getFunctionQName() {
			return new StructuredQName("wsaggr", at.ac.tuwien.infosys.util.Configuration.NAMESPACE, "pdfToXML");
		}

		@Override
		public int getMinimumNumberOfArguments() {
			return 1;
		}

		@Override
		public SequenceType getResultType(SequenceType[] arg0) {
			return SequenceType.SINGLE_NODE;
		}

		@Override
		@SuppressWarnings("all")
		public ExtensionFunctionCall makeCallExpression() {
			ExtensionFunctionCall c = new ExtensionFunctionCall() {
				private static final long serialVersionUID = -4674165664025171362L;

				public SequenceIterator call(SequenceIterator[] s, XPathContext c)
						throws XPathException {
					String string = null;
					try {
						Item i = s[0].next();
						System.out.println("---> " + i);
						if(i instanceof StringValue) {
							string = ((StringValue)i).getStringValue();
						} else {
							throw new Exception("Unexpected parameter passed to pdfToXML(..): " + i);
						}
					} catch (Exception e) {
						logger.warn("Unexpected error.", e);
						throw new IllegalArgumentException("Expecting single element as argument to pdfToXML(..)");
					}
					NodeInfo node = null;
					try {
						Element result = doConvert(string); // Util.getInstance().toElement("<a/>");
						node = c.getConfiguration().buildDocument(
								new DOMSource(result.getOwnerDocument())).getRoot();
						node = ((TinyDocumentImpl)node).getDocumentRoot();
						
					} catch (Exception e) {
						logger.warn("Unexpected error.", e);
					}
					return SingleNodeIterator.makeIterator(node);
				}
			};
			return c;
		}
		
		private Element doConvert(String pdfFileURL) throws Exception {
			Util util = Util.getInstance();
			String pdfName = UUID.randomUUID().toString() + ".pdf";
			String htmlName = UUID.randomUUID().toString() + ".html";
			InputStream is = null;
			FileOutputStream fos = null;
			try {
				
				URL url = new URL(pdfFileURL);
				is = url.openStream();
				fos = new FileOutputStream(new File(pdfName));
				
				byte[] buffer = util.io.readBytes(is);
				fos.write(buffer);
				
				new File(pdfName).deleteOnExit();
				new File(htmlName).deleteOnExit();
				new File(htmlName.replace(".html", "s.html")).deleteOnExit();
				new File(htmlName.replace(".html", "_ind.html")).deleteOnExit();
				
				Runtime.getRuntime().exec("pdftohtml " + pdfName + " " + htmlName).waitFor();
				String xml = util.io.readFile(new FileInputStream(htmlName));
				util.io.saveFile(htmlName.replace(".html", "s.html.1"), xml);
				
				if(xml.contains("<frameset") || xml.contains("<FRAMESET")) {
					String nameNew = htmlName.replace(".html", "s.html");
					xml = util.io.readFile(new FileInputStream(nameNew), "ISO-8859-1");
					util.io.saveFile(htmlName.replace(".html", "s.html.1"), xml);
				}
				//xml = util.getUnicodeCodeForExtendedChars(xml);
				return util.xml.toElementHTML(xml);
			} catch (Exception e) {
				logger.warn("Unexpected error.", e);
			} finally {
				new File(pdfName).delete();
				new File(htmlName).delete();
				new File(htmlName.replace(".html", "s.html")).delete();
				new File(htmlName.replace(".html", "_ind.html")).delete();
			}
			return null;
		}
		
	}
	
}
