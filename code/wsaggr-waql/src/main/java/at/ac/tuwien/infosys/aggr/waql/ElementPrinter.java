/*
 * Copyright (c) 2010 Michael Starzinger
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package at.ac.tuwien.infosys.aggr.waql;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Element;

/**
 * A data printer handling {@link org.w3c.dom.Element} objects.
 * @author Michael Starzinger <michael.starzinger@antforge.org>
 */
public class ElementPrinter implements DataPrinter
{
	@Override
	public boolean canHandle(Object object) {
		return (object instanceof Element);
	}

	@Override
	public String printAsExpression(Object object, DataPrinter pipeline) {
		Element element = (Element) object;
		OutputStream output = new ByteArrayOutputStream();
		try {
			Transformer transformer = TransformerFactory.newInstance().newTransformer();
			transformer.setOutputProperty(OutputKeys.INDENT, "no");
			transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
			transformer.transform(new DOMSource(element), new StreamResult(output));
		} catch (TransformerException e) {
			throw new RuntimeException(e);
		}
		return output.toString();
	}

	@Override
	public String printAsText(Object object, DataPrinter pipeline) {
		return printAsExpression(object, pipeline);
	}
}
