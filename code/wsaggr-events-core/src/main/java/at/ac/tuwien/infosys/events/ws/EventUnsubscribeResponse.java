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

package at.ac.tuwien.infosys.events.ws;

import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "UnsubscribeResponse", namespace = WSEvent.NAMESPACE)
public class EventUnsubscribeResponse {

//	(01) <s12:Envelope 
//	(02)     xmlns:s12="http://www.w3.org/2003/05/soap-envelope"
//	(03)     xmlns:wsa="http://schemas.xmlsoap.org/ws/2004/08/addressing" >
//	(04)   <s12:Header>
//	(05)     <wsa:Action>
//	(06) http://schemas.xmlsoap.org/ws/2004/08/eventing/UnsubscribeResponse
//	(07)     </wsa:Action>
//	(08)     <wsa:RelatesTo>
//	(09)       uuid:2653f89f-25bc-4c2a-a7c4-620504f6b216
//	(10)     </wsa:RelatesTo>
//	(11)     <wsa:To>http://www.example.com/MyEventSink</wsa:To>
//	(12)   </s12:Header>
//	(13)   <s12:Body />
//	(14) </s12:Envelope>
}
