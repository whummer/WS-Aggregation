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

import java.util.Arrays;
import java.util.List;

public class TestCase005 extends TestBase
{
	@Override
	protected String getInput() {
		return
			"<pair>\n" +
			"   <id>$1(1 to count(${//bar1/text()}))</id>\n" +
			"   <foo>$1(${//bar2/text()})</foo>\n" +
			"</pair>";
	}

	@Override
	protected Object[][] getDependencies() {
		List<String> list = Arrays.asList("B", "A", "C", "A");
		return new Object[][] {
			{ null, "//bar1/text()", list },
			{ null, "//bar2/text()", list },
		};
	}

	@Override
	protected String getOutput() {
		return
			"let $_waql_1_1 := (('B','A','C','A'))\n" +
			"for $_waql_1 at $_waql_1_cnt in (1 to count(('B','A','C','A')))\n" +
			"return (\n" +
			"<pair>\n" +
			"   <id>{$_waql_1}</id>\n" +
			"   <foo>{$_waql_1_1[$_waql_1_cnt]}</foo>\n" +
			"</pair>\n" +
			")";
	}
}
