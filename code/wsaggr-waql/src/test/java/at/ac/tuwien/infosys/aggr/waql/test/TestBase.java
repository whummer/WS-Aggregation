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

package at.ac.tuwien.infosys.aggr.waql.test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Collection;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import junit.framework.Assert;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.w3c.dom.Document;

import at.ac.tuwien.infosys.aggr.waql.DataDependency;
import at.ac.tuwien.infosys.aggr.waql.MalformedQueryException;
import at.ac.tuwien.infosys.aggr.waql.PreprocessorEngine;
import at.ac.tuwien.infosys.aggr.waql.PreprocessorFactory;
import at.ac.tuwien.infosys.aggr.waql.UnresolvedDependencyException;

public abstract class TestBase
{
	private PreprocessorEngine engine;

	@Before
	public void setUp() throws Exception {
		engine = PreprocessorFactory.getEngine();
	}

	@After
	public void tearDown() throws Exception {
		engine = null;
	}

	@Test
	public void testParsing() throws MalformedQueryException {
		InputStream input = new ByteArrayInputStream(getInput().getBytes());
		engine.parse(input);
	}

	@Test
	public void testDependencies() throws MalformedQueryException {
		testParsing();
		Collection<DataDependency> deps = engine.getDependencies();
		Assert.assertTrue("Number of dependencies does not match.", getDependencies().length >= deps.size());
		L1: for (Object[] entry : getDependencies()) {
			for (DataDependency dep : deps) {
				if (entry.length >= 4 && equals(entry[3], false))
					continue L1;
				if (equals(entry[0], dep.getIdentifier()) && equals(entry[1], dep.getRequest()))
					continue L1;
			}
			System.out.println("Dump of incorrect data dependencies:");
			for (DataDependency dep : deps)
				System.out.printf(">> #%d, '%s'\n", dep.getIdentifier(), dep.getRequest());
			Assert.fail("Missing data dependency.");
		}
	}

	@Test
	public void testResolving() throws MalformedQueryException {
		testParsing();
		for (Object[] entry : getDependencies()) {
			for (DataDependency dep : engine.getDependencies())
				if (equals(entry[0], dep.getIdentifier()) && equals(entry[1], dep.getRequest()))
					engine.resolveDependency(dep, entry[2]);
		}
		if (!engine.getDependencies().isEmpty()) {
			System.out.println("Dump of unresolved data dependencies:");
			for (DataDependency dep : engine.getDependencies())
				System.out.printf(">> #%d, '%s'\n", dep.getIdentifier(), dep.getRequest());
		}
		Assert.assertTrue("There still are unresolved dependencies left.", engine.getDependencies().isEmpty());
	}

	@Test
	public void testTransform() throws MalformedQueryException, UnresolvedDependencyException {
		testResolving();
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		engine.transform(output);
		Assert.assertEquals("Generated output does not match.", getOutput(), output.toString());
	}

	/**
	 * Safely check whether two objects are equal. Safe to use even if one or
	 * both of the objects are {@code null}. Will use the {@code equals} method
	 * of the first object if possible.
	 * @param o1 The first object.
	 * @param o2 The second object.
	 * @return Whether the two objects are equal.
	 */
	private static boolean equals(Object o1, Object o2) {
		if (o1 == null && o2 == null)
			return true;
		else if (o1 != null)
			return o1.equals(o2);
		else
			return o2.equals(o1);
	}

	/**
	 * Creates a new {@link org.w3c.dom.Document} for easier test data
	 * generation.
	 * @return The created document.
	 */
	protected Document createDocument() {
		try {
			return DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
		} catch (ParserConfigurationException e) {
			throw new RuntimeException("Please spare me the details.", e);
		}
	}

	/**
	 * Provides the input for this test-case which will be fed into the engine.
	 * @return The input as a string.
	 */
	protected abstract String getInput();

	/**
	 * Provides a table of all expected data dependencies and what they should
	 * be resolved with. Each entry consists of an expected identifier, an
	 * expected request and some object to resolve it with.
	 * @return The table of all data dependencies.
	 */
	protected abstract Object[][] getDependencies();

	/**
	 * Provides the expected output for this test-case as a reference so that
	 * the actual output can be verified.
	 * @return The expected output as a string.
	 */
	protected abstract String getOutput();
}
