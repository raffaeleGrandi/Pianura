package io.github.raffaelegrandi.pianura.gui.view;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.geom.Point2D;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import io.github.raffaelegrandi.pianura.sim.SimClock;
import io.github.raffaelegrandi.pianura.sim.SimEngine;
import io.github.raffaelegrandi.pianura.units.ControllerRobot;
import io.github.raffaelegrandi.pianura.units.RobotDummy;
import io.github.raffaelegrandi.pianura.units.collisions.CollisionDetector;
import io.github.raffaelegrandi.pianura.utils.Const;
import io.github.raffaelegrandi.pianura.utils.Position;

class RobotGlyphTest {

    @Test
    void labelSitsUpLeftAt45DegreesAtTheGivenDistance() {
        double k = 30 / Math.sqrt(2);                                        // 2 raggi da 15 px lungo la diagonale
        Point2D a = RobotGlyph.labelCenter(100, 100, 15, 2);
        assertEquals(100 - k, a.getX(), 1e-9);                               // a sinistra
        assertEquals(100 - k, a.getY(), 1e-9);                               // in alto (y cresce verso il basso)
        assertEquals(30, a.distance(100, 100), 1e-9);
        Point2D c = RobotGlyph.labelCenter(100, 100, 15, 0);                 // 0 = sopra il centro
        assertEquals(100, c.getX(), 1e-9);
        assertEquals(100, c.getY(), 1e-9);
        Point2D d = RobotGlyph.labelCenter(100, 100, 15, -3);                // negativo trattato come 0
        assertEquals(100, d.getX(), 1e-9);
    }

    private static RobotDummy run(double v, double w, double seconds, double startX) {
        RobotDummy r = new ControllerRobot(1, new Position(startX, 20, 0), 2, 0.1, (rb, t) -> rb.setRobotSpeed(v, w));
        SimClock clock = new SimClock(Const.physicsStep);
        SimEngine e = new SimEngine(clock, new CollisionDetector(List.of()), List.of(r));
        e.setLogger(m -> {});
        r.attachClock(clock);
        r.start();
        e.runUntil(seconds);
        e.stop();
        return r;
    }

    @Test
    @Timeout(20)
    void wheelTravelStraightAndInPlace() {
        RobotDummy straight = run(1.0, 0, 2.0, 20);
        assertEquals(2.0, straight.getLeftWheelTravel(), 0.02);
        assertEquals(2.0, straight.getRightWheelTravel(), 0.02);

        RobotDummy spin = run(0, 1.0, 1.0, 20);   // rotazione oraria sul posto: sinistra avanti, destra indietro
        assertEquals(1.0, spin.getLeftWheelTravel(), 0.02);
        assertEquals(-1.0, spin.getRightWheelTravel(), 0.02);
    }

    @Test
    @Timeout(20)
    void wheelsKeepTurningWhenPushingAgainstAWall() {
        // parte a 1.5 celle dal bordo destro (40) e spinge per 3 s: il corpo si ferma, le ruote no
        RobotDummy r = run(1.0, 0, 3.0, 38.5);
        assertTrue(r.getPosition().x < 39.01, "il corpo non deve attraversare il muro: " + r.getPosition().x);
        assertEquals(3.0, r.getLeftWheelTravel(), 0.03, "le ruote slittano ma girano");
    }

}
