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

package at.ac.tuwien.infosys.aggr.util;

import io.hummer.util.Configuration;
import io.hummer.util.Util;
import io.hummer.util.persist.IDocumentCache.DocumentCache;

import org.apache.log4j.Logger;

public class Constants {

	private static final Logger logger = Util.getLogger(Constants.class);
	
	public static final String PROP_PU_NAME = "wsaggr.persist.persistUnitName";

	/** Name of the JPA Persistence Unit (PU) */
	public static final String PU;

	public static final String REGEX_USERNAME = "[a-zA-Z_0-9]+";
	public static final String REGEX_HEXCODE = "[a-fA-F0-9]+";
	public static final String REGEX_EMAIL = "[a-zA-Z\\-\\._0-9]+@[a-zA-Z\\-\\._0-9]+\\.[a-zA-Z]+";

	public static final String CAPTCHA_PRIVATE_KEY = 
			Configuration.getValue(Configuration.PROP_CAPTCHA_PRIVATE_KEY);;
	public static final String CAPTCHA_PUBLIC_KEY = 
			Configuration.getValue(Configuration.PROP_CAPTCHA_PUBLIC_KEY);
	public static final String CAPTCHA_DEFAULT_REMOTE_IP = "128.131.172.28";

	static {
		String pu = "WS-Aggregation_HSQLDB";
		try {
			if(Configuration.containsKey(PROP_PU_NAME)) {
				pu = Configuration.getValue(PROP_PU_NAME);
			}
		} catch (Exception e) { /* swallow */ }
		logger.info("Using JPA Persistence Unit named '" + pu + "'");
		PU = pu;
		DocumentCache.DEFAULT_PERSISTENCE_UNIT.set(PU);
	}
}
