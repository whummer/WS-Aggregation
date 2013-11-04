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

import javax.jws.WebService;

import org.apache.log4j.Logger;

import at.ac.tuwien.infosys.aggr.cloudopt.collaboration.CollaborativeNodeImpl;
import at.ac.tuwien.infosys.util.Configuration;

/**
 * @author basic
 *
 */
@WebService(targetNamespace=Configuration.NAMESPACE, endpointInterface="at.ac.tuwien.infosys.aggr.cloudopt.matrixmult.IMatrixMultService")
public class MatrixMultServiceImpl extends CollaborativeNodeImpl implements IMatrixMultService {
	private static Logger logger = Logger.getLogger(MatrixMultServiceImpl.class);
	
	/**
	 * Matrix multiplication <br />
	 * LxM * MxN = LxN <br />
	 * Relevant is following: number of columns of left matrix must be equal to 
	 * the number of rows of right matrix, otherwise the multiplication is not possible.
	 */
	public MatrixMultResponse matrixMult(MatrixMultRequest request) {
		System.out.println(request);
		System.out.println(request.getMatrix1());
		System.out.println(request.getMatrix2());
		// positions means number of columns in left matrix and number of rows in right matrix
		long nrOfPositions = request.getMatrix1()[0].length;
		if(nrOfPositions != request.getMatrix2().length){
			System.out.println("Nuber of columns of left matrix and nuber of rows in right matrix are not equal: " 
					+ nrOfPositions + " != " + request.getMatrix2().length);
			return null;
		}
		double [][] m1 = request.getMatrix1();
		double [][] m2 = request.getMatrix2();
		double [][] result = new double[m1.length][m2[0].length];
//		int resultRow = 0;
		int columnCounter = 0;
		double temp = 0.0;
		// row position of result matrix
		for(int rowCounter = 0; rowCounter < m1.length; rowCounter++){
			// TODO implement multiplication algorithm
			temp = 0.0;
			// column position of result matrix
			for(columnCounter = 0; columnCounter < m2[0].length; columnCounter++){
				temp += 0.0;
				for(int i = 0; i < nrOfPositions; i++){
					temp += m1[rowCounter][i] * m2[i][columnCounter];
				}
				result[rowCounter][columnCounter] = temp;
			}
		}
		return new MatrixMultResponse(result);
	}

	@Override
	public GetNumOfTasksResponse getNumOfTasks(GetNumOfTasks request) {
		return new GetNumOfTasksResponse();
	}

	@Override
	public long getQueueLength() {
		return 0;
	}
	@Override
	public double getCPU() {
		// TODO Auto-generated method stub
		return 0;
	}
	@Override
	public void setNumOfTasks(int numOfTasks) {
		// TODO Auto-generated method stub
		logger.debug("number of tasks: " + numOfTasks);
	}

}