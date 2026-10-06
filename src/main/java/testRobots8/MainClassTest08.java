package testRobots8;

import java.awt.Point;

import jusePack.JuseManager;

public class MainClassTest08 {

	public static void main(String[] args) {
		JuseManager sim = new JuseManager(new Point(30, 30), "JUSE - radio", new ScenarioTest08());
		sim.getViewOptions().commLinks = true;
		sim.getViewOptions().commRange = false;
		sim.startSimPaused();
		sim.openConnectivityWindow();
	}

}
