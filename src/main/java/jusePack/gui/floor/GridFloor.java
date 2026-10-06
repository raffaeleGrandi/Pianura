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
        g.setColor(background);
        g.fillRect(0, 0, width, height);
        g.setColor(lines);
        double s = spacingCells * cellSizePx;
        for (double x = s; x < width; x += s) { int xi = (int) Math.round(x); g.drawLine(xi, 0, xi, height); }
        for (double y = s; y < height; y += s) { int yi = (int) Math.round(y); g.drawLine(0, yi, width, yi); }
    }

}
