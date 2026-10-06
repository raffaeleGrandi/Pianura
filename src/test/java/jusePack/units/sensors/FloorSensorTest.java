package jusePack.units.sensors;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.Color;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.List;

import org.junit.jupiter.api.Test;

import jusePack.gui.floor.FloorStyle;
import jusePack.sim.SimClock;
import jusePack.sim.SimEngine;
import jusePack.units.RobotDummy;
import jusePack.units.collisions.CollisionDetector;
import jusePack.utils.Const;
import jusePack.utils.Position;

class FloorSensorTest {

    /** Pavimento bianco con una striscia nera verticale da x = 292 a x = 308 px di mondo (larga 16 px ≈ 1 cella). */
    private static final FloorStyle LINE = (g, w, h, cell) -> {
        double k = cell / Const.cellSize;               // pixel di immagine per pixel di mondo
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, w, h);
        g.setColor(Color.BLACK);
        g.fill(new Rectangle2D.Double(292 * k, 0, 16 * k, h));
    };

    private static RobotDummy robot(double xPx, double yPx, double theta) {
        return new RobotDummy(1, new Position(xPx / Const.cellSize, yPx / Const.cellSize, theta), 2) {
            @Override public void executeLoop() {}
        };
    }

    /** Un passo di percezione senza muovere nulla. */
    private static SimEngine sense(RobotDummy r, FloorStyle floor) {
        SimEngine e = new SimEngine(new SimClock(Const.physicsStep), new CollisionDetector(List.of()), List.of(r));
        e.setLogger(m -> {});
        e.setFloor(floor);
        e.runUntil(0);
        return e;
    }

    @Test
    void plainFloorsReadWhiteAndBlack() {
        RobotDummy r = robot(300, 300, 0);
        sense(r, FloorStyle.plain(Color.WHITE));
        assertArrayEquals(new int[] { 255, 255 }, r.getFloorGrays());
        sense(r, FloorStyle.plain(Color.BLACK));
        assertArrayEquals(new int[] { 0, 0 }, r.getFloorGrays());
        assertEquals(Color.BLACK, r.getFloorColor(0));
    }

    @Test
    void lineUnderLeftSensorOnly() {
        // robot rivolto verso l'alto (θ = −90°) con il centro sul bordo destro della linea:
        // sensore sinistro (−20°) a x ≈ 303 → sulla linea; destro (+20°) a x ≈ 313 → bianco
        RobotDummy r = robot(308, 300, -Math.PI / 2);
        sense(r, LINE);
        assertTrue(r.getFloorGray(0) < 20, "sinistro dovrebbe vedere nero: " + r.getFloorGray(0));
        assertTrue(r.getFloorGray(1) > 235, "destro dovrebbe vedere bianco: " + r.getFloorGray(1));
        Point2D left = r.getUnitObject().getFloorSensors().getPosition(0);
        assertEquals(308 - 0.97 * 15 * Math.sin(Math.toRadians(20)), left.getX() * Const.cellSize, 1e-6);
    }

    @Test
    void sensorsAreSymmetricOnCenteredLine() {
        RobotDummy r = robot(300, 300, -Math.PI / 2);
        sense(r, LINE);
        assertEquals(r.getFloorGray(0), r.getFloorGray(1));   // entrambi sulla linea
        assertTrue(r.getFloorGray(0) < 20);
    }

    @Test
    void footprintGivesGradualSignalOnTheEdge() {
        // sensore singolo esattamente sul bordo della linea (x = 308): metà nero, metà bianco
        RobotDummy r = robot(300, 300, 0);
        r.configureFloorSensors(8.0 / 15, 3, 0);             // 8 px davanti al centro, verso destra (θ = 0)
        sense(r, LINE);
        int g = r.getFloorGray(0);
        assertTrue(g > 90 && g < 165, "valore sul bordo dovrebbe essere intermedio: " + g);
        // impronta puntiforme: legge un solo pixel, quindi nero o bianco
        r.configureFloorSensors(8.0 / 15, 0, 0);
        sense(r, LINE);
        int p = r.getFloorGray(0);
        assertTrue(p == 0 || p == 255, "impronta nulla: un solo pixel, letto " + p);
    }

    @Test
    void layoutIsConfigurable() {
        RobotDummy r = robot(300, 300, 0);
        r.configureFloorSensors(0.5, 1, -90, 0, 90);
        sense(r, FloorStyle.plain(Color.WHITE));
        assertEquals(3, r.getFloorSensorCount());
        Point2D left = r.getUnitObject().getFloorSensors().getPosition(0);
        assertEquals(300, left.getX() * Const.cellSize, 1e-6);
        assertEquals(300 - 7.5, left.getY() * Const.cellSize, 1e-6);   // −90° = sinistra di chi guarda verso +x
    }

    @Test
    void changingFloorAtRuntimeUpdatesReadings() {
        RobotDummy r = robot(300, 300, 0);
        SimEngine e = sense(r, FloorStyle.plain(Color.WHITE));
        e.setFloor(FloorStyle.plain(new Color(200, 0, 0)));
        assertEquals(new Color(200, 0, 0), r.getFloorColor(0));
        assertEquals(FloorMap.gray(0xC80000), r.getFloorGray(1));
    }

}
