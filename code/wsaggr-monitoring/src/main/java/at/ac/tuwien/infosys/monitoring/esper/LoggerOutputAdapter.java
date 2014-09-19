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

package at.ac.tuwien.infosys.monitoring.esper;

import io.hummer.util.Util;

import org.apache.log4j.Logger;
import org.apache.log4j.Priority;

import at.ac.tuwien.infosys.monitoring.config.LoggerOutput;

import com.espertech.esper.client.EventBean;

public class LoggerOutputAdapter extends AbstractOutputAdapter<LoggerOutput> {

	private static final Logger LOGGER = Util
			.getLogger(LoggerOutputAdapter.class);

	private static Util UTIL = new Util();

	private Priority priority;

	@Override
	public void configureInt(LoggerOutput output) {
		priority = Priority.toPriority(output.priority, Priority.INFO);
	}

	@Override
	public void processEvent(EventBean event) {
		try{
			Object toSenEvent = event.getUnderlying();
			String message = UTIL.xml.toString(toSenEvent);		
			LOGGER.log(priority, message);
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}


}
