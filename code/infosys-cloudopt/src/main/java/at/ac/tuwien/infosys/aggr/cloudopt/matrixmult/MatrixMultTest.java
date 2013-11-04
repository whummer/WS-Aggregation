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

package at.ac.tuwien.infosys.aggr.cloudopt.matrixmult;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import net.java.dev.eval.Expression;

import org.opt4j.core.Objective;
import org.opt4j.core.Objective.Sign;

import at.ac.tuwien.infosys.aggr.cloudopt.collaboration.ICollaborativeNode.MyString;
import at.ac.tuwien.infosys.util.Configuration;
import at.ac.tuwien.infosys.ws.DynamicWSClient;
import at.ac.tuwien.infosys.ws.EndpointReference;
import at.ac.tuwien.infosys.ws.IAbstractNode.TerminateRequest;

public class MatrixMultTest {
	

	public static void main(String[] args) throws Exception {
		
		MatrixMultServiceImpl s = new MatrixMultServiceImpl();
		String url = Configuration.getUrlWithVariableHost("test.cloudopt.matrix.service.address");
		System.out.println("MatrixMultTest.main() - url: " + url);
		s.setEPR(new EndpointReference(new URL(url)));
		s.deploy(url);
		IMatrixMultService client = DynamicWSClient.createClient(IMatrixMultService.class, new URL(url + "?wsdl"));
		MatrixMultRequest request = new MatrixMultRequest();
		request.setMatrix1(new double[][]{new double[]{1,2,3,4,5},new double[]{6,7,8,9,10}});
		request.setMatrix2(new double[][]{new double[]{1,2,3},new double[]{2,3,4},new double[]{3,4,5},new double[]{4,5,6},new double[]{5,6,7}});
		System.out.println(request);
		System.out.println(request.getMatrix1());
		System.out.println(request.getMatrix2());
		MatrixMultResponse response = client.matrixMult(request);
//		client.registerNewNode(new EndpointReference("zgembo element"));
		System.out.println(response.matrixToString(response.getResult()));
		MyString hallo = new MyString();
		hallo.setString("hey hey hey");
		client.hello(hallo);
		
		s.terminate(new TerminateRequest());
		
		
		// reflection:
		
		System.out.println("Annotations: ");
		for (Annotation a : IMatrixMultService.class.getDeclaredAnnotations()) {
			System.out.println(a);
		}
		System.out.println();
		System.out.println("Methods:");
		for (Method m : IMatrixMultService.class.getDeclaredMethods()) {
			System.out.println(m);
		}
		System.out.println();
		System.out.println("Interfaces:");
		for (Class i : IMatrixMultService.class.getInterfaces()) {
			System.out.println(i.getName());
		}
		
		// eval 4 java:
		Expression exp = new Expression("(x + y)/2");
		 
		 Map<String, BigDecimal> variables = new HashMap<String, BigDecimal>();
		 variables.put("x", new BigDecimal("4.32"));
		 variables.put("y", new BigDecimal("342.1"));
		 
		 BigDecimal result = exp.eval(variables);
		 
		 System.out.println(result);

		 // opt4j
		 
		 Objective objective = new Objective("objective", Sign.MAX);
		 
//		 SelectGenotype<Character> genotype = new SelectGenotype<Character>(chars);
//         genotype.init(random, 11);
//         
//         EvolutionaryAlgorithm a = new EvolutionaryAlgorithm(population, archive, individualFactory, completer, control, selector, mating, iteration, alpha, mu, lambda)
//         a.optimize();
         
         // 
         
	}

}
