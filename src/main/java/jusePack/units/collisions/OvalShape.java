package jusePack.units.collisions;

/**
 * Ellisse per il rilevamento collisioni geometrico, eventualmente ruotata.
 * La posizione è il centro dell'ellisse (in pixel); l'angolo segue la convenzione
 * dei robot (coordinate schermo, positivo = orario).
 *
 * Il raggio viene portato nel sistema di riferimento dell'ellisse (rotazione inversa),
 * dove l'ellisse è allineata agli assi; poi si sostituisce l'equazione parametrica
 * del raggio nell'equazione dell'ellisse e si risolve la quadratica:
 *
 * Con origine (ox, oy) e direzione unitaria (dx, dy), nel riferimento locale:
 *   A·t² + B·t + C = 0
 * dove:
 *   A = dx²/a² + dy²/b²
 *   B = 2·(rx·dx/a² + ry·dy/b²)    rx = ox - cx
 *   C = rx²/a² + ry²/b² - 1        ry = oy - cy
 *
 * Il parametro t corrisponde alla distanza euclidea reale
 * poiché (dx, dy) è un vettore unitario (la rotazione conserva le lunghezze).
 */
public class OvalShape implements CollidableShape {

    private final double cx, cy;      // centro in pixel
    private final double a, b;        // semi-assi in pixel
    private final double angle;
    private final double cosA, sinA;  // cos(-angle), sin(-angle) per la trasformazione inversa

    /** Ellisse allineata agli assi. */
    public OvalShape(double cx, double cy, double width, double height) {
        this(cx, cy, width, height, 0);
    }

    /**
     * @param cx     centro X in pixel
     * @param cy     centro Y in pixel
     * @param width  larghezza dell'ellisse (asse x locale) in pixel
     * @param height altezza dell'ellisse (asse y locale) in pixel
     * @param angle  rotazione in radianti
     */
    public OvalShape(double cx, double cy, double width, double height, double angle) {
        this.cx = cx;
        this.cy = cy;
        this.a = width / 2.0;
        this.b = height / 2.0;
        this.angle = angle;
        this.cosA = Math.cos(-angle);
        this.sinA = Math.sin(-angle);
    }

    @Override
    public java.awt.Shape outline() {
        java.awt.Shape e = new java.awt.geom.Ellipse2D.Double(-a, -b, 2 * a, 2 * b);
        java.awt.geom.AffineTransform at = java.awt.geom.AffineTransform.getTranslateInstance(cx, cy);
        at.rotate(angle);
        return at.createTransformedShape(e);
    }

    @Override
    public double rayIntersect(double ox, double oy, double dx0, double dy0, double maxDist) {
        // riferimento locale: centro nell'origine, assi dell'ellisse allineati
        double px = ox - cx, py = oy - cy;
        double rx = cosA * px - sinA * py;
        double ry = sinA * px + cosA * py;
        double dx = cosA * dx0 - sinA * dy0;
        double dy = sinA * dx0 + cosA * dy0;

        double A = (dx * dx) / (a * a) + (dy * dy) / (b * b);
        double B = 2.0 * (rx * dx / (a * a) + ry * dy / (b * b));
        double C = (rx * rx) / (a * a) + (ry * ry) / (b * b) - 1.0;

        double disc = B * B - 4.0 * A * C;
        if (disc < 0) return -1;  // nessuna intersezione

        double sqrtDisc = Math.sqrt(disc);
        double t1 = (-B - sqrtDisc) / (2.0 * A);
        double t2 = (-B + sqrtDisc) / (2.0 * A);

        // Entrambe le intersezioni sono dietro all'origine
        if (t2 < 0) return -1;

        // Origine dentro l'ellisse (t1 < 0 < t2): intersezione immediata
        if (t1 < 0) return 0;

        return (t1 <= maxDist) ? t1 : -1;
    }

}
