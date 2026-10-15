package jusePack.gui.floor;

import java.awt.Color;
import java.awt.Graphics2D;

/** Sfondo uniforme con linee di griglia sottili, stile "carta millimetrata". */
public class GridFloor implements FloorStyle {

    private final Color background, lines;
    private final double spacingCells;

    public GridFloor(Color background, Color lines, double spacingCells) {
        if (!(spacingCells > 0)) throw new IllegalArgumentException("spacingCells must be > 0");
        this.background = background;
        this.lines = lines;
        this.spacingCells = spacingCells;
    }

    @Override
    public void paint(Graphics2D g, int width, int height, double cellSizePx) {
        paintRegion(g, 0, 0, width, height, width, height, cellSizePx);
    }

    @Override
    public void paintRegion(Graphics2D g0, int x0, int y0, int w, int h, int width, int height, double cellSizePx) {
        Graphics2D g = (Graphics2D) g0.create();
        g.clipRect(0, 0, w, h);
        g.translate(-x0, -y0);
        g.setColor(background);
        g.fillRect(x0, y0, w, h);
        g.setColor(lines);
        double s = spacingCells * cellSizePx;
        for (long k = Math.max(1, (long) Math.floor(x0 / s)); k * s < Math.min(width, x0 + w + 1); k++) {
            int xi = (int) Math.round(k * s);
            g.drawLine(xi, y0, xi, y0 + h);
        }
        for (long k = Math.max(1, (long) Math.floor(y0 / s)); k * s < Math.min(height, y0 + h + 1); k++) {
            int yi = (int) Math.round(k * s);
            g.drawLine(x0, yi, x0 + w, yi);
        }
        g.dispose();
    }

}
