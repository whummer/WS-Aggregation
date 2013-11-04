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

package at.ac.tuwien.infosys.aggr.performance;

import java.util.LinkedList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import at.ac.tuwien.infosys.aggr.node.AggregatorNode;
import at.ac.tuwien.infosys.aggr.request.AbstractInput.InputWrapper;
import at.ac.tuwien.infosys.util.Configuration;

@XmlRootElement(name="AggregatorPerformanceInfo", namespace=Configuration.NAMESPACE)
public class AggregatorPerformanceInfo {
	@XmlElement
	private double usedMemory;
	@XmlElement
	private int requestQueueLength;
	@XmlElement
	private double windowSizeCPU;
	@XmlElement
	private double maxUsedCPUOverWindow;
	@XmlElement
	private double currentUsedCPU;
	@XmlElement
	private int openFiles;
	@XmlElement
	private List<StreamDataRate> streamDataRate = new LinkedList<StreamDataRate>();
	@XmlElement
	private List<InputDataRate> inputDataRate = new LinkedList<InputDataRate>();
	@XmlElement
	private List<InterAggregatorsDataRate> interAggregatorDataRate = new LinkedList<InterAggregatorsDataRate>();
	@XmlElement
	private List<UserDataRate> userDataRate = new LinkedList<UserDataRate>();
	@XmlTransient
	public AggregatorNode aggregator;

	@XmlRootElement
	public static class StreamDataRate {
		@XmlElement
		private InputWrapper stream;
		/** data rate in KBytes per second */
		@XmlElement
		private Double dataRate;
		/** optional */
		@XmlElement
		private Double bufferSize; 
		@XmlElement
		private Double eventFreq;
		@XmlTransient
		public InputWrapper getStream() {
			return stream;
		}
		public void setStream(InputWrapper stream) {
			this.stream = stream;
		}
		@XmlTransient
		public Double getDataRate() {
			return dataRate;
		}
		public void setDataRate(Double dataRate) {
			this.dataRate = dataRate;
		}
		@XmlTransient
		public Double getBufferSize() {
			return bufferSize;
		}
		public void setBufferSize(Double bufferSize) {
			this.bufferSize = bufferSize;
		}
		@XmlTransient
		public Double getEventFreq() {
			return eventFreq;
		}
		public void setEventFreq(Double eventFreq) {
			this.eventFreq = eventFreq;
		}
	}
	@XmlRootElement
	public static class UserDataRate {
		@XmlElement
		private String username;
		/** data rate in KBytes per second */
		@XmlElement
		private Double dataRate;
		/** event rate in events per minute */
		@XmlElement
		private Double eventFreq;
		@XmlTransient
		public Double getDataRate() {
			return dataRate;
		}
		public void setDataRate(Double dataRate) {
			this.dataRate = dataRate;
		}
		@XmlTransient
		public String getUsername() {
			return username;
		}
		public void setUsername(String username) {
			this.username = username;
		}
		@XmlTransient
		public Double getEventFreq() {
			return eventFreq;
		}
		public void setEventFreq(Double eventFreq) {
			this.eventFreq = eventFreq;
		}
	}
	@XmlRootElement
	public static class InputDataRate {
		@XmlElement
		private String requestID;
		@XmlElement
		private String inputID;
		@XmlElement
		private Double dataRate;
		@XmlTransient
		public Double getDataRate() {
			return dataRate;
		}
		public void setDataRate(Double dataRate) {
			this.dataRate = dataRate;
		}
		@XmlTransient
		public String getInputID() {
			return inputID;
		}
		public void setInputID(String inputID) {
			this.inputID = inputID;
		}
		@XmlTransient
		public String getRequestID() {
			return requestID;
		}
		public void setRequestID(String requestID) {
			this.requestID = requestID;
		}
	}
	@XmlRootElement
	public static class InterAggregatorsDataRate {
		@XmlElement
		private String aggregatorURL1;
		@XmlElement
		private String aggregatorURL2;
		@XmlElement
		private Double dataRate;
		@XmlTransient
		public String getAggregatorURL1() {
			return aggregatorURL1;
		}
		public void setAggregatorURL1(String aggregatorURL1) {
			this.aggregatorURL1 = aggregatorURL1;
		}
		@XmlTransient
		public String getAggregatorURL2() {
			return aggregatorURL2;
		}
		public void setAggregatorURL2(String aggregatorURL2) {
			this.aggregatorURL2 = aggregatorURL2;
		}
		@XmlTransient
		public Double getDataRate() {
			return dataRate;
		}
		public void setDataRate(Double dataRate) {
			this.dataRate = dataRate;
		}
	}
	@XmlTransient
	public double getUsedMemory() {
		return usedMemory;
	}
	public void setUsedMemory(double usedMemory) {
		this.usedMemory = usedMemory;
	}
	@XmlTransient
	public int getRequestQueueLength() {
		return requestQueueLength;
	}
	public void setRequestQueueLength(int requestQueueLength) {
		this.requestQueueLength = requestQueueLength;
	}
	@XmlTransient
	public double getCurrentUsedCPU() {
		return currentUsedCPU;
	}
	public void setCurrentUsedCPU(double currentUsedCPU) {
		this.currentUsedCPU = currentUsedCPU;
	}
	@XmlTransient
	public double getMaxUsedCPUOverWindow() {
		return maxUsedCPUOverWindow;
	}
	public void setMaxUsedCPUOverWindow(double maxUsedCPUOverWindow) {
		this.maxUsedCPUOverWindow = maxUsedCPUOverWindow;
	}
	@XmlTransient
	public double getWindowSizeCPU() {
		return windowSizeCPU;
	}
	public void setWindowSizeCPU(double windowSizeCPU) {
		this.windowSizeCPU = windowSizeCPU;
	}
	@XmlTransient
	public int getOpenFiles() {
		return openFiles;
	}
	public void setOpenFiles(int openFiles) {
		this.openFiles = openFiles;
	}
	@XmlTransient
	public List<StreamDataRate> getStreamDataRate() {
		return streamDataRate;
	}
	public void setStreamDataRate(List<StreamDataRate> streamDataRate) {
		this.streamDataRate = streamDataRate;
	}
	@XmlTransient
	public List<InputDataRate> getInputDataRate() {
		return inputDataRate;
	}
	public void setInputDataRate(List<InputDataRate> inputDataRate) {
		this.inputDataRate = inputDataRate;
	}
	@XmlTransient
	public List<InterAggregatorsDataRate> getInterAggregatorDataRate() {
		return interAggregatorDataRate;
	}
	public void setInterAggregatorDataRate(List<InterAggregatorsDataRate> interAggregatorDataRate) {
		this.interAggregatorDataRate = interAggregatorDataRate;
	}
	@XmlTransient
	public List<UserDataRate> getUserDataRate() {
		return userDataRate;
	}
	public void setUserDataRate(List<UserDataRate> userDataRate) {
		this.userDataRate = userDataRate;
	}
	
	@Override
	public String toString() {
		StringBuilder b = new StringBuilder();
		b.append("[PI ");
		b.append("mem=" + usedMemory);
		b.append(", cpu_current=" + currentUsedCPU);
		b.append(", cpu_max (" + windowSizeCPU/1000 + "sec)=" + maxUsedCPUOverWindow);
		b.append(", files=" + openFiles);
		b.append(", queue=" + requestQueueLength);
		b.append(", (some values omitted)]");
		return b.toString();
	}
}
