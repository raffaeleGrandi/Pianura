package io.github.raffaelegrandi.pianura.units.collisions;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.Color;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import io.github.raffaelegrandi.pianura.arena.ArenaObjectType;
import io.github.raffaelegrandi.pianura.arena.Obstacle;
import io.github.raffaelegrandi.pianura.units.RobotDummy;
import io.github.raffaelegrandi.pianura.units.UnitObject;
import io.github.raffaelegrandi.pianura.utils.Const;
import io.github.raffaelegrandi.pianura.utils.Position;

/**
 * The obstacle grid is only an acceleration: sensors, line of sight and validation must give
 * exactly the same results as testing every shape.
 */
class SpatialIndexTest {

    @AfterEach
    void restore() { CollisionDetector.bruteForceForTests = false; }

    /** Random arena: obstacles of every size and angle (also overlapping and across the border), robots anywhere. */
    private record Scene(CollisionDetector cd, List<UnitObject> units) {}

    private static Scene scene(long seed, int w, int h, int nObs, int nRobots) {
        Random r = new Random(seed);
        List<Obstacle> obs = new ArrayList<>();
        for (int i = 0; i < nObs; i++) {
            ArenaObjectType t = r.nextBoolean() ? ArenaObjectType.OBSTACLE_RECT : ArenaObjectType.OBSTACLE_OVAL;
            double size = r.nextDouble() < 0.05 ? 10 + r.nextDouble() * 20 : 0.5 + r.nextDouble() * 4;   // a few long walls
            obs.add(new Obstacle(t, Color.GRAY, new Position(r.nextDouble() * w, r.nextDouble() * h, r.nextDouble() * 7),
                size, 0.5 + r.nextDouble() * 3));
        }
        List<UnitObject> units = new ArrayList<>();
        for (int i = 0; i < nRobots; i++) {
            Position p = new Position(1 + r.nextDouble() * (w - 2), 1 + r.nextDouble() * (h - 2), r.nextDouble() * 7);
            units.add(new RobotDummy(i + 1, p, 1 + r.nextDouble() * 4) { @Override public void executeLoop() {} }.getUnitObject());
        }
        return new Scene(new CollisionDetector(obs, w * Const.cellSize, h * Const.cellSize), units);
    }

    /** IR and bump values and hit points of every robot, as one string. */
    private static String sense(Scene s) {
        List<CircleShape> bodies = s.cd().snapshotRobots(s.units());
        StringBuilder sb = new StringBuilder();
        for (UnitObject u : s.units()) {
            s.cd().testUnitForCollisions(u, bodies);
            SensorArray ir = u.getIrSensors(), bump = u.getBumpSensors();
            for (int i = 0; i < ir.length(); i++) {
                Point2D p = ir.getCheckPoint(i);
                sb.append(ir.getSensorValue(i)).append('/').append(bump.getSensorValue(i))
                  .append(p == null ? "-" : String.format(java.util.Locale.ROOT, "@%.9f,%.9f", p.getX(), p.getY())).append(' ');
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    private static String lineOfSight(Scene s, long seed, int w, int h) {
        Random r = new Random(seed);
        StringBuilder sb = new StringBuilder();
        for (int k = 0; k < 2000; k++) {
            double ax = r.nextDouble() * w * Const.cellSize, ay = r.nextDouble() * h * Const.cellSize;
            double len = r.nextDouble() * 20 * Const.cellSize, a = r.nextDouble() * 2 * Math.PI;
            sb.append(s.cd().lineOfSight(ax, ay, ax + len * Math.cos(a), ay + len * Math.sin(a)) ? '1' : '0');
        }
        return sb.toString();
    }

    @Test
    void sensorsAreIdenticalWithAndWithoutTheGrid() {
        for (long seed = 1; seed <= 5; seed++) {
            Scene s = scene(seed, 80, 60, 400, 60);
            CollisionDetector.bruteForceForTests = true;
            String brute = sense(s);
            CollisionDetector.bruteForceForTests = false;
            String grid = sense(s);
            assertEquals(brute, grid, "seed " + seed);
            assertTrue(brute.chars().filter(c -> c == '@').count() > 100, "the scene must actually produce hits");
        }
    }

    @Test
    void lineOfSightIsIdenticalWithAndWithoutTheGrid() {
        Scene s = scene(11, 80, 60, 300, 0);
        CollisionDetector.bruteForceForTests = true;
        String brute = lineOfSight(s, 3, 80, 60);
        CollisionDetector.bruteForceForTests = false;
        String grid = lineOfSight(s, 3, 80, 60);
        assertEquals(brute, grid);
        assertTrue(brute.contains("0") && brute.contains("1"));
    }

    @Test
    void validationIsIdenticalWithAndWithoutTheGrid() {
        Scene s = scene(21, 60, 40, 250, 40);
        CollisionDetector.bruteForceForTests = true;
        CollisionDetector.Validation brute = s.cd().validate(s.units());
        CollisionDetector.bruteForceForTests = false;
        CollisionDetector.Validation grid = s.cd().validate(s.units());
        assertEquals(brute.messages(), grid.messages());
        assertEquals(brute.badObstacles(), grid.badObstacles());
        assertFalse(brute.messages().isEmpty());
    }

    @Test
    void gridQueryReturnsEachCandidateOnceInOrder() {
        List<CollidableShape> shapes = List.of(
            new RectShape(150, 10, 300, 8, 0),         // centred: x 0..300, spans many buckets
            new OvalShape(100, 100, 20, 20),
            new RectShape(1000, 1000, 10, 10, 0));
        ShapeGrid g = new ShapeGrid(shapes, 60);
        assertArrayEquals(new int[] { 0, 1 }, g.query(0, 0, 200, 200));
        assertArrayEquals(new int[] { 0 }, g.query(250, 0, 260, 20));
        assertArrayEquals(new int[] { 2 }, g.query(990, 990, 2000, 2000));
        assertArrayEquals(new int[0], g.query(500, 500, 600, 600));
        assertArrayEquals(new int[0], g.query(-500, -500, -400, -400));
    }

}
