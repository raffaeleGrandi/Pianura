package jusePack.gui.view;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.awt.geom.RoundRectangle2D;

/**
 * Disegno di un robot visto dall'alto (corpo, due ruote con battistrada, muso, ruotino)
 * e della sua etichetta a "fumetto".
 *
 * Il robot è disegnato in coordinate di mondo con una rototraslazione; l'etichetta in
 * coordinate di schermo, sempre dritta, alla distanza {@code distanceFromCenter} (in raggi
 * del robot) dal centro, sempre in alto a sinistra a 45° sullo schermo (non ruota col robot):
 * 0 = sopra il centro, 1 = sul bordo.
 */
public final class RobotGlyph {

    private RobotGlyph() {}

    public static final Color NOSE = new Color(240, 190, 40);
    private static final Color WHEEL = new Color(85, 85, 85);
    private static final Color TREAD = new Color(160, 160, 160);
    private static final Color CASTER = new Color(125, 125, 125);
    private static final Font LABEL_FONT = new Font(Font.SANS_SERIF, Font.BOLD, 11);
    /** Sotto questo raggio sullo schermo (pixel) il robot è disegnato semplificato: disco + muso. */
    public static final double DETAIL_MIN_SCREEN_RADIUS = 7;

    /**
     * Robot nel riferimento locale già traslato e ruotato (asse x = direzione di marcia).
     * @param R             raggio in unità di mondo
     * @param px            un pixel di schermo in unità di mondo
     * @param leftTravel    strada percorsa dalla ruota sinistra (unità di mondo): fa scorrere il battistrada
     * @param rightTravel   strada percorsa dalla ruota destra
     */
    public static void paintBody(Graphics2D g, double R, double px, Color body, double leftTravel, double rightTravel) {
        boolean detailed = R / px >= DETAIL_MIN_SCREEN_RADIUS;

        g.setColor(body);
        g.fill(new Ellipse2D.Double(-R, -R, 2 * R, 2 * R));
        // muso: settore colorato davanti
        g.setColor(NOSE);
        g.fill(new Arc2D.Double(-R, -R, 2 * R, 2 * R, -35, 70, Arc2D.PIE));
        if (detailed) {
            // ruote sopra il corpo, sporgenti dal bordo come nella vista dall'alto di un robot reale
            // (sinistra = y negativo: y cresce verso il basso e il robot guarda verso +x)
            // ruote vicine al centro lungo l'asse: sporgono poco dal cerchio del corpo
            double wl = 0.90 * R, ww = 0.34 * R, wy = 0.78 * R;
            paintWheel(g, -wy, wl, ww, px, leftTravel);
            paintWheel(g, wy, wl, ww, px, rightTravel);
            // ruotino posteriore
            double c = 0.16 * R;
            g.setColor(CASTER);
            g.fill(new Ellipse2D.Double(-0.72 * R - c, -c, 2 * c, 2 * c));
        }
    }

    private static void paintWheel(Graphics2D g, double cy, double len, double wid, double px, double travel) {
        RoundRectangle2D wheel = new RoundRectangle2D.Double(-len / 2, cy - wid / 2, len, wid, wid * 0.8, wid * 0.8);
        g.setColor(WHEEL);
        g.fill(wheel);
        // battistrada: righe che scorrono all'indietro quando la ruota avanza
        Shape oldClip = g.getClip();
        g.clip(wheel);
        g.setColor(TREAD);
        g.setStroke(new BasicStroke((float) Math.max(0.9 * px, wid * 0.08)));
        double step = len / 4.5;
        double off = -(travel % step);
        if (off > 0) off -= step;
        for (double x = -len / 2 + off; x <= len / 2 + step; x += step) {
            g.draw(new Line2D.Double(x, cy - wid / 2, x, cy + wid / 2));
        }
        g.setClip(oldClip);
    }

    /** Direzione dell'etichetta sullo schermo: in alto a sinistra a 45° (y cresce verso il basso). */
    public static final double LABEL_DIR_X = -Math.sqrt(0.5), LABEL_DIR_Y = -Math.sqrt(0.5);

    /**
     * Centro dell'etichetta in coordinate di mondo: in alto a sinistra a 45°, a
     * {@code distanceFromCenter} raggi dal centro. Non dipende dall'orientamento del robot,
     * così l'etichetta non gira intorno al robot quando questo ruota.
     */
    public static Point2D labelCenter(double cx, double cy, double R, double distanceFromCenter) {
        double d = Math.max(0, distanceFromCenter) * R;
        return new Point2D.Double(cx + d * LABEL_DIR_X, cy + d * LABEL_DIR_Y);
    }

    /**
     * Etichetta in coordinate di schermo: riquadro arrotondato con il numero, dritto.
     * Se è staccata dal robot, un codino la collega al bordo del robot come un fumetto.
     * @param robotScreen centro del robot sullo schermo
     * @param labelScreen centro dell'etichetta sullo schermo
     * @param robotScreenR raggio del robot sullo schermo
     */
    public static void paintLabel(Graphics2D g, String text, Point2D robotScreen, Point2D labelScreen,
                                  double robotScreenR, Color accent) {
        g.setFont(LABEL_FONT);
        FontMetrics fm = g.getFontMetrics();
        double w = Math.max(18, fm.stringWidth(text) + 10), h = 18;
        double lx = labelScreen.getX(), ly = labelScreen.getY();
        RoundRectangle2D box = new RoundRectangle2D.Double(lx - w / 2, ly - h / 2, w, h, 8, 8);

        // codino: dal bordo dell'etichetta al bordo del robot, solo se c'è spazio tra i due
        double dx = robotScreen.getX() - lx, dy = robotScreen.getY() - ly, dist = Math.hypot(dx, dy);
        double fromBox = Math.min(w, h) / 2;
        if (dist > robotScreenR + fromBox + 2) {
            double ux = dx / dist, uy = dy / dist;
            g.setColor(new Color(60, 60, 60, 200));
            g.setStroke(new BasicStroke(1.2f));
            g.draw(new Line2D.Double(lx + ux * fromBox, ly + uy * fromBox,
                robotScreen.getX() - ux * (robotScreenR + 1), robotScreen.getY() - uy * (robotScreenR + 1)));
        }
        g.setColor(new Color(255, 255, 255, 235));
        g.fill(box);
        g.setColor(accent != null ? accent : new Color(60, 60, 60));
        g.setStroke(new BasicStroke(accent != null ? 2f : 1.2f));
        g.draw(box);
        g.setColor(Color.BLACK);
        g.drawString(text, (float) (lx - fm.stringWidth(text) / 2.0), (float) (ly + fm.getAscent() / 2.0 - 1.5));
    }

    /** Solo per comodità di chi disegna fuori dalla vista: robot completo in coordinate di mondo. */
    public static void paint(Graphics2D g, double cx, double cy, double theta, double R, double px, Color body,
                             double leftTravel, double rightTravel) {
        AffineTransform saved = g.getTransform();
        g.translate(cx, cy);
        g.rotate(theta);
        paintBody(g, R, px, body, leftTravel, rightTravel);
        g.setTransform(saved);
    }

}
