package testRobots3;

import java.awt.Point;
import io.github.raffaelegrandi.pianura.*;

public class MainClassTest03 {
	
	static ScenarioTest03 scenario03;	
	
	public static void main(String[] args) {		
		scenario03 = new ScenarioTest03();
		Simulator simManager = new Simulator(new Point(30,30),"logArea",scenario03);
		simManager.startSimPaused();	// parte in pausa: sistemare la scena, poi Run (o Spazio)
	}//end main
}//end class
