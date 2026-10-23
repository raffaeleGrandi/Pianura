package testRobots5;

import java.awt.Point;

import io.github.raffaelegrandi.pianura.JuseManager;

public class MainClassTest05 {

	public static void main(String[] args) {
		JuseManager sim = new JuseManager(new Point(30, 30), "JUSE - arena 80x50", new ScenarioTest05());
		sim.startSimPaused();	// parte in pausa: sistemare la scena, poi Run (o Spazio)
	}

}
