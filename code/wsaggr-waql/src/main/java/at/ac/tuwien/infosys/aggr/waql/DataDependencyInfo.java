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
 * The main implementation of a data dependency.
 * @author Michael Starzinger <michael.starzinger@antforge.org>
 */
public class DataDependencyInfo implements DataDependency
{
	/** The identifier of this data dependency as specified in the query. */
	private final Integer identifier;

	/** The textual representation of the request of this data dependency. */
	private String request;

	/** The parsed node represented by this data dependency. */
	private final SimpleNode node;

	/** The number of nested data dependencies inside this one. */
	private int nestings;

	public DataDependencyInfo(Integer identifier, String request, SimpleNode node) {
		super();
		this.identifier = identifier;
		this.request = request;
		this.node = node;
	}

	@Override
	public Integer getIdentifier() {
		return identifier;
	}

	@Override
	public String getRequest() {
		return request;
	}

	/**
	 * Updates the textual representation of the request for this data
	 * dependency. This is useful if the request changes after nested data
	 * dependencies have been resolved.
	 * @param request The new textual representation of the request.
	 */
	public void setRequest(String request) {
		this.request = request;
	}

	/**
	 * Returns the parsed node represented by this data dependency.
	 * @return The parsed node.
	 */
	public SimpleNode getNode() {
		return node;
	}

	/**
	 * Increases the number of nested data dependencies inside this one.
	 * @return The new number of nested data dependencies.
	 */
	public int increaseNestings() {
		return ++nestings;
	}

	/**
	 * Decreases the number of nested data dependencies inside this one.
	 * @return The new number of nested data dependencies.
	 */
	public int decreaseNestings() {
		return --nestings;
	}
	
	
	public String toString() {
		return "[D $" + (identifier == null ? "" : identifier) + "{" + request + "}]";
	}
}
