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

package at.ac.tuwien.infosys.events.schema.test;

import org.w3c.dom.Element;

import at.ac.tuwien.infosys.aggr.events.query.EventStream;
import at.ac.tuwien.infosys.events.schema.XmlSchemaInference.SchemaSet;
import at.ac.tuwien.infosys.events.schema.infer.EventSchemaInference;
import at.ac.tuwien.infosys.events.schema.infer.EventSchemaInferenceTwining;
import at.ac.tuwien.infosys.events.schema.infer.EventSchemaValidator;
import at.ac.tuwien.infosys.events.schema.infer.EventSchemaInference.SchemaInferenceConfig;
import at.ac.tuwien.infosys.aggr.xml.XPathProcessor;
import io.hummer.util.Util;

@org.junit.Ignore
public class SchemaInferenceTest {

	private EventStream stream;
	private EventSchemaInference infer;

	public void test() throws Exception {
		Util util = new Util();

//		String file = "schemaInferenceTest.xml";
//		String typePath = "local-name(.)";
//		String[][] corrs = new String[][]{ {"//login/@s", "//logout/@s", "//activate/@s", "//access/@s"} };

//		String file = "eventtrace2_big.xml";
//		String typePath = "@t";
//		String[][] corrs = new String[][]{ {"@p"} };

		String file = "eventtrace3.xml";
		String typePath = "local-name(.)";
		String[][] corrs = new String[][]{ 
					{"//DeliveryID/text()"}, 
					{"//PacketID/text()", "//NewProduct/@PacketID"} };

		Element tests = util.xml.toElement(util.io.readFile(
				SchemaInferenceTest.class.getResourceAsStream(file)));

		for(Object t : XPathProcessor.evaluateAsList("//events", tests)) {

			stream = new EventStream(null, null);
			SchemaInferenceConfig config = new SchemaInferenceConfig();
			config.namePatternFirstElementInStream = "login";
			config.namePatternLastElementInStream = "logout";
			config.allowOverlappingWindows = true;
			config.eventTypeXPath = typePath;
			for(String[] corr: corrs) {
				config.addCorrelation(corr);
			}
			infer = new EventSchemaInferenceTwining(stream, config);

			int count = 0;
			for(Element e : util.xml.getChildElements((Element)t)) {
				stream.notifyDirectListeners(e);
				System.out.println("added event #" + count++);
				Thread.sleep(20);
				if(count % 50 == 0) {
					generateAndValidateSchema();
				}
			}
		}
		
		generateAndValidateSchema();

	}
	
	private void generateAndValidateSchema() {
		long before = System.currentTimeMillis();
		SchemaSet schema = infer.getCurrentlyBestEventSchema();
		System.out.println("best schema: " + schema);
		long after = System.currentTimeMillis();
		long diff = after - before;
		System.out.println("Inference took " + (diff) + "ms");
		
		before = System.currentTimeMillis();
		boolean valid = EventSchemaValidator.validate(schema, infer.getTwineStarts());
		after = System.currentTimeMillis();
		diff = after - before;
		System.out.println("Schema validation: " + valid + " (took " + (diff) + "ms)");
		if(!valid) {
			throw new RuntimeException("Unable to validate generated schema!");
		}
	}

	public static void main(String[] args) throws Exception {
		SchemaInferenceTest test = new SchemaInferenceTest();
		try {
			test.test();
		} catch (Exception e) {
			e.printStackTrace();
		}
		System.exit(0);
	}

}
