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

import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.ServerSocket;
import java.net.Socket;

import at.ac.tuwien.infosys.aggr.testbed.messaging.Request;

public class RequestServer {
	private boolean running;
	
	private int port;
	
//	Socket socket = null;
	ServerSocket serverSocket = null;
//	ObjectInputStream ois = null;
//	MyClassLoader classloader = null;

	private final Object lock = new Object(); // only one thread should perform deployment work at a time..
	
	public RequestServer(int port) {
		this.port = port;
	}
	
	public static void main(String[] args) throws Exception {
		int port = 1234;
		
		if(args.length == 1 && !args[0].trim().isEmpty()) {
			port = Integer.parseInt(args[0]);
		}
		
		new RequestServer(port).run();
	}
	
//	public static class MyClassLoader extends ClassLoader {
//		
//		private Set<Class<?>> classes = new HashSet<Class<?>>();
//		
//		protected synchronized Class<?> loadClass(String name, boolean resolve)
//				throws ClassNotFoundException {
//			for(Class<?> c : classes) {
//				if(c.getName().equals(name))
//					return c;
//			}
//			return super.loadClass(name, resolve);
//		}
//	}
	
	public void run() throws Exception {
		BootstrapWrapper bootstrapper = new BootstrapWrapper();
		final RequestExecuter executer = new RequestExecuter(this, bootstrapper);
		
		running = true;

		try {
			
			System.out.println("Starting up listener..");
			serverSocket = new ServerSocket(port, 0);
			
			//classloader = new MyClassLoader();
			
			while(running) {
				
				try {

					System.out.println("Listener started, waiting for connections..");
					
					final Socket socket = serverSocket.accept();
					
					new Thread() {
						public void run() {
							ObjectInputStream ois = null;
							try {
								
								System.out.println("Got connection..");
								
								ois = new ObjectInputStream(socket.getInputStream());
								
								System.out.println("Got input stream..");
								
								Request request;
								while((request = (Request) ois.readObject()) != null) {
									synchronized (lock) {
										System.out.println("got request: " + request);
										request.execute(executer);
									}
								}
							} catch (EOFException e) {
								System.out.println("Client closed socket");
							} catch (Exception e) {
								e.printStackTrace();
							} finally {
								try {
									socket.close();
								} catch (Exception e2) {
									e2.printStackTrace();
								}
							}
						}
					}.start();
					
				} catch (Exception e) {
					e.printStackTrace();
				} 
				
			} 
			Thread.sleep(100);
		} catch (Exception e) {
			System.out.println(e.getMessage());
		} finally {
			closeSockets();
		}
	}
	
	public void stop() {
		running = false;
		closeSockets();
		System.exit(0);
	}
	
	private void closeSockets() {
		try {
//			if(ois != null)
//				ois.close();
//			if(socket != null)
//				socket.close();
			if(serverSocket != null)
				serverSocket.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
