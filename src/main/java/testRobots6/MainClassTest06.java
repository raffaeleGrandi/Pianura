package testRobots6;

import java.awt.Point;

import io.github.raffaelegrandi.pianura.JuseManager;

public class MainClassTest06 {

	public static void main(String[] args) {
		JuseManager sim = new JuseManager(new Point(30, 30), "JUSE - line following", new ScenarioTest06());
		sim.startSimPaused();
	}

}
