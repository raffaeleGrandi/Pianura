package jusePack.gui.view;

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

    private static double clamp(double s) { return Math.max(MIN_SCALE, Math.min(MAX_SCALE, s)); }

}
