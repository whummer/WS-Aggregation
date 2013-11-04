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
 * A data dependency is an immutable object representing an unresolved data
 * dependency inside a parsed WAQL query. The object identity itself may be
 * used to identify a single dependency, so do not clone or replicate such an
 * object but pass it by reference instead.
 * @author Michael Starzinger <michael.starzinger@antforge.org>
 * @author Waldemar Hummer <hummer@infosys.tuwien.ac.at>
 */
public interface DataDependency
{
	/**
	 * Returns the identifier of this data dependency as specified in the
	 * original WAQL query. If no identifier was specified this operation will
	 * return <code>null</code> instead.
	 * @return The data dependency identifier or <code>null</code> if none was
	 * specified.
	 */
	public Integer getIdentifier();

	/**
	 * Returns the textual representation of the request inside this data
	 * dependency as specified in the original WAQL query.
	 * @return The textual representation of the request.
	 */
	public String getRequest();
	
	/** we do *not* want to overwrite equals() in any DataDependency, hence the code here.. */
	static final class Comparator {
		public static boolean equal(DataDependency d1, DataDependency d2) {
			if (d1.getIdentifier() == null) {
				if (d2.getIdentifier() != null)
					return false;
			} else if (!d1.getIdentifier().equals(d2.getIdentifier()))
				return false;
			if (d1.getRequest() == null) {
				if (d2.getRequest() != null)
					return false;
			} else if (!d1.getRequest().equals(d2.getRequest()))
				return false;
			return true;
		}
	}
}
