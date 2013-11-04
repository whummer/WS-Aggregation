package at.ac.tuwien.infosys.events.highfreq.test;

import java.net.MalformedURLException;

import at.ac.tuwien.infosys.bursthandling.strategy.EventBurstHandling.EventBurstHandlingException;
import at.ac.tuwien.infosys.bursthandling.strategy.loadshedding.IntervalLoadShedder;

public class TestIntervalLoadShedder {

	/**
	 * @param args
	 * @throws MalformedURLException
	 * @throws EventBurstHandlingException
	 * @throws InterruptedException 
	 */
	public static void main(String[] args) throws MalformedURLException,
			EventBurstHandlingException, InterruptedException {

		new TestStrategy(new IntervalLoadShedder(7500, 1)).performTest(true);
	}

}
