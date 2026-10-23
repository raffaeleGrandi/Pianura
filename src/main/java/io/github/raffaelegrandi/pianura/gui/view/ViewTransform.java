package io.github.raffaelegrandi.pianura.gui.view;

import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;

/**
 * Trasformazione mondo → schermo della vista: {@code schermo = mondo * scale + offset}.
 * Il mondo è in pixel di mondo (celle x Const.cellSize); lo schermo in pixel del pannello.
 * Nessuna dipendenza da Swing: è la parte testabile della vista.
 */
public final class ViewTransform {

    public static final double MIN_SCALE = 0.05;
    public static final double MAX_SCALE = 20;

    private double scale = 1, offX = 0, offY = 0;

    public double getScale() { return scale; }
    public double getOffsetX() { return offX; }
    public double getOffsetY() { return offY; }

    public AffineTransform asAffine() {
        AffineTransform at = AffineTransform.getTranslateInstance(offX, offY);
        at.scale(scale, scale);
        return at;
    }

    public Point2D toScreen(double wx, double wy) { return new Point2D.Double(wx * scale + offX, wy * scale + offY); }

    public Point2D toWorld(double sx, double sy) { return new Point2D.Double((sx - offX) / scale, (sy - offY) / scale); }

    /** Adatta il rettangolo di mondo alla vista, centrato, con un margine in pixel di schermo. */
    public void fit(Rectangle2D world, double viewW, double viewH, double paddingPx) {
        double availW = Math.max(1, viewW - 2 * paddingPx), availH = Math.max(1, viewH - 2 * paddingPx);
        scale = clamp(Math.min(availW / world.getWidth(), availH / world.getHeight()));
        offX = (viewW - world.getWidth() * scale) / 2 - world.getX() * scale;
        offY = (viewH - world.getHeight() * scale) / 2 - world.getY() * scale;
    }

    /** Moltiplica lo zoom per {@code factor} tenendo fermo il punto di schermo (sx, sy). */
    public void zoomAt(double factor, double sx, double sy) {
        setScaleAt(scale * factor, sx, sy);
    }

    /** Imposta lo zoom tenendo fermo il punto di schermo (sx, sy). */
    public void setScaleAt(double newScale, double sx, double sy) {
        Point2D w = toWorld(sx, sy);
        scale = clamp(newScale);
        offX = sx - w.getX() * scale;
        offY = sy - w.getY() * scale;
    }

    /** Sposta la vista di (dx, dy) pixel di schermo. */
    public void pan(double dx, double dy) {
        offX += dx;
        offY += dy;
    }

    /** Punto di mondo al centro di una vista di {@code viewW x viewH} pixel. */
    public Point2D center(double viewW, double viewH) { return toWorld(viewW / 2, viewH / 2); }

    /** Porta il punto di mondo (wx, wy) al centro della vista, senza cambiare lo zoom. */
    public void centerOn(double wx, double wy, double viewW, double viewH) {
        offX = viewW / 2 - wx * scale;
        offY = viewH / 2 - wy * scale;
    }

    /**
     * Avvicina il centro della vista al punto (wx, wy) della frazione {@code f} (0 = fermo, 1 = subito):
     * usato per seguire un robot in modo morbido.
     */
    public void moveCenterToward(double wx, double wy, double viewW, double viewH, double f) {
        Point2D c = center(viewW, viewH);
        centerOn(c.getX() + (wx - c.getX()) * f, c.getY() + (wy - c.getY()) * f, viewW, viewH);
    }

    /**
     * Tiene il centro della vista dentro il rettangolo di mondo: si può volare sopra la mappa
     * ma non perdersi nel vuoto attorno.
     */
    public void clampCenter(Rectangle2D world, double viewW, double viewH) {
        Point2D c = center(viewW, viewH);
        double x = Math.max(world.getMinX(), Math.min(world.getMaxX(), c.getX()));
        double y = Math.max(world.getMinY(), Math.min(world.getMaxY(), c.getY()));
        if (x != c.getX() || y != c.getY()) centerOn(x, y, viewW, viewH);
    }

    /**
     * Direzione di scorrimento quando il puntatore è vicino al bordo della vista:
     * {-1, 0, +1} per x e per y (+1 = verso destra / verso il basso del mondo).
     */
    public static int[] edgeDirection(double x, double y, double viewW, double viewH, double marginPx) {
        int dx = x < marginPx ? -1 : (x > viewW - marginPx ? 1 : 0);
        int dy = y < marginPx ? -1 : (y > viewH - marginPx ? 1 : 0);
        return new int[] { dx, dy };
    }

    private static double clamp(double s) { return Math.max(MIN_SCALE, Math.min(MAX_SCALE, s)); }

}
