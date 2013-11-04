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
package at.ac.tuwien.infosys.aggr.test;

import java.util.LinkedList;
import java.util.List;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.junit.Ignore;
import org.w3c.dom.Element;

import at.ac.tuwien.infosys.aggr.AggregationClient;
import at.ac.tuwien.infosys.aggr.request.AggregationRequest;
import at.ac.tuwien.infosys.aggr.test.FlowTest.TestSuite.TestRun;
import at.ac.tuwien.infosys.aggr.xml.XPathProcessor;
import at.ac.tuwien.infosys.test.TestServiceStarter;
import at.ac.tuwien.infosys.util.Configuration;
import at.ac.tuwien.infosys.ws.EndpointReference;
import at.ac.tuwien.infosys.util.Util;

@Ignore
public class FlowTest {

	private static Util util = new Util();
	
	public static boolean createTopology = false;
	
	@XmlRootElement
	public static class TestSuite {
		@XmlRootElement
		public static class TestRun {
			@XmlAttribute
			private boolean exclude;
			private AggregationRequest request;
			private Object result;
			private List<String> resultAssertion = new LinkedList<String>();
			public AggregationRequest getRequest() {
				return request;
			}
			public void setRequest(AggregationRequest request) {
				this.request = request;
			}
			public Object getResult() {
				return result;
			}
			public void setResult(Object result) {
				this.result = result;
			}
			@XmlTransient
			public boolean isExclude() {
				return exclude;
			}
			public void setExclude(boolean exclude) {
				this.exclude = exclude;
			}
			public List<String> getResultAssertion() {
				return resultAssertion;
			}
			public void setResultAssertion(List<String> resultAssertion) {
				this.resultAssertion = resultAssertion;
			}
		}
		@XmlElement(name="testRun")
		private List<TestRun> testRuns = new LinkedList<TestRun>();
		@XmlTransient
		public List<TestRun> getTestRuns() {
			return testRuns;
		}
		public void setTestRuns(List<TestRun> testRuns) {
			this.testRuns = testRuns;
		}
	}
	
	public static void main(String[] args) throws Exception {
		
		String in = util.io.readFile(FlowTest.class.getResourceAsStream("flowTest.xml"));
		Element element = util.xml.toElement(in);
		
		TestSuite tests = util.xml.toJaxbObject(TestSuite.class, element);
	
		System.out.println("starting default setup");
		TestServiceStarter.setupDefault(2);
		

		String address = Configuration.getUrlWithVariableHost(
				Configuration.PROP_GATEWAY_URL, Configuration.PROP_HOST);
		AggregationClient c = new AggregationClient(new EndpointReference(
			"<wsa:EndpointReference xmlns:wsa=\"http://schemas.xmlsoap.org/ws/2004/08/addressing\" " +
				"xmlns:tns=\"" + Configuration.NAMESPACE + "\">" +
				"<wsa:Address>" + address + "</wsa:Address>" +
				"<wsa:ServiceName PortName=\"GatewayPort\">" +
					"tns:GatewayService" +
				"</wsa:ServiceName>" +
			"</wsa:EndpointReference>"));
		
		/*long t1 = System.currentTimeMillis();
		String s = Util.readFile("tmp/test2.xml");
		long t2 = System.currentTimeMillis();
		System.out.println(Math.abs(t1-t2));
		Element e1 = Util.toElement(s);
		t1 = System.currentTimeMillis();
		System.out.println(e1 + " - " + Math.abs(t1-t2) + " - " + MemoryAgent.deepSizeOf(e1));
		System.exit(0);*/
		
		// TODO temp
		boolean temp = true;
		if(temp) {
			
			AggregationRequest r1 = util.xml.toJaxbObject(AggregationRequest.class, 
					util.xml.toElement(util.io.readFile("etc/queries/wienerlinien.xml")));
			Element e1 = c.aggregate(r1);
			util.xml.print(e1);
			System.exit(0);
			
			int stepSize = 50;
			for(int i = 1; i <= 301; i += stepSize) {
				AggregationRequest r = util.xml.toJaxbObject(AggregationRequest.class, 
						util.xml.toElement(util.io.readFile("etc/queries/imdbMovieInfo.xml")
								.replace("::querySize::", ""+stepSize).replace("::queryStart::", ""+i)
								.replace("::queryEnd::", ""+(i+stepSize))));
				System.out.println(r);
				Element e = c.aggregate(r);
				util.xml.print(e);
			}
			System.exit(0);
		}
		// TODO temp
		
		
		for(TestRun test : tests.testRuns) {
		
			if(!test.exclude) {
				AggregationRequest request = test.request;
				
				String tID = null;
				if(createTopology) {
					tID = c.createTopology("tree(2,2)", new String[]{"Voting"});
				}
				
				long time1 = System.currentTimeMillis();
				Element result = c.aggregate(tID, request.getQueries(), request.getInputs());
				long time2 = System.currentTimeMillis();
				result = util.xml.cloneCanonical(result);
				util.xml.print(result);
				System.out.println("Processing took " + Math.abs(time1-time2) + "ms");
	
				String result1 = util.xml.toStringCanonical(result);
				if(test.result != null) {
					String result2 = util.xml.toStringCanonical((Element)test.result);
					if(!result1.equals(result2) && !result.equals(test.result)) {
						throw new RuntimeException("Unexpected test result:\n" + result1 + "\nexpected:\n" + result2);
					}
				}
				for(String ass : test.resultAssertion) {
					if(!XPathProcessor.matches(ass, result)) {
						throw new RuntimeException("Result assertion '" + ass + "' failed for result: " + result1);
					}
				}
				
				Thread.sleep(500);
			}
		}
		
		System.exit(0);
	}
	
}
