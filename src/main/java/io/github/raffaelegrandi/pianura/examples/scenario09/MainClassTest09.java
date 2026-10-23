package io.github.raffaelegrandi.pianura.examples.scenario09;

import java.awt.Point;

import io.github.raffaelegrandi.pianura.Simulator;

/**
 * Large map (300 x 200 cells, 60 robots): fly over it with W A S D, the mouse at the border of
 * the view or the minimap; double click a robot to follow it.
 */
public class MainClassTest09 {

	public static void main(String[] args) {
		Simulator sim = new Simulator(new Point(30, 30), "JUSE - large map", new ScenarioTest09());
		sim.startSimPaused();
	}

}
