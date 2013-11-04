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

import java.util.HashMap;

import javax.annotation.Resource;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import at.ac.tuwien.infosys.ws.RestService;

/**
 * @author Daniel Domberger
 */
@Path("/")
@Resource
public class GenericRestService implements RestService {
	/* TODO For every Request a new Object is instanciated and hence, the
	 * responses HashMap cleared. Possible solution: save the HashMap in
	 * the RequestServer process, and get it from there somehow.
	 */
	private static final long serialVersionUID = 3827679530215595887L;
	
	public HashMap<String, String> responses;
	
	public GenericRestService() {}
	
	public GenericRestService(HashMap<String, String> responses) {
		this.responses = responses;
	}
	
	@GET
	@Path("/{id}")
	@Produces(MediaType.TEXT_PLAIN)
	public String genericPlaintextMethod(@PathParam("id") String id) {
		return getResponse(id);
	}
	
	@GET
	@Path("/{id}")
	@Produces(MediaType.TEXT_XML)
	public String genericHtmlMethod(@PathParam("id") String id) {
		return getResponse(id);
	}
	
	@GET
	@Path("/{id}")
	@Produces(MediaType.TEXT_HTML)
	public String genericXmlMethod(@PathParam("id") String id) {
		return getResponse(id);
	}
	
	@GET
	@Path("/{id}")
	@Produces(MediaType.APPLICATION_JSON)
	public String genericJsonMethod(@PathParam("id") String id) {
		return getResponse(id);
	}
	
	public void setResponses(HashMap<String, String> responses) {
		this.responses = responses;
		System.out.println("Responses set to: " + responses);
	}
	
	public HashMap<String,String> getResponses() {
		return responses;
	}
	
	private String getResponse(String id) {
		System.out.println(responses);
		String response = "blubs: " + id;
		if(responses != null)
			response = responses.get(id);
		else
			System.out.println("Responses are null");
		
		if(response != null)
			return response;

		return "response is null";
	}

	@Override
	public void onDeploy() { }
	@Override
	public void onUndeploy() { }
}
