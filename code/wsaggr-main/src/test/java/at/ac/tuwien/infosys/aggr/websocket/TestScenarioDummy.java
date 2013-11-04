package at.ac.tuwien.infosys.aggr.websocket;


public class TestScenarioDummy extends TestScenario {

	public static void main(String[] args) throws Exception {
		
		setup();
		checkForSockets();
		
		System.out.println("Now just sleeping..");
		while(true) {
			Thread.sleep(100 * 1000);
		}
		
	}

}
