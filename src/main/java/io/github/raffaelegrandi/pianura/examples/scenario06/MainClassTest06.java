package io.github.raffaelegrandi.pianura.examples.scenario06;

import java.awt.Point;

import io.github.raffaelegrandi.pianura.Simulator;

public class MainClassTest06 {

	public static void main(String[] args) {
		Simulator sim = new Simulator(new Point(30, 30), "Pianura - line following", new ScenarioTest06());
		sim.startSimPaused();
	}

}
