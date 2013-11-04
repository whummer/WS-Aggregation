package at.ac.tuwien.infosys.events.highfreq.test;

import java.net.MalformedURLException;

import at.ac.tuwien.infosys.bursthandling.strategy.EventBurstHandling.EventBurstHandlingException;
import at.ac.tuwien.infosys.bursthandling.strategy.loadshedding.FixedLoadShedder;

public class TestFixedLoadShedder {

	/**
	 * @param args
	 * @throws MalformedURLException
	 * @throws EventBurstHandlingException
	 * @throws InterruptedException 
	 */
	public static void main(String[] args) throws MalformedURLException,
			EventBurstHandlingException, InterruptedException {
		new TestStrategy(new FixedLoadShedder(5)).performTest(true);
		System.out.println("finished TestFixedLoadShedder");
	}

}
