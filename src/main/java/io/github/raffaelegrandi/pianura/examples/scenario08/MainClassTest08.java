package io.github.raffaelegrandi.pianura.examples.scenario08;

import java.awt.Point;

import io.github.raffaelegrandi.pianura.Simulator;

public class MainClassTest08 {

	public static void main(String[] args) {
		Simulator sim = new Simulator(new Point(30, 30), "JUSE - radio", new ScenarioTest08());
		sim.getViewOptions().commLinks = true;
		sim.getViewOptions().commRange = false;
		sim.startSimPaused();
		sim.openConnectivityWindow();
	}

}
