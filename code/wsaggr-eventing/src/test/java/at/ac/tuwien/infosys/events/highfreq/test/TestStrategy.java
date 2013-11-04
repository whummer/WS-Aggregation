package at.ac.tuwien.infosys.events.highfreq.test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;

import at.ac.tuwien.infosys.bursthandling.strategy.EventBurstHandling;
import at.ac.tuwien.infosys.bursthandling.strategy.EventBurstHandling.EventBurstHandlingException;

public class TestStrategy {
	private static final String JAVA_BIN = System.getProperty("java.home")
			+ "/bin" + "/java";
	private static final String CLASSPATH = System
			.getProperty("java.class.path");
	private final EventBurstHandling strategy;
	private Process process;
	private StrategyController controller;

	/**
	 * 
	 * @param strategy
	 * @param duration
	 */
	public TestStrategy(EventBurstHandling strategy) {
		this.strategy = strategy;
	}

	/**
	 * @throws MalformedURLException
	 * @throws EventBurstHandlingException
	 * @throws InterruptedException
	 */
	public void performTest(boolean simple) throws MalformedURLException,
			EventBurstHandlingException, InterruptedException {

		String className = TestSetupSimple.class.getCanonicalName();
		if (!simple) {
			className = TestSetup.class.getCanonicalName();
		}

		ProcessBuilder builder = new ProcessBuilder(JAVA_BIN, "-cp", CLASSPATH,
				className);

		String eventStreamID = null;

		try {
			process = builder.start();
			BufferedReader output = new BufferedReader(new InputStreamReader(
					process.getInputStream()));
			String line = null;
			Thread logger = null;
			while ((line = output.readLine()) != null) {
				System.out.println(line);
				if (line.startsWith("EventStreamID: ")) {
					eventStreamID = line.substring("EventStreamID: ".length());
					controller = new StrategyController(eventStreamID,
							this.strategy);
					controller.startEscalation();
					logger = (new Thread(new OutputLogger(output)));
					logger.start();
					break;
				}
			}
			BufferedReader in = new BufferedReader(new InputStreamReader(
					System.in));
			
			in.readLine();
			System.out.println("Stopping test.");
			controller.stopEscalation();
			process.getInputStream().close();
			process.destroy();
			
		} catch (IOException e) {
			System.out.println("TestStrategy - performTest - IOException: "
					+ e.getMessage());
			e.printStackTrace();
		}
	}

}

class OutputLogger implements Runnable {
	private BufferedReader output;

	public OutputLogger(BufferedReader output) {
		this.output = output;
	}

	@Override
	public void run() {
		String line = null;
		try {
			while ((line = this.output.readLine()) != null) {
				System.out.println(line);
			}
		} catch (IOException e) {
			System.out.println("Stopped reading output.");
		}
		System.out.println("finished OutputLogger");
	}
}
