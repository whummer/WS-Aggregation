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

import java.io.StringReader;

import junit.framework.Assert;

import org.junit.Test;

import at.ac.tuwien.infosys.aggr.waql.ParseException;
import at.ac.tuwien.infosys.aggr.waql.Parser;
import at.ac.tuwien.infosys.aggr.waql.SimpleNode;

public class ParserTest
{
	private static Parser prepare(String input) {
		return new Parser(new StringReader(input));
	}

	@Test
	public void testDependencyIdentifierGiven() throws ParseException {
		SimpleNode n = prepare("$23{//foo/bar}").pDataDependency();
		Assert.assertEquals(23, n.jjtGetValue());
		Assert.assertEquals(1, n.jjtGetNumChildren());
	}

	@Test
	public void testDependencyIdentifierMissing() throws ParseException {
		SimpleNode n = prepare("${//foo/bar}").pDataDependency();
		Assert.assertEquals(null, n.jjtGetValue());
		Assert.assertEquals(1, n.jjtGetNumChildren());
	}

	@Test(expected = NumberFormatException.class)
	public void testDependencyIdentifierOverflow() throws ParseException {
		prepare("$999999999999{//foo/bar}").pDataDependency();
	}

	@Test(expected = ParseException.class)
	public void testDependencyPathMalformed() throws ParseException {
		prepare("${<bug>}").pDataDependency();
	}

	@Test(expected = ParseException.class)
	public void testDependencyPathMissing() throws ParseException {
		prepare("${}").pDataDependency();
	}

	@Test
	public void testTemplateIdentifierGiven() throws ParseException {
		SimpleNode n = prepare("$23(<foo/>)").pTemplateList();
		Assert.assertEquals(23, n.jjtGetValue());
		Assert.assertEquals(1, n.jjtGetNumChildren());
	}

	@Test
	public void testTemplateIdentifierMissing() throws ParseException {
		SimpleNode n = prepare("$(<foo/>)").pTemplateList();
		Assert.assertEquals(null, n.jjtGetValue());
		Assert.assertEquals(1, n.jjtGetNumChildren());
	}

	@Test(expected = NumberFormatException.class)
	public void testTemplateIdentifierOverflow() throws ParseException {
		prepare("$999999999999(<foo/>)").pTemplateList();
	}

	@Test
	public void testTemplateList1() throws ParseException {
		SimpleNode n = prepare("$(<foo/>,<bar>x</bar>,<baz/>)").pTemplateList();
		Assert.assertEquals(3, n.jjtGetNumChildren());
	}

	@Test
	public void testTemplateList2() throws ParseException {
		SimpleNode n = prepare("$('foo','bar','some string','another,string')").pTemplateList();
		Assert.assertEquals(4, n.jjtGetNumChildren());
	}

	@Test
	public void testTemplateList3() throws ParseException {
		SimpleNode n = prepare("$(\"foo\",\"bar,baz\")").pTemplateList();
		Assert.assertEquals(2, n.jjtGetNumChildren());
	}

	@Test
	public void testTemplateList4() throws ParseException {
		SimpleNode n = prepare("$(2, 3, 5, 7, 11, 13, 17, 19, 23, 29)").pTemplateList();
		Assert.assertEquals(10, n.jjtGetNumChildren());
	}

	@Test
	public void testTemplateListMixed() throws ParseException {
		SimpleNode n = prepare("$('foo', \"bar\", <test></test>, 23)").pTemplateList();
		Assert.assertEquals(4, n.jjtGetNumChildren());
	}

	@Test(expected = ParseException.class)
	public void testTemplateListEmpty() throws ParseException {
		prepare("$()").pTemplateList();
	}

	@Test(expected = ParseException.class)
	public void testTemplateListMarlformed() throws ParseException {
		prepare("$(<foo/>,)").pTemplateList();
	}

	@Test
	public void testDollarEscaped() throws ParseException {
		prepare("<test>$$</test>").parse();
	}

	@Test(expected = ParseException.class)
	public void testDollarUnescaped() throws ParseException {
		prepare("<test>$</test>").parse();
	}
}
