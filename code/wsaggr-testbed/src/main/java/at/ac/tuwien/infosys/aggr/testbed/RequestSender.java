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

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.Socket;

import org.apache.log4j.Logger;

import at.ac.tuwien.infosys.aggr.testbed.config.Config;
import at.ac.tuwien.infosys.aggr.testbed.messaging.Request;
import at.ac.tuwien.infosys.util.Util;


public class RequestSender {
	String host;
	int port;
	int retries = 0;
	
	private static final Logger LOGGER = Util.getLogger(RequestSender.class);
	
	/**
	 * Constructor for RequestSender
	 * @param host		Host to connect to
	 * @param retries	retries of zero indicates to try infinitely
	 * @throws ConnectException If the connection fails
	 */
	public RequestSender(String host, int retries) throws ConnectException, IOException {
		this.host = host;
		this.retries = retries;
		connectToHost();
	}
	
	public RequestSender(String host) throws ConnectException, IOException {
		this.host = host;
		connectToHost();
	}
	
	/**
	 * Connects to the host and acquires the oos (ObjectOutputStream) for this
	 * RequestSender. Will retry connecting according to the value set in
	 * retries. If it's set to 0, will try infinitely.
	 * @throws ConnectException	if all retries are used and connection couldn't
	 * 							be established.
	 */
	private Socket connectToHost() throws ConnectException, IOException {
		port = Config.requestPort;
		
		//disconnect();
		
		int tried = 0;
		boolean noStream = true;
		while(noStream) {
			try {
				LOGGER.info("Getting connection to " + host + ":" + port);
				Socket socket = new Socket(InetAddress.getByName(host), port);
				
				if(noStream)
					return socket;
				
				noStream = false;
			} catch (Exception e) {
				noStream = true;
				LOGGER.info("Got Exception: " + e.getMessage() + ". Waiting for RequestServer to respond..");
				try {
					Thread.sleep(500);
				} catch (InterruptedException e1) {
					LOGGER.error(e1.getMessage());
				}
				if(retries > 0) {
					tried++;
					if(tried == retries) {
						LOGGER.warn("Couldn't establish connection to Node " + host);
						throw new ConnectException();
					}
				}
				continue;
			}
		}
		return null;
	}
	
	public void sendRequest(Request request) throws IOException {
		
		Socket socket = connectToHost();
		ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream());
		
		oos.writeObject(request);
		oos.flush();
		oos.reset();
		oos.close();
		socket.close();
		LOGGER.info("Request sent");
	}
}