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
 * The WAQL preprocessor factory can instantiate new preprocessor engines
 * which in turn can be used in a new context (i.e. for a new query). The
 * typical lifetime of one engine only lasts a single query.
 * @author Michael Starzinger <michael.starzinger@antforge.org>
 */
public abstract class PreprocessorFactory
{
	/**
	 * Returns a new WAQL preprocessor engine instance.
	 * @return The new preprocessor engine instance.
	 */
	public static PreprocessorEngine getEngine() {
		return new Engine();
	}
}
