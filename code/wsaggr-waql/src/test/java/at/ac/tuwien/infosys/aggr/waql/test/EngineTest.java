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

import junit.framework.JUnit4TestAdapter;
import junit.framework.Test;
import junit.framework.TestSuite;

public class EngineTest
{
	public static Test suite() {
		TestSuite suite = new TestSuite();
		for (int i = 1; i <= 999; i++)
			try {
				suite.addTest(new JUnit4TestAdapter(Class.forName(String.format("org.antforge.waqlpp.test.TestCase%03d", i))));
			} catch (ClassNotFoundException e) {
				break;
			}
		return suite;
	}
}
