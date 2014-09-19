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

import io.hummer.util.Configuration;

@XmlRootElement(namespace=Configuration.NAMESPACE)
public class MatrixMultResponse {
	private double[][] result; // = {{1,2,3,4},{1,2,3,4},{1,2,3,4},{1,2,3,4}};
	
	public MatrixMultResponse() {
		super();
	}

	public MatrixMultResponse(double[][] result) {
		super();
		this.result = result;
	}

	/**
	 * Return the matrix as a printable string.
	 * @param matrix
	 * @return
	 */
	public String matrixToString(double[][] matrix) {
		if(matrix == null)
			return "matrix is null";
		String result = "";
		for(int i = 0; i < matrix[0].length; i++){
			result += "\tc" + i;
		}
		result += "\n";
		for (int i = 0; i < matrix.length; i++) {
			result += "l" + i;
			for(int j = 0; j < matrix[i].length; j++){
				result += "\t" + matrix[i][j];
			}
			result += "\n";
		}
		return result;
	}

	public double[][] getResult() {
		return result;
	}

	public void setResult(double[][] result) {
		this.result = result;
	}
}
