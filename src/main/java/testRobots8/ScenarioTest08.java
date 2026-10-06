package testRobots8;

import java.awt.Color;

import jusePack.ArenaObjects.ArenaObjectType;
import jusePack.ArenaObjects.Obstacle;
import jusePack.gui.floor.FloorStyle;
import jusePack.structures.ScenarioManager;
import jusePack.units.ControllerRobot;
import jusePack.utils.Position;

/** Fourteen robots exchanging beacons over a radio with a 9-cell range. */
public class ScenarioTest08 extends ScenarioManager {

	public ScenarioTest08(){
		setArenaSize(60, 40);
		setFloor(FloorStyle.grid(new Color(250, 250, 246), new Color(215, 220, 228), 1));
		getCommSettings().setRange(9);

		Color wall = new Color(120, 110, 100);
		addObstacle(new Obstacle(ArenaObjectType.OBSTACLE_RECT, wall, new Position(30, 12, 0), 1, 24));
		addObstacle(new Obstacle(ArenaObjectType.OBSTACLE_RECT, new Color(70, 110, 160), new Position(14, 28, Math.toRadians(20)), 6, 4));
		addObstacle(new Obstacle(ArenaObjectType.OBSTACLE_OVAL, new Color(235, 235, 230), new Position(46, 26, 0), 6, 6));

		double[][] p = { {6,6}, {10,14}, {6,22}, {12,34}, {20,8}, {22,20}, {24,32},
		                 {36,6}, {40,16}, {38,34}, {48,10}, {54,18}, {52,32}, {44,38} };
		for (int i = 0; i < p.length; i++) {
			addRobot(new ControllerRobot(i + 1, new Position(p[i][0], p[i][1], i * 0.7), 2, 0.1, new BeaconWanderer(i + 1)));
		}
	}

}
