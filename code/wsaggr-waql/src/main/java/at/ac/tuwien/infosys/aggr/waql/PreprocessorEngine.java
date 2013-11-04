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

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collection;

/**
 * The WAQL preprocessor engine is the central preprocessor API also
 * representing the current preprocessor state. There are three main
 * state-changing operations for the engine, which are typically called in the
 * given order:
 * <ol>
 * <li><b>Parsing</b>: Reads and parses the WAQL query in textual form from
 *    the input and constructs an intermediate representation out of it. The
 *    input is read as a whole and any syntactical or grammatical anomalies
 *    will be discovered during this phase.</li>
 * <li><b>Resolving</b> of data dependencies: At this point the engine has
 *    generated a list of all unresolved data dependencies contained in the
 *    WAQL query. Those dependencies have to be resolved by the application
 *    before the actual transformation can be done.</li>
 * <li><b>Transformation</b>: Writes the final XQuery (without any WAQL
 *    extensions) to the output. This is the last phase in the preprocessor
 *    life-cycle.</li>
 * </ol>
 * @author Michael Starzinger <michael.starzinger@antforge.org>
 */
public interface PreprocessorEngine
{
	/**
	 * Parses a WAQL query in textual form from the given input stream. The
	 * input is read as a whole and any syntactical oder grammatical anomalies
	 * will be discovered during this phase.
	 * @param input An input stream of the WAQL query to be consumed.
	 * @throws MalformedQueryException In case of syntactical oder grammatical
	 * errors in the input.
	 */
	public void parse(InputStream input) throws MalformedQueryException;

	/**
	 * Returns available unresolved data dependencies from the original query.
	 * Those dependencies which already have been resolved will be excluded.
	 * In case of nested dependencies only the innermost one will be included.
	 * Note that you should regularly retrieve the dependencies after resolving
	 * with {@link #resolveDependency} because new ones might become available.
	 * @return The collection of available unresolved data dependencies.
	 */
	public Collection<DataDependency> getDependencies();

	/**
	 * Resolves the given data dependency by providing actual content. The
	 * content object will be inserted into the original query in place of the
	 * data dependency. The conversion of the content object into a textual
	 * representation will be done by the printer pipeline configured for this
	 * engine, see {@link DataPrinter} for details about that. Note that
	 * resolving dependencies can generate new ones in case of nested
	 * dependencies in the input. The application should therefore call
	 * {@link #getDependencies} afterwards to retrieve those new dependencies.
	 * @param dependency The data dependency to be resolved.
	 * @param content An object representing the content to be inserted.
	 */
	public void resolveDependency(DataDependency dependency, Object content);

	/**
	 * Adds a new data printer on top of the printer pipeline. Those printers
	 * will be used to convert content objects into a textual representation.
	 * Take a look at the {@link DataPrinter} documentation for details about
	 * how the printer pipeline works.
	 * @param printer The data printer to be added to the pipeline.
	 */
	public void addDataPrinter(DataPrinter printer);

	/**
	 * Transforms the WAQL query into a valid XQuery (without any WAQL
	 * extensions) and writes it to the given output stream. Note that all
	 * data dependencies should have been resolved beforehand.
	 * @param output An output stream for the final XQuery.
	 * @throws UnresolvedDependencyException In case the query still contains
	 * unresolved data dependencies.
	 */
	public void transform(OutputStream output) throws UnresolvedDependencyException;
}
