package at.ac.tuwien.infosys.events.highfreq.test;

import java.net.MalformedURLException;

import at.ac.tuwien.infosys.bursthandling.strategy.EventBurstHandling.EventBurstHandlingException;
import at.ac.tuwien.infosys.bursthandling.strategy.forwarding.ForwardStrategy;

public class TestForwardStrategy {

	/**
	 * @param args
	 * @throws MalformedURLException
	 * @throws EventBurstHandlingException
	 * @throws InterruptedException 
	 */
	public static void main(String[] args) throws MalformedURLException,
			EventBurstHandlingException, InterruptedException {
	
		new TestStrategy(new ForwardStrategy(2, false)).performTest(false);
	}

}
