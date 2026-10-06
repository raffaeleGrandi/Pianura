package jusePack.units.collisions;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.Color;
import java.awt.Dimension;
import java.util.List;

import org.junit.jupiter.api.Test;

import jusePack.ArenaObjects.ArenaObjectType;
import jusePack.ArenaObjects.Obstacle;
import jusePack.units.RobotDummy;
import jusePack.utils.Position;

class RebuildAndRotationTest {

    @Test
    void rotatedOvalRayIntersection() {
        // ellisse 40x10 centrata in (100,0): allineata, il raggio lungo +x entra a 80;
        // ruotata di 90° l'asse lungo diventa verticale e il raggio entra a 95
        assertEquals(80, new OvalShape(100, 0, 40, 10).rayIntersect(0, 0, 1, 0, 500), 1e-9);
        assertEquals(95, new OvalShape(100, 0, 40, 10, Math.PI / 2).rayIntersect(0, 0, 1, 0, 500), 1e-9);
        assertEquals(10, new OvalShape(100, 0, 40, 10, Math.PI / 2).outline().getBounds2D().getWidth(), 1e-6);
    }

    @Test
    void rebuildPicksUpMovedObstacle() {
        Obstacle o = new Obstacle(ArenaObjectType.OBSTACLE_RECT, Color.BLUE, new Position(20, 10, 0), new Dimension(2, 2));
        RobotDummy r = new RobotDummy(1, new Position(10, 10, 0), 5) { @Override public void executeLoop() {} };
        CollisionDetector cd = new CollisionDetector(List.of(o));
        cd.testUnitForCollisions(r.getUnitObject());
        assertEquals(255, r.getUnitObject().getIrSensors().getSensorValue(0), "ostacolo fuori portata");

        o.getID().setPos(new Position(14, 10, 0));    // spostato davanti al robot
        cd.testUnitForCollisions(r.getUnitObject());
        assertEquals(255, r.getUnitObject().getIrSensors().getSensorValue(0), "senza rebuild le forme sono le vecchie");
        cd.rebuild();
        cd.testUnitForCollisions(r.getUnitObject());
        // centro robot x=150px, faccia dell'ostacolo x=195px, raggio robot 15px → 30px
        assertEquals(30, r.getUnitObject().getIrSensors().getSensorValue(0));
    }

    @Test
    void originInsideLargeShapeIsAlwaysContact() {
        // quadrato 200x200: dal centro l'uscita è a 100 px, ben oltre maxDist = 15
        assertEquals(0, new RectShape(0, 0, 200, 200, 0.3).rayIntersect(0, 0, 1, 0, 15), 1e-9);
        assertEquals(0, new OvalShape(0, 0, 200, 120, 0.3).rayIntersect(0, 0, 1, 0, 15), 1e-9);
        assertEquals(0, new CircleShape(0, 0, 100).rayIntersect(0, 0, 1, 0, 15), 1e-9);
    }

    @Test
    void robotInsideLargeObstacleIsFlagged() {
        Obstacle big = new Obstacle(ArenaObjectType.OBSTACLE_RECT, Color.YELLOW, new Position(10, 10, 0), new Dimension(10, 10));
        RobotDummy r = new RobotDummy(3, new Position(11, 12, 0), 2) { @Override public void executeLoop() {} };
        CollisionDetector.Validation v = new CollisionDetector(List.of(big)).validate(List.of(r.getUnitObject()));
        assertTrue(v.badRobots().contains(r.getUnitObject()), v.messages().toString());
    }

    @Test
    void validationReportsWhichElementsAreBad() {
        Obstacle a = new Obstacle(ArenaObjectType.OBSTACLE_RECT, Color.BLUE, new Position(10, 10, 0), new Dimension(4, 4));
        Obstacle b = new Obstacle(ArenaObjectType.OBSTACLE_RECT, Color.BLUE, new Position(12, 10, 0), new Dimension(4, 4));
        Obstacle c = new Obstacle(ArenaObjectType.OBSTACLE_OVAL, Color.WHITE, new Position(30, 30, 0), new Dimension(2, 2));
        RobotDummy r1 = new RobotDummy(1, new Position(30, 31.5, 0), 2) { @Override public void executeLoop() {} };  // tocca l'ovale
        RobotDummy r2 = new RobotDummy(2, new Position(5, 30, 0), 2) { @Override public void executeLoop() {} };
        CollisionDetector.Validation v = new CollisionDetector(List.of(a, b, c))
            .validate(List.of(r1.getUnitObject(), r2.getUnitObject()));
        assertEquals(java.util.Set.of(0, 1), v.badObstacles());
        assertTrue(v.badRobots().contains(r1.getUnitObject()));
        assertFalse(v.badRobots().contains(r2.getUnitObject()));
        assertEquals(2, v.messages().size(), v.messages().toString());
    }

}
