package jusePack.units.sensors;

import java.awt.Color;
import java.awt.geom.Point2D;
import java.util.Arrays;

/**
 * Sensori ottici rivolti verso il pavimento (riflettanza / colore), sotto il robot.
 *
 * Ogni sensore sta a una distanza dal centro pari a {@code radiusFraction} del raggio
 * del robot e a un angolo rispetto alla direzione di marcia (gradi, positivo = verso
 * destra, cioè in senso orario sullo schermo). Legge il colore medio del pavimento
 * in un'area circolare di raggio {@code footprintPx} (pixel di mondo): con un'area
 * più grande il passaggio sul bordo di una linea è più graduale, come per un sensore
 * a riflessione reale.
 *
 * Predefinito: due sensori a 97% del raggio, a −20° (sinistra, indice 0) e +20° (destra, indice 1),
 * distanti tra loro circa 2/3 di cella; impronta di raggio 1.5 px.
 *
 * Thread-safety: come {@code SensorArray}, le letture sono pubblicate in blocco
 * (snapshot immutabile) dal motore e lette dal thread del robot.
 */
public class FloorSensorArray {

    public static final double DEFAULT_RADIUS_FRACTION = 0.97;
    public static final double[] DEFAULT_ANGLES_DEG = { -20, 20 };
    public static final double DEFAULT_FOOTPRINT_PX = 1.5;

    /** Configurazione: posizioni e area sensibile. */
    public record Layout(double radiusFraction, double footprintPx, double[] anglesDeg) {
        public Layout {
            if (radiusFraction < 0) throw new IllegalArgumentException("radiusFraction must be >= 0");
            if (footprintPx < 0) throw new IllegalArgumentException("footprintPx must be >= 0");
            anglesDeg = anglesDeg.clone();
        }
        public int count() { return anglesDeg.length; }
        public double angleRad(int i) { return Math.toRadians(anglesDeg[i]); }
    }

    private record Reading(int[] rgb, Point2D[] positionsCells) {}

    private volatile Layout layout;
    private volatile Reading reading;

    public FloorSensorArray() {
        this(new Layout(DEFAULT_RADIUS_FRACTION, DEFAULT_FOOTPRINT_PX, DEFAULT_ANGLES_DEG));
    }

    public FloorSensorArray(Layout layout) {
        setLayout(layout);
    }

    /** Cambia numero, posizione o area dei sensori (le letture ripartono da zero). */
    public void setLayout(Layout l) {
        layout = l;
        reading = new Reading(new int[l.count()], new Point2D[l.count()]);
    }

    public Layout getLayout() { return layout; }

    public int count() { return layout.count(); }

    /** Pubblica in blocco le nuove letture (chiamato dal motore). */
    public void publish(int[] rgb, Point2D[] positionsCells) {
        reading = new Reading(Arrays.copyOf(rgb, rgb.length), Arrays.copyOf(positionsCells, positionsCells.length));
    }

    /** Colore letto dal sensore i. */
    public Color getColor(int i) { return FloorMap.color(reading.rgb()[i]); }

    /** RGB impacchettato (0xRRGGBB) letto dal sensore i. */
    public int getRGB(int i) { return reading.rgb()[i]; }

    /** Luminosità letta dal sensore i: 0 = nero (linea), 255 = bianco. */
    public int getGray(int i) { return FloorMap.gray(reading.rgb()[i]); }

    /** Luminosità di tutti i sensori (copia). */
    public int[] getGrays() {
        int[] rgb = reading.rgb();
        int[] out = new int[rgb.length];
        for (int i = 0; i < rgb.length; i++) out[i] = FloorMap.gray(rgb[i]);
        return out;
    }

    /** Posizione (in celle) del sensore i all'ultima lettura, o null se non ancora letto. */
    public Point2D getPosition(int i) { return reading.positionsCells()[i]; }

}
