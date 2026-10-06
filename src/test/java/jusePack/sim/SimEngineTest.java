package jusePack.sim;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.Color;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import jusePack.ArenaObjects.ArenaObjectType;
import jusePack.ArenaObjects.Obstacle;
import jusePack.units.ControllerRobot;
import jusePack.units.RobotDummy;
import jusePack.units.collisions.CollisionDetector;
import jusePack.utils.Const;
import jusePack.utils.Position;
import testRobots4.TestDummy04;

/** Test del motore senza GUI. */
class SimEngineTest {

    /** Crea clock+motore, collega e avvia i robot (motore non ancora avviato). */
    private static SimEngine setup(List<RobotDummy> robots, List<Obstacle> obstacles) {
        SimClock clock = new SimClock(Const.physicsStep);
        SimEngine engine = new SimEngine(clock, new CollisionDetector(obstacles), robots);
        engine.setLogger(m -> {});
        for (RobotDummy r : robots) r.attachClock(clock);
        return engine;
    }

    private static void startRobots(List<RobotDummy> robots) {
        for (RobotDummy r : robots) r.start();
    }

    private static List<Obstacle> someObstacles() {
        List<Obstacle> obs = new ArrayList<>();
        obs.add(new Obstacle(ArenaObjectType.OBSTACLE_RECT, Color.BLUE, new Position(30, 25, 10), new Dimension(7, 7)));
        obs.add(new Obstacle(ArenaObjectType.OBSTACLE_OVAL, Color.WHITE, new Position(22, 17, 0), new Dimension(4, 4)));
        obs.add(new Obstacle(ArenaObjectType.OBSTACLE_RECT, Color.RED, new Position(30, 37, 30), new Dimension(2, 15)));
        return obs;
    }

    private static List<double[]> runSwarm(double seconds) {
        List<RobotDummy> robots = new ArrayList<>();
        robots.add(new TestDummy04(1, new Position(7, 17, Math.toRadians(90)), 2));
        robots.add(new TestDummy04(2, new Position(10, 20, 0), 2));
        robots.add(new TestDummy04(3, new Position(17, 19, 0), 2));
        robots.add(new TestDummy04(4, new Position(30, 18, 0), 2));
        robots.add(new TestDummy04(5, new Position(21, 30, 0), 2));
        SimEngine engine = setup(robots, someObstacles());
        startRobots(robots);
        engine.runUntil(seconds);
        engine.stop();
        List<double[]> poses = new ArrayList<>();
        for (RobotDummy r : robots) {
            Position p = r.getPosition();
            poses.add(new double[] { p.x, p.y, p.theta });
        }
        return poses;
    }

    @Test
    @Timeout(60)
    void sameScenarioGivesSameResult() {
        List<double[]> a = runSwarm(30);
        List<double[]> b = runSwarm(30);
        for (int i = 0; i < a.size(); i++) {
            assertArrayEquals(a.get(i), b.get(i), 0.0, "robot " + (i + 1) + " diverso tra due esecuzioni");
        }
    }

    @Test
    @Timeout(20)
    void controllerIsCalledAtItsPeriodInSimTime() {
        List<Double> calls = new CopyOnWriteArrayList<>();
        List<RobotDummy> robots = List.of(new ControllerRobot(1, new Position(20, 20, 0), 2, 0.1,
            (r, t) -> calls.add(t)));
        SimEngine engine = setup(robots, List.of());
        startRobots(robots);
        engine.runUntil(1.0);
        engine.stop();
        // chiamate a t = 0.0, 0.1, ..., 1.0: runUntil ritorna dopo che i robot hanno reagito all'istante finale
        assertEquals(11, calls.size(), "chiamate: " + calls);
        for (int i = 0; i < calls.size(); i++) assertEquals(i * 0.1, calls.get(i), 1e-9);
    }

    @Test
    @Timeout(20)
    void straightMotionMatchesKinematics() {
        List<RobotDummy> robots = List.of(new ControllerRobot(1, new Position(10, 20, 0), 2, 0.1,
            (r, t) -> r.setRobotSpeed(1.0, 0)));   // 1 cella/s verso +x
        SimEngine engine = setup(robots, List.of());
        startRobots(robots);
        engine.runUntil(5.0);
        engine.stop();
        assertEquals(15.0, robots.get(0).getPosition().x, 1e-6);
        assertEquals(20.0, robots.get(0).getPosition().y, 1e-6);
    }

    @Test
    @Timeout(20)
    void robotNeverEntersWall() {
        // parte a 3 celle dal bordo sinistro e spinge contro il muro per 10 s
        List<RobotDummy> robots = List.of(new ControllerRobot(1, new Position(3, 20, Math.PI), 2, 0.1,
            (r, t) -> r.setRobotSpeed(2.0, 0)));
        SimEngine engine = setup(robots, List.of());
        startRobots(robots);
        engine.runUntil(10.0);
        engine.stop();
        double surfaceToWallPx = robots.get(0).getPosition().x * Const.cellSize - Const.unitRadius;
        assertTrue(surfaceToWallPx >= 0, "compenetrazione: " + surfaceToWallPx + " px");
        assertTrue(surfaceToWallPx < Const.sensorsCheckingRange0 + 1, "si è fermato troppo lontano: " + surfaceToWallPx);
    }

    @Test
    @Timeout(20)
    void largeArenaWallsAreAtTheConfiguredSize() {
        // arena 80 x 50 celle: il robot spinge verso destra e verso il basso, oltre i 40 celle dell'arena standard
        double w = 80 * Const.cellSize, h = 50 * Const.cellSize;
        List<RobotDummy> robots = List.of(new ControllerRobot(1, new Position(70, 45, Math.atan2(1, 2)), 2, 0.1,
            (r, t) -> r.setRobotSpeed(2.0, 0)));
        SimClock clock = new SimClock(Const.physicsStep);
        SimEngine engine = new SimEngine(clock, new CollisionDetector(List.of(), w, h), robots);
        engine.setLogger(m -> {});
        robots.get(0).attachClock(clock);
        robots.get(0).start();
        engine.runUntil(20.0);
        engine.stop();
        Position p = robots.get(0).getPosition();
        double rightGap = w - (p.x * Const.cellSize + Const.unitRadius);
        double bottomGap = h - (p.y * Const.cellSize + Const.unitRadius);
        assertTrue(p.x > 60 && p.y > 40, "il robot deve trovarsi oltre l'arena standard: " + p);
        assertTrue(rightGap >= 0 && bottomGap >= 0, "compenetrazione: " + rightGap + ", " + bottomGap);
        assertTrue(Math.min(rightGap, bottomGap) < Const.sensorsCheckingRange0 + 1, "non ha raggiunto il muro: " + p);
    }

    @Test
    @Timeout(20)
    void faultTimeDisablesRobotInSimTime() {
        RobotDummy r = new ControllerRobot(1, new Position(10, 20, 0), 2, 0.1,
            (rb, t) -> rb.setRobotSpeed(1.0, 0), Const.unitColor, 500);
        List<RobotDummy> robots = List.of(r);
        SimEngine engine = setup(robots, List.of());
        startRobots(robots);
        engine.runUntil(2.0);
        engine.stop();
        assertFalse(r.isActive());
        assertEquals(10.5, r.getPosition().x, 0.02);   // si è mosso solo fino a t = 0.5
    }

    @Test
    @Timeout(20)
    void pauseFreezesTimeAndStepAdvances() throws Exception {
        List<RobotDummy> robots = List.of(new ControllerRobot(1, new Position(20, 20, 0), 2, 0.1,
            (r, t) -> r.setRobotSpeed(1.0, 0)));
        SimEngine engine = setup(robots, List.of());
        engine.start();                // parte in pausa
        startRobots(robots);
        Thread.sleep(200);
        assertEquals(0, engine.getClock().getStep(), "in pausa il tempo non deve avanzare");

        engine.step(25);
        waitFor(() -> engine.getClock().getStep() == 25);
        Thread.sleep(100);
        assertEquals(25, engine.getClock().getStep(), "dopo i passi richiesti deve tornare in pausa");

        engine.setSpeed(SimEngine.MAX_SPEED);
        engine.resume();
        waitFor(() -> engine.getClock().now() > 2.0);
        engine.stop();
        engine.join();
        waitFor(() -> !robots.get(0).isActive());
    }

    @Test
    @Timeout(20)
    void editableBeforeStartAndInPauseButNotAfterStop() throws Exception {
        List<RobotDummy> robots = List.of(new ControllerRobot(1, new Position(20, 20, 0), 2, 0.1, (r, t) -> {}));
        SimEngine engine = setup(robots, List.of());
        assertTrue(engine.isEditable(), "prima dell'avvio");
        engine.start();
        startRobots(robots);
        assertTrue(engine.isEditable(), "in pausa");
        engine.resume();
        waitFor(() -> engine.getClock().now() > 0.2);
        assertFalse(engine.isEditable(), "in esecuzione");
        engine.stop();
        engine.join();
        assertTrue(engine.isStopped());
        assertFalse(engine.isEditable(), "dopo lo stop");
    }

    @Test
    @Timeout(20)
    void nonCooperativeRobotDoesNotFreezeSimulation() {
        List<String> log = new CopyOnWriteArrayList<>();
        RobotDummy busy = new RobotDummy(1, new Position(10, 10, 0), 2) {
            @Override public void executeLoop() {
                while (isActive()) Thread.onSpinWait();   // non chiama mai sleepSim()
            }
        };
        List<RobotDummy> robots = List.of(busy);
        SimEngine engine = setup(robots, List.of());
        engine.setLogger(log::add);
        startRobots(robots);
        engine.runUntil(1.0);
        busy.setActive(false);
        engine.stop();
        assertEquals(1.0, engine.getClock().now(), 1e-9);
        assertEquals(1, log.size(), "atteso un solo avviso: " + log);
        assertTrue(log.get(0).contains("Robot #1"));
    }

    private static void waitFor(Supplier<Boolean> cond) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10_000;
        while (!cond.get()) {
            if (System.currentTimeMillis() > deadline) fail("condizione non raggiunta");
            Thread.sleep(5);
        }
    }

}
