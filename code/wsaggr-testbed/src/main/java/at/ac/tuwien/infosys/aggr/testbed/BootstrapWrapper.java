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

package at.ac.tuwien.infosys.aggr.testbed;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import org.apache.log4j.Logger;

import at.ac.tuwien.infosys.aggr.testbed.config.Config;
import at.ac.tuwien.infosys.util.Util;

/**
 * Wrapper Class for the bootstrap script. Copying stuff from/to remote hosts
 * and starting or stopping stuff can be done using this class.
 * @author Daniel Domberger
 *
 */
public class BootstrapWrapper {
	Config config;
	
	private final String CMD_DEPLOY = "deploy";
	private final String CMD_START_REQUEST_SERVER = "startRequestServer";
	private static final Logger LOGGER = Util.getLogger(BootstrapWrapper.class);
	
	String scriptname;
	
	String username;
	String keyfile;
	String requestPort;
	
	/**
	 * Creates a BootstrapWrapper for remote deployment
	 * @param config
	 */
	public BootstrapWrapper(Config config) {
		this.config = config;
		
		username = Config.OS_USER_DEFAULT;
		if(config.getEC2Config().getSshUser() != null) {
			username = config.getEC2Config().getSshUser().trim();
		}
		keyfile = config.getEC2Config().getPrivateKeyFile();
		scriptname = Config.BOOTSTRAP_SCRIPTNAME;
		requestPort = Integer.toString(Config.requestPort);
		
		assertString(username);
		assertString(keyfile);
		assertString(scriptname);
	}
	
	/**
	 * Creates a BootstrapWrapper for local use only
	 */
	public BootstrapWrapper() {
		scriptname = Config.BOOTSTRAP_SCRIPTNAME;
	}
	
	/**
	 * Deploys stuff and starts up the {@link RequestServer}
	 * @param hostname	Hostname of the machine where stuff should be deployed
	 */
	public void deployStuff(String hostname) {
		LOGGER.info("Deploying stuff on " + hostname);
		String filename = Config.STUFF_FILENAME;
		
		try {
			checkString(hostname);
			checkString(filename);
			
			ProcessBuilder pb = new ProcessBuilder(scriptname, CMD_DEPLOY,
					username, keyfile, hostname, requestPort, filename);
			
			runProcess(pb);
		} catch(Exception e) {
			LOGGER.error("hostname or filename empty. Script will not be run. "
					+ e.getMessage());
		}
	}
	
	public void startRequestServer(String hostname) {
		LOGGER.info("Starting RequestServer on " + hostname);
		
		try {
			checkString(hostname);
			
			LOGGER.debug("Running process");
			ProcessBuilder pb = new ProcessBuilder(scriptname, 
					CMD_START_REQUEST_SERVER, username, keyfile, hostname,
					requestPort);
			
			runProcess(pb);
		} catch(Exception e) {
			LOGGER.error(e.getMessage());
		}
	}
	
	private void runProcess(ProcessBuilder pb) throws IOException {
		pb.redirectErrorStream(true);
		Process p = pb.start();
		String line;
		BufferedReader br =
			new BufferedReader(new InputStreamReader(p.getInputStream()));
		while((line = br.readLine()) != null)
			LOGGER.info(line);
	}
	
	private void assertString(String s) {
		assert(s != null && !s.equals(""));
	}
	
	private void checkString(String s) throws Exception {
		if(s == null) {
			throw new NullPointerException();
		}
		
		if(s.equals("")) {
			throw new Exception("String is empty");
		}
	}
}
