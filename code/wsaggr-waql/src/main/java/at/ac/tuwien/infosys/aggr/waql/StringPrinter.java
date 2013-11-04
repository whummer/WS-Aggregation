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

/**
 * A data printer handling {@link java.lang.String} objects.
 * @author Michael Starzinger <michael.starzinger@antforge.org>
 */
public class StringPrinter implements DataPrinter
{
	@Override
	public boolean canHandle(Object object) {
		return (object instanceof String);
	}

	@Override
	public String printAsExpression(Object object, DataPrinter pipeline) {
		String s = (String) object;
		StringBuilder builder = new StringBuilder();
		builder.append('\'');
		builder.append(s.replaceAll("'", "''"));
		builder.append('\'');
		return builder.toString();
	}

	@Override
	public String printAsText(Object object, DataPrinter pipeline) {
		String s = (String) object;
		return s;
	}
}
