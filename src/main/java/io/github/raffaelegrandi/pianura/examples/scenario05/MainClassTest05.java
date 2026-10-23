package io.github.raffaelegrandi.pianura.examples.scenario05;

import java.awt.Point;

import io.github.raffaelegrandi.pianura.Simulator;

public class MainClassTest05 {

	public static void main(String[] args) {
		Simulator sim = new Simulator(new Point(30, 30), "JUSE - arena 80x50", new ScenarioTest05());
		sim.startSimPaused();	// parte in pausa: sistemare la scena, poi Run (o Spazio)
	}

}
