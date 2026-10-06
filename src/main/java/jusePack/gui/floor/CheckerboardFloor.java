package jusePack.gui.floor;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;

/**
 * Pavimento a scacchiera. Il quadrato in alto a sinistra ha il colore {@code c2}
 * (come nella versione originale di JUSE).
 */
public class CheckerboardFloor implements FloorStyle {

    private final Color c1, c2;
    private final double squareCells;
    private final boolean banner;

    public CheckerboardFloor(Color c1, Color c2, double squareCells, boolean banner) {
        if (!(squareCells > 0)) throw new IllegalArgumentException("squareCells must be > 0");
        this.c1 = c1;
        this.c2 = c2;
        this.squareCells = squareCells;
        this.banner = banner;
    }

    /** Colore del quadrato in riga {@code row}, colonna {@code col}. */
    public Color colorAt(int row, int col) {
        return ((row + col) % 2 == 0) ? c2 : c1;
    }

    @Override
    public void paint(Graphics2D g, int width, int height, double cellSizePx) {
        double s = squareCells * cellSizePx;
        int rows = (int) Math.ceil(height / s), cols = (int) Math.ceil(width / s);
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                g.setColor(colorAt(r, c));
                // arrotondamento sui bordi: niente fessure tra quadrati con lato non intero
                int x0 = (int) Math.round(c * s), y0 = (int) Math.round(r * s);
                int x1 = (int) Math.round((c + 1) * s), y1 = (int) Math.round((r + 1) * s);
                g.fillRect(x0, y0, x1 - x0, y1 - y0);
            }
        }
        if (banner) paintBanner(g, rows, cols, s);
    }

    /** "JUSE" nella penultima riga, una lettera per quadrato, nel colore opposto al quadrato. */
    private void paintBanner(Graphics2D g, int rows, int cols, double s) {
        String text = "JUSE";
        int row = rows - 2, firstCol = cols - 1 - text.length();
        if (row < 0 || firstCol < 0) return;
        g.setFont(new Font("SansSerif", Font.PLAIN, Math.max(8, (int) Math.round(s * 0.8))));
        for (int k = 0; k < text.length(); k++) {
            int col = firstCol + k;
            g.setColor(colorAt(row, col) == c1 ? c2 : c1);
            String ch = String.valueOf(text.charAt(k));
            Rectangle2D b = g.getFontMetrics().getStringBounds(ch, g);
            float x = (float) (col * s + (s - b.getWidth()) / 2);
            float y = (float) ((row + 1) * s - 3);
            g.drawString(ch, x, y);
        }
    }

}
