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

package at.ac.tuwien.infosys.aggr.waql.test;

import io.hummer.util.Util;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import at.ac.tuwien.infosys.aggr.waql.MalformedQueryException;
import at.ac.tuwien.infosys.aggr.waql.PreprocessorFactory;

/**
 * A simple test harness capable of running the "XML Query Test Suite" with the
 * parser from the WAQL preprocessor. It is mainly used to check the grammar
 * rules for the parser against some common input queries. To learn more about
 * the aforementioned test suite visit the website.
 * @see <a href="http://www.w3.org/XML/Query/test-suite/">XML Query Test Suite</a>
 * @author Michael Starzinger <michael.starzinger@antforge.org>
 */
public class XQTSHarness
{
	/** The location of the XQTS package file. */
	private static final String XQTS_ZIPFILE = "etc/test/XQTS_1_0_3.zip";

	/** The set of well-known failures to be ignored. */
	private static final Set<String> WELL_KNOWN_FAILURES = new HashSet<String>(
			Arrays.asList(new String[] {
					"Queries/XQuery/Expressions/Construct/DirectConElem/DirectConElemAttr/Constr-attr-content-1.xq", /* escape the $ */
			}));

	private static enum ResultType {
		PASS_ON_VALID,   /* this is ok */
		FAIL_ON_INVALID, /* this is ok */
		PASS_ON_INVALID, /* this is semi-ok */
		FAIL_ON_VALID,   /* this is BAD! */
	}

	private static boolean parse(InputStream input, boolean showErrors) {
		String in = null;
		try {
			try {
				in = Util.getInstance().io.readFile(input);
				input = new ByteArrayInputStream(in.getBytes());
			} catch (Exception e) {
				e.printStackTrace();
			}
			PreprocessorFactory.getEngine().parse(input);
			return true;
		} catch (MalformedQueryException e) {
			if(showErrors) {
				System.out.println(in);
				e.printStackTrace();
			}
			return false;
		}
	}

	private static ResultType check(InputStream input, boolean expectsError) {
		boolean parseResult = parse(input, !expectsError);
		//System.out.printf("\tCHECK expects=%s, result=%s\n", expectsError, parseResult);
		if (!expectsError)
			return parseResult ? ResultType.PASS_ON_VALID : ResultType.FAIL_ON_VALID;
		else
			return parseResult ? ResultType.PASS_ON_INVALID : ResultType.FAIL_ON_INVALID;
	}

	public static void main(String[] args) throws Exception {
		int[] counter = new int[ResultType.values().length];
		System.out.printf("Using XML Query Test Suite: %s\n", new File(XQTS_ZIPFILE).getCanonicalPath());
		ZipFile file = new ZipFile(XQTS_ZIPFILE);
		DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
		for (Enumeration<? extends ZipEntry> en = file.entries(); en.hasMoreElements(); /*nop*/) {
			ZipEntry entry = en.nextElement();
			if (entry.getName().startsWith("cat") && entry.getName().endsWith(".xml")) {
				NodeList tests = builder.parse(file.getInputStream(entry)).getElementsByTagName("test-case");
				for (int i = 0; i < tests.getLength(); i++) {
					Element test = (Element) tests.item(i);
					if (test.getAttribute("is-XPath2").equals("true"))
						continue;
					String testfile = "Queries/XQuery/" + test.getAttribute("FilePath") + ((Element) test.getElementsByTagName("query").item(0)).getAttribute("name") + ".xq";
					if (WELL_KNOWN_FAILURES.contains(testfile))
						continue;
					//System.out.printf("\tNOW cat=%s, query=%s\n", entry.getName(), testfile);
					ResultType result = check(file.getInputStream(file.getEntry(testfile)), test.getElementsByTagName("expected-error").getLength() > 0);
					if (result == ResultType.FAIL_ON_VALID)
						System.out.printf("[%15s] %s\n", result, testfile);
					counter[result.ordinal()]++;
				}
			}
		}
		file.close();
		System.out.println("Result of XML Query Test Suite:");
		for (ResultType result : ResultType.values())
			System.out.printf("\t %15s = %d\n", result, counter[result.ordinal()]);
	}
}
