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

package at.ac.tuwien.infosys.aggr.util;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

@XmlRootElement(name="assertion")
public class DebugAssertion {

	public static enum AssertionEvaluationTime {
		/** [B]efore applying the preparation/finalization query */
		B, 	
		/** [A]fter applying the preparation/finalization query */
		A
	}
	public static enum AssertionEvaluationTarget {
		/** [R]esponse = evaluate data source response directly */
		R, 	
		/** [T]otal = evaluate assertion on the total result. */
		T
	}

	@XmlElement(name="ID")
	private String ID;
	@XmlElement(name="expression")
	private String expression;
	@XmlElement(name="inputID")
	private String inputID;
	@XmlElement(name="assertTarget")
	private AssertionEvaluationTarget assertTarget;
	@XmlElement(name="assertTime")
	private AssertionEvaluationTime assertTime;
	
	@XmlRootElement
	public static class AssertionResult {
		@XmlElement
		private String ID;
		@XmlElement
		private String expression;
		@XmlElement
		private boolean isOK;
		
		/** default constructor required by JAXB. */
		@Deprecated
		public AssertionResult() { }
		public AssertionResult(String ID, String expression, boolean isOK) {
			this.expression = expression;
			this.ID = ID;
			this.isOK = isOK;
		}
		
		@XmlTransient
		public String getExpression() {
			return expression;
		}
		public void setExpression(String expression) {
			this.expression = expression;
		}
		@XmlTransient
		public String getID() {
			return ID;
		}
		public void setID(String iD) {
			ID = iD;
		}
		@XmlTransient
		public boolean isOK() {
			return isOK;
		}
		public void setOK(boolean isOK) {
			this.isOK = isOK;
		}
	}

	@XmlTransient
	public String getID() {
		return ID;
	}
	public void setID(String iD) {
		ID = iD;
	}

	@XmlTransient
	public String getExpression() {
		return expression;
	}
	public void setExpression(String expression) {
		this.expression = expression;
	}

	@XmlTransient
	public String getInputID() {
		return inputID;
	}
	public void setInputID(String inputID) {
		this.inputID = inputID;
	}

	@XmlTransient
	public AssertionEvaluationTarget getAssertTarget() {
		return assertTarget;
	}
	public void setAssertTarget(AssertionEvaluationTarget assertTarget) {
		this.assertTarget = assertTarget;
	}

	@XmlTransient
	public AssertionEvaluationTime getAssertTime() {
		return assertTime;
	}
	public void setAssertTime(AssertionEvaluationTime assertTime) {
		this.assertTime = assertTime;
	}
	
}
