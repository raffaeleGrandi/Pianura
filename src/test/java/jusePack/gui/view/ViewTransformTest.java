package jusePack.gui.view;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;

import org.junit.jupiter.api.Test;

class ViewTransformTest {

    private static final double EPS = 1e-9;

    @Test
    void worldScreenRoundTrip() {
        ViewTransform vt = new ViewTransform();
        vt.setScaleAt(2.5, 0, 0);
        vt.pan(40, -12);
        Point2D s = vt.toScreen(123.4, 56.7);
        Point2D w = vt.toWorld(s.getX(), s.getY());
        assertEquals(123.4, w.getX(), EPS);
        assertEquals(56.7, w.getY(), EPS);
        // asAffine coincide con toScreen
        Point2D a = vt.asAffine().transform(new Point2D.Double(123.4, 56.7), null);
        assertEquals(s.getX(), a.getX(), EPS);
        assertEquals(s.getY(), a.getY(), EPS);
    }

    @Test
    void fitCentersWorldAndRespectsPadding() {
        ViewTransform vt = new ViewTransform();
        // mondo 1200x750 (80x50 celle) in una vista 800x600 con margine 10
        vt.fit(new Rectangle2D.Double(0, 0, 1200, 750), 800, 600, 10);
        assertEquals(780.0 / 1200, vt.getScale(), EPS);           // limita la larghezza
        Point2D tl = vt.toScreen(0, 0), br = vt.toScreen(1200, 750);
        assertEquals(10, tl.getX(), EPS);
        assertEquals(790, br.getX(), EPS);
        assertEquals(300, (tl.getY() + br.getY()) / 2, EPS);     // centrato in verticale
    }

    @Test
    void zoomKeepsPointUnderCursorFixed() {
        ViewTransform vt = new ViewTransform();
        vt.fit(new Rectangle2D.Double(0, 0, 600, 600), 700, 700, 0);
        Point2D before = vt.toWorld(250, 410);
        vt.zoomAt(1.7, 250, 410);
        Point2D after = vt.toWorld(250, 410);
        assertEquals(before.getX(), after.getX(), 1e-6);
        assertEquals(before.getY(), after.getY(), 1e-6);
    }

    @Test
    void scaleIsClamped() {
        ViewTransform vt = new ViewTransform();
        vt.zoomAt(1e6, 0, 0);
        assertEquals(ViewTransform.MAX_SCALE, vt.getScale(), EPS);
        vt.zoomAt(1e-9, 0, 0);
        assertEquals(ViewTransform.MIN_SCALE, vt.getScale(), EPS);
    }

}
