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

package at.ac.tuwien.infosys.aggr.tx;

import io.hummer.util.ws.EndpointReference;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name="CoordinationContext", namespace=CoordinationContext.NS_WS_COORDINATION)
public class CoordinationContext {
	public static final String NS_WS_COORDINATION = "http://docs.oasis-open.org/ws-tx/wscoor/2006/06";
	public static final String COORD_TYPE_WS_TX = "http://docs.oasis-open.org/ws-tx/wsat/2006/06";

	@XmlElement(name="Identifier", namespace=CoordinationContext.NS_WS_COORDINATION)
	public String Identifier;
	@XmlElement(name="Expires", namespace=CoordinationContext.NS_WS_COORDINATION)
	public String Expires;
	@XmlElement(name="RegistrationService", namespace=CoordinationContext.NS_WS_COORDINATION)
	public EndpointReference RegistrationService;
	@XmlElement(name="CoordinationType", namespace=CoordinationContext.NS_WS_COORDINATION)
	public String CoordinationType;

	
}
