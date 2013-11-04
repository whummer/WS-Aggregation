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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The template list manager handles the transformation of parsed template
 * lists into the appropriate XQuery language constructs. It is tightly coupled
 * to the engine and parser model, but it encapsulates the required logic to
 * perform the actual language transformation.
 * @author Michael Starzinger <michael.starzinger@antforge.org>
 */
public class TemplateListManager
{
	/** The table containing all added templates already combining those with
	 * same identifiers.*/
	private final Map<Object,List<SimpleNode>> templates =
		new HashMap<Object,List<SimpleNode>>();

	/** The table mapping parsed template list nodes to variable names. */
	private final Map<SimpleNode,String> variables =
		new HashMap<SimpleNode,String>();

	/** The first token of our generated prolog stream. */
	private Token firstPrologToken;

	/** The last token of our generated prolog stream. */
	private Token lastPrologToken;

	/**
	 * Adds a new template list to this manager. The given node object is
	 * required to identify the template list site and to access the token
	 * stream of the actual list.
	 * @param identifier The identifier of the template list.
	 * @param node The parsed node of the template list.
	 */
	public void addTemplateList(Integer identifier, SimpleNode node) {

		// If the template list has no specified identifier we create an
		// anonymous key here, otherwise we reuse the identifier as a key.
		Object key = identifier;
		if (key == null)
			key = new Object();

		// Add the template into our table.
		if (!templates.containsKey(key))
			templates.put(key, new ArrayList<SimpleNode>());
		templates.get(key).add(node);
	}

	/**
	 * Generates the query prolog for all previously added template lists. The
	 * return value indicates if there is some prolog to be inserted into the
	 * query.
	 * @return Boolean value indicating whether there is a prolog to be
	 * inserted into the query or not.
	 */
	public boolean generateProlog() {

		// Check if we actually have to generate a prolog.
		if (templates.isEmpty())
			return false;

		// Initiate the prolog token list.
		firstPrologToken = new Token(-1, "return (\n");
		lastPrologToken = firstPrologToken;

		// Iterate over all combined template lists and handle correlated and
		// non-correlated ones in a single phase.
		int tplCount = 0;
		for (List<SimpleNode> tpls : templates.values()) {
			String tplCountVar = String.format("$_waql_%d_cnt", ++tplCount);

			// First we get the main (first) template list out of the
			// correlated set and generate the actual for-loop construct.
			SimpleNode tplMain = tpls.remove(0);
			String tplMainVar = String.format("$_waql_%d", tplCount);
			variables.put(tplMain, tplMainVar);
			prepend(")\n");
			prepend(tplMain);
			if (tpls.isEmpty())
				prepend(String.format("for %s in (", tplMainVar));
			else
				prepend(String.format("for %s at %s in (", tplMainVar, tplCountVar));

			// Then we handle all templates actually correlated to the main
			// template list we got above.
			int tplCountLinked = 0;
			for (SimpleNode tplLinked : tpls) {
				String tplLinkedVar = String.format("$_waql_%d_%d", tplCount, ++tplCountLinked);
				variables.put(tplLinked, String.format("%s[%s]", tplLinkedVar, tplCountVar));
				prepend(")\n");
				prepend(tplLinked);
				prepend(String.format("let %s := (", tplLinkedVar));
			}
		}

		// We generated a prolog.
		templates.clear();
		return true;
	}

	/**
	 * Returns the first token of a previously generated prolog. If there is no
	 * prolog to be inserted into the query, {@code null} will be returned.
	 * @return The first token of the generated prolog stream or {@code null}.
	 */
	public Token getFirstPrologToken() {
		return firstPrologToken;
	}

	/**
	 * Returns the last token of a previously generated prolog. If there is no
	 * prolog to be inserted into the query, {@code null} will be returned.
	 * @return The last token of the generated prolog stream or {@code null}.
	 */
	public Token getLastPrologToken() {
		return lastPrologToken;
	}

	/**
	 * Returns the variable name for the given template list. The template list
	 * node should be replaced by the provided variable name. Note that the
	 * variable name is returned in expression representation and needs to be
	 * modified when used as content text.
	 * @param node The parsed node of the template list.
	 * @return The textual representation of the variable name.
	 */
	public String getVariableName(SimpleNode node) {

		// Check if the given template list has a variable assigned to it.
		if (!variables.containsKey(node))
			throw new IllegalArgumentException("Manager does no know about given template list.");

		// Return the assigned variable name.
		return variables.get(node);
	}

	/**
	 * Prepends a new token to the existing prolog. The token will contain the
	 * given textual image without any modification and will be placed in front
	 * of all previously generated prolog tokens.
	 * @param image The textual image for the token.
	 */
	private void prepend(String image) {
		Token token = new Token(-1, image);
		token.next = firstPrologToken;
		firstPrologToken = token;
	}

	/**
	 * Prepends existing tokens to the existing prolog. The tokens will be
	 * taken from the children of the given node and will be reused as prolog
	 * tokens.
	 * @param node The node containing the children to use.
	 */
	private void prepend(SimpleNode node) {
		Token firstToken = ((SimpleNode) node.jjtGetChild(0)).jjtGetFirstToken();
		Token lastToken = ((SimpleNode) node.jjtGetChild(node.jjtGetNumChildren() - 1)).jjtGetLastToken();
		lastToken.next = firstPrologToken;
		firstPrologToken = firstToken;
	}
}
