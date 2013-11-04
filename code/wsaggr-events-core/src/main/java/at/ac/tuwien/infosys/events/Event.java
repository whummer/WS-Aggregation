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

package at.ac.tuwien.infosys.events;

import java.util.concurrent.atomic.AtomicLong;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlTransient;

import at.ac.tuwien.infosys.util.Identifiable;

public class Event implements Identifiable {

	public static final AtomicLong ID_COUNTER = new AtomicLong(1);
	
	@XmlElement(name="eventID")
	private long id = ID_COUNTER.incrementAndGet();
	
	@XmlAttribute(name="timeStamp")
	private long timeStamp = System.currentTimeMillis();

	@XmlAttribute(name="type")
	private String type = getEventType(getClass());
	
	
	@XmlTransient
	public long getIdentifier() {
		return id;
	}
	public void setIdentifier(long id) {
		this.id = id;
	}
	
	@XmlTransient
	public long getTimeStamp() {
		return timeStamp;
	}
	public void setTimeStamp(long timeStamp) {
		this.timeStamp = timeStamp;
	}

	@XmlTransient
	public String getType() {
		return type;
	}
	public void setType(String type) {
		this.type = type;
	}

	
	public static <T extends Event> String getEventType(Class<T> event){
		return event.getSimpleName().replace("Event", "");
	}

}
