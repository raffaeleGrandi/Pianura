package jusePack.gui.floor;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;

/**
 * Pista per il line-following: un anello scuro su fondo chiaro, con una curva a "S"
 * nel lato inferiore per rendere il percorso meno banale.
 * Lo spessore della linea è in celle (2/3 di cella ≈ distanza tra i due sensori ottici predefiniti).
 */
public class TrackFloor implements FloorStyle {

    private final Color background, line;
    private final double lineCells, marginCells;

    public TrackFloor(Color background, Color line, double lineCells, double marginCells) {
        this.background = background;
        this.line = line;
        this.lineCells = lineCells;
        this.marginCells = marginCells;
    }

    @Override
    public void paint(Graphics2D g, int width, int height, double cellSizePx) {
        g.setColor(background);
        g.fillRect(0, 0, width, height);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(line);
        BasicStroke stroke = new BasicStroke((float) (lineCells * cellSizePx), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);

        double m = marginCells * cellSizePx;
        double w = width - 2 * m, h = height - 2 * m;
        double r = Math.min(w, h) * 0.35;
        // anello con angoli arrotondati: lato superiore e laterali
        Path2D p = new Path2D.Double();
        double x0 = m, y0 = m, x1 = m + w, y1 = m + h;
        p.moveTo(x0 + r, y0);
        p.lineTo(x1 - r, y0);
        p.quadTo(x1, y0, x1, y0 + r);
        p.lineTo(x1, y1 - r);
        p.quadTo(x1, y1, x1 - r, y1);
        // lato inferiore con una "S"
        double sx = (x0 + x1) / 2, amp = h * 0.18;
        p.curveTo(sx + w * 0.15, y1, sx + w * 0.1, y1 - amp, sx, y1 - amp);
        p.curveTo(sx - w * 0.1, y1 - amp, sx - w * 0.15, y1, x0 + r, y1);
        p.quadTo(x0, y1, x0, y1 - r);
        p.lineTo(x0, y0 + r);
        p.quadTo(x0, y0, x0 + r, y0);
        p.closePath();
        // linea → poligono in coordinate assolute, poi riempito: il risultato non dipende dal ritaglio,
        // quindi la pista si può disegnare a riquadri senza giunture
        g.fill(flatten(stroke.createStrokedShape(flatten(p))));
    }

    /** Spezzata equivalente alla forma (tolleranza 0.05 pixel). */
    private static Path2D flatten(java.awt.Shape shape) {
        Path2D out = new Path2D.Double();
        out.append(new java.awt.geom.FlatteningPathIterator(shape.getPathIterator(null), 0.05, 16), false);
        return out;
    }

}
