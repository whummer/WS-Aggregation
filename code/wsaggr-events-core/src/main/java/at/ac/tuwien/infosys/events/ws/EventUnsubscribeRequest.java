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

@XmlRootElement(name = "Unsubscribe", namespace = WSEvent.NAMESPACE)
public class EventUnsubscribeRequest {

	
//	(01) <s12:Envelope 
//	(02)     xmlns:s12="http://www.w3.org/2003/05/soap-envelope"
//	(03)     xmlns:wsa="http://schemas.xmlsoap.org/ws/2004/08/addressing"
//	(04)     xmlns:wse="http://schemas.xmlsoap.org/ws/2004/08/eventing"
//	(05)     xmlns:ow="http://www.example.org/oceanwatch" >
//	(06)   <s12:Header>
//	(07)     <wsa:Action>
//	(08)       http://schemas.xmlsoap.org/ws/2004/08/eventing/Unsubscribe
//	(09)     </wsa:Action>
//	(10)     <wsa:MessageID>
//	(11)       uuid:2653f89f-25bc-4c2a-a7c4-620504f6b216
//	(12)     </wsa:MessageID>
//	(13)     <wsa:ReplyTo>
//	(14)      <wsa:Address>http://www.example.com/MyEventSink</wsa:Address>
//	(15)     </wsa:ReplyTo>
//	(16)     <wsa:To>
//	(17)       http://www.example.org/oceanwatch/SubscriptionManager
//	(18)     </wsa:To>
//	(19)     <wse:Identifier>
//	(20)       uuid:22e8a584-0d18-4228-b2a8-3716fa2097fa
//	(21)     </wse:Identifier>
//	(22)   </s12:Header>
//	(23)   <s12:Body>
//	(24)     <wse:Unsubscribe />
//	(25)   </s12:Body>
//	(26) </s12:Envelope>
	
}
