package testRobots3;

import java.awt.Point;
import jusePack.*;

public class MainClassTest03 {
	
	static ScenarioTest03 scenario03;	
	
	public static void main(String[] args) {		
		scenario03 = new ScenarioTest03();
		JuseManager simManager = new JuseManager(new Point(30,30),"logArea",scenario03);
		simManager.startSimPaused();	// parte in pausa: sistemare la scena, poi Run (o Spazio)
	}//end main
}//end class
