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

import javax.xml.bind.annotation.XmlRootElement;

import at.ac.tuwien.infosys.util.Configuration;

@XmlRootElement(namespace=Configuration.NAMESPACE, name="matrixMult")
public class MatrixMultRequest {
	
	private double[][] matrix1;
	private double[][] matrix2;
	
	public MatrixMultRequest() {
		super();
		// TODO Auto-generated constructor stub
	}

	public double[][] getMatrix1() {
		return matrix1;
	}

	public void setMatrix1(double[][] matrix1) {
		this.matrix1 = matrix1;
	}

	public double[][] getMatrix2() {
		return matrix2;
	}

	public void setMatrix2(double[][] matrix2) {
		this.matrix2 = matrix2;
	}

}