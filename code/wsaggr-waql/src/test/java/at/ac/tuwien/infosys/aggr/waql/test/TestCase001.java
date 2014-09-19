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

public class TestCase001 extends TestBase
{
	@Override
	protected String getInput() {
		return "<test>$23{//foo/bar}</test>";
	}

	@Override
	protected Object[][] getDependencies() {
		return new Object[][] {
			{ 23, "//foo/bar", "<result>42</result>" },
		};
	}

	@Override
	protected String getOutput() {
		return "<test><result>42</result></test>";
	}
}