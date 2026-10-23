package testRobots4;

import java.awt.Point;
import io.github.raffaelegrandi.pianura.*;

public class MainClassTest04 {

	static ScenarioTest04 scenario04;

	public static void main(String[] args) {
		scenario04 = new ScenarioTest04();
		JuseManager simManager = new JuseManager(new Point(30,30), "logArea", scenario04);
		simManager.startSimPaused();	// parte in pausa: sistemare la scena, poi Run (o Spazio)
	}//end main

}//end class
