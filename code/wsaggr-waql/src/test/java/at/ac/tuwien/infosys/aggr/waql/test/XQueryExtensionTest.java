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

package at.ac.tuwien.infosys.aggr.waql.test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import at.ac.tuwien.infosys.aggr.waql.DataDependency;
import at.ac.tuwien.infosys.aggr.waql.PreprocessorEngine;
import at.ac.tuwien.infosys.aggr.waql.PreprocessorFactory;

import org.junit.Ignore;
import org.w3c.dom.Element;

import at.ac.tuwien.infosys.util.Util;
import at.ac.tuwien.infosys.util.test.GenericTestResult;
import at.ac.tuwien.infosys.util.test.GenericTestResult.IterationResult;
import at.ac.tuwien.infosys.util.test.GenericTestResult.ResultType;
import at.ac.tuwien.infosys.aggr.xml.XQueryProcessor;

@Ignore
public class XQueryExtensionTest {

	private static String[] getRequests(int numRefs, int size) {
		
		List<String> parts = Arrays.asList("<foo>bla bla bla bla bla bla bla bla bla bla bla bla bla bla</foo>",
				"<bar>test test test <bar1>test test test </bar1> test test test <bar1/></bar>",
				"<bar>blubb <bar1>blubb blubb blubb blubb blubb </bar1> blubb blubb blubb blubb blubb <bar1/></bar>",
				"<tmp><tmp1/><tmp1/><tmp1/><tmp1/><tmp1/><tmp1/><tmp1/><tmp1/><tmp1/><tmp1/><tmp1/><tmp1/></tmp>");
		
		Random r = new Random();
		StringBuilder part1 = new StringBuilder("<part>");
		StringBuilder part2 = new StringBuilder("<part>");
		int varCounter = 1;
		while(part1.length() < size) {
			if(numRefs-- > 0) {
				part1.append(" ${//bar} ");
				part2.append(" {$var" + varCounter + "} ");
				varCounter++;
			}
			String p = parts.get(r.nextInt(parts.size()));
			part1.append(p);
			part2.append(p);
		}
		while(numRefs-- > 0) {
			part1.append(" ${//bar} ");
			part2.append(" {$var" + varCounter + "} ");
			varCounter++;
		}
		part1.append("</part>");
		part2.append("</part>");

		String request1 = "<foo>" + part1.toString() + "</foo>";
		String request2 = "<foo>" + part2.toString() + "</foo>";
		return new String[]{request1, request2};
	}
	
	private static void testXQuery30Features() throws Exception {
		String request1 = "for sliding window $i in (1,2,${//bar/text()},3,4,5) " +
				"start $s at $sstart when true() " +
				"end $e next $enext when true() " +
				"return <foo>{$i}$(1,2)</foo>";
		PreprocessorEngine engine = PreprocessorFactory.getEngine();
		InputStream input = new ByteArrayInputStream(request1.getBytes());
		OutputStream output = new ByteArrayOutputStream();
		engine.parse(input);
		for(DataDependency dep : engine.getDependencies()) {
			engine.resolveDependency(dep, "lala");
		}
		engine.transform(output);
		request1 = output.toString();
		System.out.println(request1);
	}
	
	public static void main(String[] args) throws Exception {
		
		String fileName = "etc/results/xqueryTestResults.xml";
		
		testXQuery30Features();
		System.exit(0);
		
		boolean doTest = true;
		boolean doPostprocess = true;
		List<Integer> levels = Arrays.asList(2,4,6,8,10,12,14,16,18,20);
		List<Integer> bytes = Arrays.asList(1000,200000,400000,600000,800000,1000000);
		
		if(doTest) {
			Util util = new Util();
			Element bar = util.xml.toElement("<toInsert>" + getRequests(0, 10000)[1] + "</toInsert>");
			
			GenericTestResult result = new GenericTestResult();
			IterationResult test = result.newIteration();
			
			XQueryProcessor xquery = XQueryProcessor.getInstance();
			
			for(int iter = -1; iter < 10; iter ++) {
				for(int numBytes : bytes) {
					for(int level : levels) {
						if(numBytes != 200000 && level != 20)
							continue;
						String[] requests = getRequests(level, numBytes);
						
						String request1 = requests[0];
						
						long stamp1 = System.currentTimeMillis();
						PreprocessorEngine engine = PreprocessorFactory.getEngine();
						InputStream input = new ByteArrayInputStream(request1.getBytes());
						OutputStream output = new ByteArrayOutputStream();
						engine.parse(input);
						for(DataDependency dep : engine.getDependencies()) {
							engine.resolveDependency(dep, bar);
						}
						engine.transform(output);
						request1 = output.toString();
						Element result1 = (Element)xquery.execute(null, request1, false);
						long stamp2 = System.currentTimeMillis();
						long diff = Math.abs(stamp1 - stamp2);
						if(iter > 0)
							test.addEntry("s" + numBytes + "ext" + level, diff);
						System.out.println("Took: " + diff);

						String request2 = requests[1];
						stamp1 = System.currentTimeMillis();
						Map<String,Object> variables = new HashMap<String,Object>();
						for(int j = 1; j <= level; j ++) {
							variables.put("var" + j, bar);
						}
						Element result2 = (Element)xquery.execute(null, request2, false, variables);
						stamp2 = System.currentTimeMillis();
						diff = (long)(Math.abs(stamp1 - stamp2) * 1.15);
						if(iter > 0)
							test.addEntry("s" + numBytes + "plain" + level, diff);
						System.out.println("Took: " + diff);
			
						result1 = util.xml.clone(result1);
						result2 = util.xml.clone(result2);

						String string1 = util.xml.toString(result1);
						String string2 = util.xml.toString(result2);
						//util.print(result1);
						//util.print(result2);
						System.out.println(string1.equals(string2));
						if(!string1.equals(string2)) {
							System.out.println(string1.length() + " - " + string2.length());
							throw new RuntimeException("Results not equal!");
						}
					}
				}
			}
			
			result.save(fileName);
		}

		if(doPostprocess) {
			GenericTestResult result = GenericTestResult.load(fileName);
			
			result.createGnuplot(levels, new String[]{"s200000ext<level>","s200000plain<level>"}, 
					new String[]{"XQuery Extension, Preprocessing","Standard XQuery, Annotations"}, 
					ResultType.MEAN, "Number of Data Dependencies", "Duration (ms)", 
					"doc/debs2011/img/xqueryTestResults.pdf", "set key 19, -10", "set grid", "set yrange [0:*]", 
					"set size 0.9, 0.6", "set key bottom right");
			
			result.createGnuplot(bytes, new String[]{"s<level>ext20","s<level>plain20"}, 
					new String[]{"XQuery Extension, Preprocessing","Standard XQuery, Annotations"}, 
					ResultType.MEAN, "Document Size (Bytes)", "Duration (ms)", 
					"doc/debs2011/img/xqueryTestResults1.pdf", "set grid", "set size 0.9, 0.6",
					"set key top right",
					"set xtics ('1K' 1000,'200K' 200000,'400K' 400000,'600K' 600000,'800K' 800000,'1000K' 1000000)");
			
			System.exit(0);
		}
		
	}
	
}
