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
 * A data printer is used to convert content objects into a textual
 * representation during data dependency resolving. There are predefined
 * printers available for the most common object types, but additional ones
 * implementing this interface can be used. Those printers will be chained to
 * form a printer pipeline. The first one able to handle a given object will be
 * called to do the actual conversion. The last printer in the chain should be
 * a default printer which can handle any {@link java.lang.Object} to ensure
 * conversion is possible for all types.
 * @author Michael Starzinger <michael.starzinger@antforge.org>
 */
public interface DataPrinter
{
	/**
	 * Checks whether the given content object can be handled by this data
	 * printer. In most cases an <code>instanceof</code> statement will be used
	 * to check for a specific object type.
	 * @param object The object which should be converted.
	 * @return The boolean value indicating whether the given object can be
	 * handled by this data printer.
	 */
	public boolean canHandle(Object object);

	/**
	 * Converts the given content object into a textual representation when
	 * used as an expression. Used when the data dependency is stated as a
	 * XQuery expression like in this example:
	 * <p><code>
	 *    let $var := <b>${//anypath}</b> return
	 *    &lt;test&gt;{$var}&lt;/test&gt;
	 * </code><p>
	 * @param object The object which should be converted.
	 * @param pipeline The top of the printer pipeline. Useful for recursive
	 * types like collections or arrays.
	 * @return The textual representation of the given object.
	 */
	public String printAsExpression(Object object, DataPrinter pipeline);

	/**
	 * Converts the given content object into a textual representation when
	 * used as text. Used when the data dependency is stated as actual content
	 * of a XQuery direct constructor like in this example:
	 * <p><code>
	 *    &lt;test&gt;<b>${//anypath}</b>&lt;/test&gt;
	 * </code></p>
	 * @param object The object which should be converted.
	 * @param pipeline The top of the printer pipeline. Useful for recursive
	 * types like collections or arrays.
	 * @return The textual representation of the given object.
	 */
	public String printAsText(Object object, DataPrinter pipeline);
}
