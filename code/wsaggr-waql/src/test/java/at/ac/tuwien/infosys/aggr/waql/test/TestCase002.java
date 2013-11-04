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

public class TestCase002 extends TestBase
{
	@Override
	protected String getInput() {
		return
			"(: Test all those nasty little XQuery constructs with explicit\n" +
			"   whitespace handling and special lexical matcher rules. How\n" +
			"   do you put a smiley :-) without a nose : ) into a comment?" +
			"   Hell did you know you can even (:(:(::) nest :) comments :):)\n" +
			"<!-- First we want to construct a XML comment - and test - - if\n" +
			"     characters are tokenized -> correctly. -->,\n" +
			"<foo><![CDATA[ Next we test the even funnier CDATA stuff and\n" +
			"               include ]] some ]> nasty ] ]> tricks. ]]></foo>,\n" +
			"<?foo Ever used processing instructions before? > Me neither!\n" +
			"      But the WAQL preprocessor should deal with them was well. ?>,\n" +
			"(#foo Last but not least we also check for correct handling of\n" +
			"      pragma constructs with special # characters # ) in them. #){}";
	}

	@Override
	protected Object[][] getDependencies() {
		return new Object[][] {};
	}

	@Override
	protected String getOutput() {
		return getInput();
	}
}
