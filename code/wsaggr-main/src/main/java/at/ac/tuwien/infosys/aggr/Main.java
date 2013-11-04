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

package at.ac.tuwien.infosys.aggr;

import at.ac.tuwien.infosys.aggr.util.ServiceStarter;
import at.ac.tuwien.infosys.util.Util;
import at.ac.tuwien.infosys.util.misc.PerformanceInterceptor;

public class Main {

	static Util util = Util.getInstance();
	
	static void addXMLParsingInterceptor() {
		PerformanceInterceptor.addInterceptor(new PerformanceInterceptor() {
			@Override
			public void handleEvent(EventType type, String correlationID, Object ... userObjects) {
				String s = userObjects.length <= 0 ? "" : userObjects[0].toString();
				s = s.length() > 1000 ? s.substring(0, 1000) : s;
				if(type == EventType.FINISH_PARSE_XML_STRICT) {
					long t1 = eventTimes.get(EventType.START_PARSE_XML_STRICT).get(correlationID);
					long diff = (System.currentTimeMillis() - t1);
					if(diff > 2000) {
						System.out.println("--> XML strict: " + diff + " - " + s);
					}
				} else if(type == EventType.FINISH_PARSE_XML_TIDY) {
					long t1 = eventTimes.get(EventType.START_PARSE_XML_TIDY).get(correlationID);
					long diff = (System.currentTimeMillis() - t1);
					if(diff > 2000) {
						System.out.println("--> XML tidy: " + diff + " - " + s);
					}
				}
				super.handleEvent(type, correlationID);
			}
		});
	}
	
	public static void main(String[] args) throws Exception {
		ServiceStarter.main(args);
		//addXMLParsingInterceptor();
	}
	
}
