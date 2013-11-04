package ch.ethz.mxquery;

import java.util.concurrent.atomic.AtomicInteger;

/** added by hummer@infosys.tuwien.ac.at */
public class MXQueryGlobals {

	public static final int granularity = 1001;
	public static final AtomicInteger garbCollThreshold = new AtomicInteger(200);
	
}
