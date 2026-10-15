package jusePack.gui.view;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.Color;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.List;

import org.junit.jupiter.api.Test;

import jusePack.gui.floor.FloorStyle;
import jusePack.units.RobotDummy;
import jusePack.units.sensors.FloorMap;
import jusePack.utils.Const;
import jusePack.utils.Position;

/** Flying over a large map: keys, following a robot, staying over the arena. */
class NavigationTest {

    private static final double C = Const.cellSize;
    private static final double W = 400 * C, H = 300 * C;          // arena much larger than the view

    private static RobotDummy robot(double x, double y) {
        return new RobotDummy(1, new Position(x, y, 0), 2) { @Override public void executeLoop() {} };
    }

    private static ArenaView view(RobotDummy r) {
        ArenaView v = new ArenaView(new FloorMap(FloorStyle.plain(Color.WHITE), W, H, 1), W, H, List.of(),
            r == null ? List.of() : List.of(r), new ViewOptions());
        v.setSize(800, 600);
        v.getTransform().setScaleAt(1, 0, 0);
        v.centerOn(W / 2, H / 2);
        return v;
    }

    private static Point2D center(ArenaView v) { return v.getTransform().center(v.getWidth(), v.getHeight()); }

    @Test
    void transformHelpers() {
        ViewTransform t = new ViewTransform();
        t.setScaleAt(2, 0, 0);
        t.centerOn(100, 50, 800, 600);
        assertEquals(100, t.center(800, 600).getX(), 1e-9);
        assertEquals(50, t.center(800, 600).getY(), 1e-9);
        t.moveCenterToward(200, 50, 800, 600, 0.25);
        assertEquals(125, t.center(800, 600).getX(), 1e-9);
        t.clampCenter(new Rectangle2D.Double(0, 0, 110, 40), 800, 600);
        assertEquals(110, t.center(800, 600).getX(), 1e-9);
        assertEquals(40, t.center(800, 600).getY(), 1e-9);
        assertArrayEquals(new int[] { -1, 1 }, ViewTransform.edgeDirection(5, 595, 800, 600, 18));
        assertArrayEquals(new int[] { 0, 0 }, ViewTransform.edgeDirection(400, 300, 800, 600, 18));
        assertArrayEquals(new int[] { 1, -1 }, ViewTransform.edgeDirection(790, 2, 800, 600, 18));
    }

    @Test
    void wasdFliesOverTheMapAndShiftIsFaster() {
        ArenaView v = view(null);
        Point2D c0 = center(v);
        v.setPanKey(3, true, false);                 // D: to the right
        v.navigate(0, 1.0);
        double slow = center(v).getX() - c0.getX();
        assertEquals(0.8 * 800, slow, 1e-6, "one second: 80% of the view width");
        assertEquals(c0.getY(), center(v).getY(), 1e-9);
        v.setPanKey(3, false, false);
        v.setPanKey(2, true, true);                  // Shift+S: down, three times faster
        Point2D c1 = center(v);
        v.navigate(0, 0.5);
        assertEquals(0.5 * 3 * 0.8 * 800, center(v).getY() - c1.getY(), 1e-6);
        v.setPanKey(2, false, true);
        assertFalse(v.navigate(0, 0.1), "nothing moves the view any more");
    }

    @Test
    void theViewCannotLeaveTheArena() {
        ArenaView v = view(null);
        v.setPanKey(1, true, false);                 // A: left, for a long time
        for (int i = 0; i < 200; i++) v.navigate(0, 0.1);
        assertEquals(0, center(v).getX(), 1e-6, "the center stops at the left border");
        v.setPanKey(1, false, false);
        v.centerOn(-5000, H + 5000);
        assertEquals(0, center(v).getX(), 1e-6);
        assertEquals(H, center(v).getY(), 1e-6);
    }

    @Test
    void followingKeepsTheRobotCenteredAndKeysStopIt() {
        RobotDummy r = robot(50, 40);
        ArenaView v = view(r);
        v.follow(r);
        for (int i = 0; i < 100; i++) v.navigate(0, 0.05);
        assertEquals(50 * C, center(v).getX(), 0.5);
        assertEquals(40 * C, center(v).getY(), 0.5);

        r.getUnitObject().setPos(new Position(60, 45, 0));   // the robot moves: the view goes after it
        for (int i = 0; i < 100; i++) v.navigate(0, 0.05);
        assertEquals(60 * C, center(v).getX(), 0.5);
        assertSame(r, v.getFollowed());

        v.setPanKey(0, true, false);                 // W: flying by hand stops following
        v.navigate(0, 0.1);
        assertNull(v.getFollowed());
        v.setPanKey(0, false, false);
    }

    @Test
    void minimapShowsTheWholeArenaAndMovesTheView() {
        java.awt.Rectangle mm = Minimap.bounds(800, 600, W, H);
        assertEquals(Minimap.MAX_SIDE, mm.width, "the longer side of the arena fills the minimap");
        assertEquals(Math.round(Minimap.MAX_SIDE * H / W), mm.height);
        assertEquals(800 - Minimap.MARGIN, mm.x + mm.width, "bottom-right corner");
        assertEquals(600 - Minimap.MARGIN, mm.y + mm.height);
        Point2D mid = Minimap.toWorld(mm, mm.x + mm.width / 2.0, mm.y + mm.height / 2.0, W, H);
        assertEquals(W / 2, mid.getX(), W / mm.width);
        assertEquals(0, Minimap.toWorld(mm, mm.x - 50, mm.y, W, H).getX(), "clamped to the arena");

        ArenaView v = view(null);
        assertTrue(v.minimapVisible(), "zoomed in on a large arena");
        v.centerFromMinimap(mm.x + mm.width * 0.25, mm.y + mm.height * 0.75);
        assertEquals(W * 0.25, center(v).getX(), W / mm.width);
        assertEquals(H * 0.75, center(v).getY(), H / mm.height);
        v.fitToWindow();
        assertFalse(v.minimapVisible(), "the whole arena is visible: no minimap");
    }

}
