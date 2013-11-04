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
package at.ac.tuwien.infosys.aggr.waql;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

import org.apache.log4j.Logger;

import at.ac.tuwien.infosys.util.Util;

public class QueryPreprocessor {
	
	private static final Logger logger = Util.getLogger(QueryPreprocessor.class);

	private QueryPreprocessor() {}
	
	public static String preprocess(String in) throws Exception {
		PreprocessorEngine engine = PreprocessorFactory.getEngine();

		in = replaceAmpersands(in).trim();

		InputStream input = new ByteArrayInputStream(in.getBytes());
		OutputStream output = new ByteArrayOutputStream();
		boolean apostrophesAdded = false;
		try {
			try {
				engine.parse(input);				
			} catch (Exception e) {
				engine = PreprocessorFactory.getEngine();
				engine.parse(new ByteArrayInputStream(("'" + in + "'").getBytes()));
				apostrophesAdded = true;
			}
			engine.transform(output);
		} catch (Exception e) {
			e.printStackTrace();
			if(in != null && !in.startsWith("http://"))
				logger.info("Error occurred when preprocessing expression " + in);
			return null;
		}
		String out = output.toString();
		if(apostrophesAdded) {
			if(out.startsWith("'")) out = out.substring(1);
			if(out.endsWith("'")) out = out.substring(0, out.length() - 1);
		}
		return out;
	}
	
	public static String replaceAmpersands(String in) {
		String in1 = in.replaceAll("&([^a])", "&amp;$1");
		in1 = in1.replaceAll("&(a[^m])", "&amp;$1");
		in1 = in1.replaceAll("&(am[^p])", "&amp;$1");
		in1 = in1.replaceAll("&(amp[^;])", "&amp;$1");
		in1 = in1.replaceAll("&$", "&amp;");
		return in1;
	}

}
