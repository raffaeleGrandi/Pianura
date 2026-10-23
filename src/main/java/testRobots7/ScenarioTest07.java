package testRobots7;

import java.awt.Color;

import io.github.raffaelegrandi.pianura.arena.ArenaObjectType;
import io.github.raffaelegrandi.pianura.arena.Obstacle;
import io.github.raffaelegrandi.pianura.gui.floor.FloorStyle;
import io.github.raffaelegrandi.pianura.remote.RemoteServer;
import io.github.raffaelegrandi.pianura.structures.ScenarioManager;
import io.github.raffaelegrandi.pianura.utils.Position;

/**
 * Two robots driven from outside JUSE over UDP (see clients/python/README.md).
 * The robots stand still until a client says hello.
 */
public class ScenarioTest07 extends ScenarioManager {

	public ScenarioTest07(RemoteServer server){
		setArenaSize(40, 30);
		setFloor(FloorStyle.randomTiles());

		Color box = new Color(70, 110, 160);
		addObstacle(new Obstacle(ArenaObjectType.OBSTACLE_RECT, box, new Position(14, 10, Math.toRadians(25)), 6, 3));
		addObstacle(new Obstacle(ArenaObjectType.OBSTACLE_RECT, box, new Position(28, 20, Math.toRadians(-15)), 5, 5));
		addObstacle(new Obstacle(ArenaObjectType.OBSTACLE_OVAL, Color.WHITE, new Position(26, 8, 0), 4, 4));
		addObstacle(new Obstacle(ArenaObjectType.OBSTACLE_OVAL, Color.WHITE, new Position(10, 22, Math.toRadians(30)), 6, 3));
		addObstacle(new Obstacle(ArenaObjectType.OBSTACLE_RECT, new Color(120, 110, 100), new Position(20, 25, 0), 1, 10));

		// id, pose, IR range (cells), control period (s)
		addRobot(server.createRobot(1, new Position(5, 5, 0), 2, 0.1));
		addRobot(server.createRobot(2, new Position(34, 25, Math.PI), 2, 0.1));
	}

}
