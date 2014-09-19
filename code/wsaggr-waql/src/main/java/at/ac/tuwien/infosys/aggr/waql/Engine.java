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

import io.hummer.util.Util;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * The main implementation of a preprocessor engine.
 * @author Michael Starzinger <michael.starzinger@antforge.org>
 * @author Waldemar Hummer <hummer@infosys.tuwien.ac.at>
 */
public class Engine implements PreprocessorEngine
{
	/** The printer pipeline used by this engine. */
	private final DataPrinterPipeline pipeline = new DataPrinterPipeline();

	/** The parser used in the parsing step by this engine. */
	private Parser parser;

	/** The root node of the parsed input. */
	private SimpleNode rootNode;

	/** The set of all parsed dependencies. */
	private Map<SimpleNode,DataDependencyInfo> dependenciesAll;

	/** The set of all currently available unresolved dependencies. */
	private Set<DataDependencyInfo> dependenciesAvailable;

	/** The manager for all parsed template lists. */
	private TemplateListManager templates;
	
	@Override
	public void parse(InputStream input) throws MalformedQueryException {

		// Check if this engine has already parsed input before.
		if (parser != null)
			throw new IllegalStateException("Engine has been used for parsing before.");

		String in = null;
		
		try {
			in = Util.getInstance().io.readFile(input);
		} catch (Exception e) {
			throw new MalformedQueryException("Unable to read query from provided input stream.", e);
		}
		
		// Instantiate a new parser for this engine.
		parser = new Parser(new ByteArrayInputStream(in.getBytes()));

		// Actually fire up the parser.
		try {
			try {
				rootNode = parser.parse();
			} catch (Throwable t) {
				in = QueryPreprocessor.replaceAmpersands(in);
				parser = new Parser(new ByteArrayInputStream(in.getBytes()));
				rootNode = parser.parse();
			}
		} catch (ParseException e) {
			throw new MalformedQueryException("Unknown syntactical or grammatical error while parsing.", e);
		} catch (TokenMgrError e) {
			throw new MalformedQueryException("Unexpected token read from input while parsing.", e);
		} catch (NumberFormatException e) {
			throw new MalformedQueryException("Overflow while parsing integer literal.", e);
		}

		// Generate a list of all parsed data dependencies.
		dependenciesAll = new HashMap<SimpleNode,DataDependencyInfo>();
		dependenciesAvailable = new HashSet<DataDependencyInfo>();
		for (SimpleNode node : parser.tableDataDependency) {
			Integer identifier = (Integer) node.jjtGetValue();
			SimpleNode toReconstruct = (SimpleNode) node;
			if(toReconstruct.children != null) {
				toReconstruct = (SimpleNode) node.jjtGetChild(0);
			}
			String request = reconstructNode(toReconstruct);
			DataDependencyInfo info = new DataDependencyInfo(identifier, request, node);
			dependenciesAll.put(node, info);
			dependenciesAvailable.add(info);

			// Check whether the given dependency is a nested one.
			for (SimpleNode parent = (SimpleNode) node.jjtGetParent(); parent != null; parent = (SimpleNode) parent.jjtGetParent())
				if (dependenciesAll.containsKey(parent)) {
					DataDependencyInfo parentInfo = dependenciesAll.get(parent);
					parentInfo.increaseNestings();
					dependenciesAvailable.remove(parentInfo);
					break;
				}
		}

		// Generate a list of all parsed template lists.
		templates = new TemplateListManager();
		for (SimpleNode node : parser.tableTemplateList) {
			Integer identifier = (Integer) node.jjtGetValue();
			templates.addTemplateList(identifier, node);

			// Check whether the given template is inside the query body.
			for (SimpleNode parent = (SimpleNode) node.jjtGetParent(); parent != null; parent = (SimpleNode) parent.jjtGetParent()) {
				if (parent.equals(parser.nodeQueryBody))
					break;
				if (parent.jjtGetParent() == null)
					throw new MalformedQueryException("Unexpected template list outside of query body.");
			}
		}
	}

	@Override
	public Collection<DataDependency> getDependencies() {

		// Check if this engine has already collected dependencies.
		if (dependenciesAvailable == null)
			throw new IllegalStateException("Engine has not yet parsed any dependencies.");

		// Return an copy of the list of available data dependencies.
		return new ArrayList<DataDependency>(dependenciesAvailable);
	}

	@Override
	public void resolveDependency(DataDependency dependency, Object content) {

		// Check if this engine has already collected dependencies.
		if (dependenciesAll == null || dependenciesAvailable == null)
			throw new IllegalStateException("Engine has not yet parsed any dependencies.");

		// Check if this engine knows about the given dependency.
		/** edited by whu */
		if (!dependenciesAll.values().contains(dependency))
			throw new IllegalArgumentException("Engine does not know about given dependency. (engine: " + this + ", known: " + dependenciesAvailable + ", unknown: " + dependency + " )");
		DataDependencyInfo info = (DataDependencyInfo) dependency;
		SimpleNode node = info.getNode();

		// Actually perform the token replacement.
		replaceNode(node, generateDependencyReplacement(node, content));

		// Remove the resolved data dependency from the list of unresolved ones.
		dependenciesAvailable.remove(dependency);

		// Search for new dependencies which became available just now.
		for (SimpleNode parent = (SimpleNode) node.jjtGetParent(); parent != null; parent = (SimpleNode) parent.jjtGetParent())
			if (dependenciesAll.containsKey(parent)) {
				DataDependencyInfo parentInfo = dependenciesAll.get(parent);
				if (parentInfo.decreaseNestings() <= 0) {
					SimpleNode toReconstruct = (SimpleNode) parentInfo.getNode();
					if(toReconstruct.children != null) {
						toReconstruct = (SimpleNode) toReconstruct.jjtGetChild(0);
					}
					String parentRequest = reconstructNode(toReconstruct);
					parentInfo.setRequest(parentRequest);
					dependenciesAvailable.add(parentInfo);
				}
				break;
			}
	}

	@Override
	public void addDataPrinter(DataPrinter printer) {

		// Delegate this method to the printer pipeline.
		pipeline.addDataPrinter(printer);
	}

	@Override
	public void transform(OutputStream output) throws UnresolvedDependencyException {

		// Check if this engine has already parsed some input.
		if (parser == null || dependenciesAvailable == null || templates == null)
			throw new IllegalStateException("Engine has not yet parsed any input.");

		// Check if all unresolved dependencies have been resolved before.
		if (!dependenciesAvailable.isEmpty())
			throw new UnresolvedDependencyException("There are unresolved data dependencies left: " + dependenciesAvailable);

		// We need to insert the prolog for templates into our token stream.
		if (templates.generateProlog()) {
			prefixNode(parser.nodeQueryBody, templates.getFirstPrologToken(), templates.getLastPrologToken());
			postfixNode(parser.nodeQueryBody, "\n)");
		}

		// We need to replace template lists by their variable names.
		for (SimpleNode node : parser.tableTemplateList)
			replaceNode(node, generateTemplateReplacement(node));

		// We need to "un-escape" all escaped dollar signs.
		for (SimpleNode node : parser.tableEscapeDollar)
			replaceNode(node, "$");

		// Transform everything back into a textual representation.
		reconstructNode(rootNode, output);
	}

	/**
	 * Reconstructs the textual representation of the given node. We actually
	 * iterate over the token stream here, so any prior modification of that
	 * stream will be visible.
	 * @param node The node for which to reconstruct.
	 * @return The textual representation of the node.
	 */
	private static String reconstructNode(SimpleNode node) {
		StringBuilder result = new StringBuilder();
		for (Token token = node.jjtGetFirstToken(); token != null; token = token.next) {
			if (token.specialToken != null) {
				StringBuilder builder = new StringBuilder();
				for (Token specialToken = token.specialToken; specialToken != null; specialToken = specialToken.specialToken)
					builder.insert(0, specialToken);
				result.append(builder);
			}
			result.append(token.image);
			if (token == node.jjtGetLastToken())
				break;
		}
		return result.toString();
	}

	/**
	 * Reconstructs the textual representation of the given node. We actually
	 * iterate over the token stream here, so any prior modification of that
	 * stream will be visible.
	 * @param node The node for which to reconstruct.
	 * @param output The output stream into which the result is written.
	 */
	private static void reconstructNode(SimpleNode node, OutputStream output) {
		PrintStream out = new PrintStream(output);
		for (Token token = node.jjtGetFirstToken(); token != null; token = token.next) {
			if (token.specialToken != null) {
				StringBuilder builder = new StringBuilder();
				for (Token specialToken = token.specialToken; specialToken != null; specialToken = specialToken.specialToken)
					builder.insert(0, specialToken);
				out.print(builder.toString());
			}
			out.print(token.image);
			if (token == node.jjtGetLastToken())
				break;
		}
	}

	/**
	 * Replaces the token stream for the given node. This actually changes the
	 * textual representation for the given node. However the abstract syntax
	 * tree remains unchanged.
	 * @param node The node for which to replace tokens.
	 * @param replacement The textual representation of the replacement.
	 */
	private static void replaceNode(SimpleNode node, String replacement) {
		Token token = node.jjtGetFirstToken();
		token.kind = -1;
		token.image = replacement;
		token.next = node.jjtGetLastToken().next;
		for (SimpleNode parent = (SimpleNode) node.jjtGetParent(); parent != null; parent = (SimpleNode) parent.jjtGetParent())
			if (parent.jjtGetLastToken() == node.jjtGetLastToken())
				parent.jjtSetLastToken(token);
			else
				break;
		node.jjtSetLastToken(token);
	}

	/**
	 * Inserts a prefix into the token stream in front of the given node. This
	 * actually extends the given node by prefixing the given stream of tokens.
	 * However the abstract syntax tree remains unchanged.
	 * @param node The node in front of which to add the prefix.
	 * @param firstToken The first token of the prefix stream to insert.
	 * @param lastToken The last token of the prefix stream to insert.
	 */
	private static void prefixNode(SimpleNode node, Token firstToken, Token lastToken) {
		Token token = node.jjtGetFirstToken();
		Token newToken = new Token();
		newToken.kind = token.kind;
		newToken.image = token.image;
		newToken.next = token.next;
		token.kind = -1;
		token.image = "";
		token.next = firstToken;
		lastToken.next = newToken;
	}

	/**
	 * Inserts a postfix into the token stream behind the given node. This
	 * actually extends the given node by modifying the last token. However the
	 * abstract syntax tree remains unchanged.
	 * @param node The node behind of which to add the postfix.
	 * @param postfix The textual representation of the postfix.
	 */
	private static void postfixNode(SimpleNode node, String postfix) {
		Token token = node.jjtGetLastToken();
		token.kind = -1;
		token.image = token.image.concat(postfix);
	}

	/**
	 * Generates a replacement for the given data dependency node. Uses the
	 * current printer pipeline to convert the content object into a textual
	 * representation.
	 * @param node The given data dependency node.
	 * @param content The content object used as replacement.
	 * @return The textual representation of the replacement.
	 */
	private String generateDependencyReplacement(SimpleNode node, Object content) {
		Parser.UsageType usage = (Parser.UsageType) ((SimpleNode) node.jjtGetParent()).jjtGetValue();
		if(usage == null)
			usage = Parser.UsageType.AS_TEXT;
		switch (usage) {
		case AS_EXPR:
			return pipeline.printAsExpression(content, null);
		case AS_TEXT:
			return pipeline.printAsText(content, null);
		default:
			throw new RuntimeException("Unexpected usage type encountered.");
		}
	}

	/**
	 * Generates a replacement for the given template list node. Uses the
	 * current template list manager to retrieve the variable name.
	 * @param node The given template list node.
	 * @return The textual representation of the replacement.
	 */
	private String generateTemplateReplacement(SimpleNode node) {
		Parser.UsageType usage = (Parser.UsageType) ((SimpleNode) node.jjtGetParent()).jjtGetValue();
		switch (usage) {
		case AS_EXPR:
			return templates.getVariableName(node);
		case AS_TEXT:
			return "{" + templates.getVariableName(node) + "}";
		default:
			throw new RuntimeException("Unexpected usage type encountered.");
		}
	}
}
