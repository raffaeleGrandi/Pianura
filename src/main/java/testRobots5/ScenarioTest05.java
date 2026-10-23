package testRobots5;

import java.awt.Color;
import java.awt.Dimension;

import io.github.raffaelegrandi.pianura.ArenaObjects.ArenaObjectType;
import io.github.raffaelegrandi.pianura.ArenaObjects.Obstacle;
import io.github.raffaelegrandi.pianura.gui.floor.FloorStyle;
import io.github.raffaelegrandi.pianura.structures.ScenarioManager;
import io.github.raffaelegrandi.pianura.utils.Position;
import testRobots4.TestDummy04;

/**
 * Arena grande (80 x 50 celle) con pavimento in pietra, stanze e corridoi:
 * esempio per zoom, spostamento della vista e pannello laterale.
 */
public class ScenarioTest05 extends ScenarioManager {

	private static final Color WALL = new Color(120, 110, 100);
	private static final Color BOX = new Color(70, 110, 160);
	private static final Color PILLAR = new Color(235, 235, 230);

	public ScenarioTest05(){
		setArenaSize(80, 50);
		setFloor(FloorStyle.voronoiTiles());
		buildObstacles();
		buildRobots();
	}

	private void wall(double x, double y, int w, int h){
		addObstacle(new Obstacle(ArenaObjectType.OBSTACLE_RECT, WALL, new Position(x, y, 0), new Dimension(w, h)));
	}

	private void buildObstacles(){
		// tramezzi che dividono l'arena in tre zone, con varchi
		wall(27, 10, 1, 20);
		wall(27, 43, 1, 14);
		wall(54, 8, 1, 16);
		wall(54, 38, 1, 24);
		wall(64, 25, 20, 1);
		// casse ruotate e pilastri
		addObstacle(new Obstacle(ArenaObjectType.OBSTACLE_RECT, BOX, new Position(12, 12, Math.toRadians(20)), new Dimension(5, 3)));
		addObstacle(new Obstacle(ArenaObjectType.OBSTACLE_RECT, BOX, new Position(40, 30, Math.toRadians(-35)), new Dimension(6, 4)));
		addObstacle(new Obstacle(ArenaObjectType.OBSTACLE_RECT, BOX, new Position(68, 40, Math.toRadians(10)), new Dimension(4, 4)));
		addObstacle(new Obstacle(ArenaObjectType.OBSTACLE_OVAL, PILLAR, new Position(14, 34, 0), new Dimension(4, 4)));
		addObstacle(new Obstacle(ArenaObjectType.OBSTACLE_OVAL, PILLAR, new Position(40, 12, 0), new Dimension(3, 3)));
		addObstacle(new Obstacle(ArenaObjectType.OBSTACLE_OVAL, PILLAR, new Position(70, 12, 0), new Dimension(6, 3)));
	}

	private void buildRobots(){
		double[][] p = {
			{ 6, 6, 0 }, { 6, 22, 45 }, { 8, 44, -30 }, { 20, 26, 90 }, { 20, 6, 180 },
			{ 34, 6, 0 }, { 36, 22, 120 }, { 46, 44, -90 }, { 48, 18, 200 },
			{ 60, 6, 30 }, { 75, 6, 150 }, { 60, 32, 0 }, { 76, 32, 270 }, { 62, 46, 60 } };
		for (int i = 0; i < p.length; i++) {
			addRobot(new TestDummy04(i + 1, new Position(p[i][0], p[i][1], Math.toRadians(p[i][2])), 2));
		}
	}

}
