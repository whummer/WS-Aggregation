package at.ac.tuwien.infosys.events.highfreq.test;

import java.net.MalformedURLException;

import at.ac.tuwien.infosys.bursthandling.strategy.EventBurstHandling.EventBurstHandlingException;
import at.ac.tuwien.infosys.bursthandling.strategy.loadshedding.ProbabilisticLoadShedder;

public class TestProbabilisticLoadShedder {

	/**
	 * @param args
	 * @throws MalformedURLException
	 * @throws EventBurstHandlingException
	 * @throws InterruptedException
	 */
	public static void main(String[] args) throws MalformedURLException,
			EventBurstHandlingException, InterruptedException {

		new TestStrategy(new ProbabilisticLoadShedder(0.5)).performTest(true);

	}

}