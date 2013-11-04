/*
 * Copyright (c) 2010 Michael Starzinger
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package at.ac.tuwien.infosys.aggr.waql;

import java.util.Arrays;
import java.util.LinkedList;

/**
 * The main implementation of a data printer pipeline.
 * @author Michael Starzinger <michael.starzinger@antforge.org>
 */
public class DataPrinterPipeline implements DataPrinter
{
	/** The actual current pipeline of data printers. */
	private final LinkedList<DataPrinter> pipeline =
		new LinkedList<DataPrinter>(Arrays.asList(DEFAULT_PIPELINE));

	@Override
	public boolean canHandle(Object object) {

		// We can handle everything.
		return true;
	}

	@Override
	public String printAsExpression(Object object, DataPrinter _) {

		// Iterate through all printers in the pipeline and delegate to the
		// first one which can handle the given object.
		for (DataPrinter printer : pipeline)
			if (printer.canHandle(object))
				return printer.printAsExpression(object, this);

		// If we fall through, something went severely wrong.
		throw new RuntimeException("Unable to handle object type.");
	}

	@Override
	public String printAsText(Object object, DataPrinter _) {

		// Iterate through all printers in the pipeline and delegate to the
		// first one which can handle the given object.
		for (DataPrinter printer : pipeline)
			if (printer.canHandle(object))
				return printer.printAsText(object, this);

		// If we fall through, something went severely wrong.
		throw new RuntimeException("Unable to handle object type.");
	}

	/**
	 * Adds a new data printer to this pipeline.
	 * @param printer The data printer to be added.
	 */
	public void addDataPrinter(DataPrinter printer) {

		// Add the given printer to the top of the pipeline.
		pipeline.addFirst(printer);
	}

	/** The default set of data printers added to new pipelines. */
	private static final DataPrinter[] DEFAULT_PIPELINE = new DataPrinter[] {
		new ElementPrinter(),
		new CollectionPrinter(),
		new StringPrinter(),
		new DefaultPrinter(),
	};
}
