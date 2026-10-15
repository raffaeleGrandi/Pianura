package jusePack.gui.floor;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

/**
 * Piastrelle quadrate con tonalità leggermente diverse, un po' di grana e fughe scure.
 * Generato proceduralmente: con lo stesso seed il pavimento è sempre identico,
 * e si può disegnare a riquadri senza giunture.
 */
public class RandomTilesFloor implements FloorStyle {

    private final Color base;
    private final double tileCells;
    private final int variation;
    private final long seed;

    /**
     * @param base      colore medio delle piastrelle
     * @param tileCells lato della piastrella in celle
     * @param variation variazione massima di luminosità tra piastrelle (0–255)
     * @param seed      seme del generatore casuale
     */
    public RandomTilesFloor(Color base, double tileCells, int variation, long seed) {
        if (!(tileCells > 0)) throw new IllegalArgumentException("tileCells must be > 0");
        this.base = base;
        this.tileCells = tileCells;
        this.variation = Math.max(0, variation);
        this.seed = seed;
    }

    @Override
    public void paint(Graphics2D g, int width, int height, double cellSizePx) {
        paintRegion(g, 0, 0, width, height, width, height, cellSizePx);
    }

    /**
     * Ogni pixel dipende solo dalla sua posizione e dal seme: tonalità della piastrella,
     * grana (~4% dei pixel) e fughe larghe 2 pixel sui bordi delle piastrelle.
     */
    @Override
    public void paintRegion(Graphics2D g, int x0, int y0, int w, int h, int fullW, int fullH, double cellSizePx) {
        if (w <= 0 || h <= 0) return;
        double s = tileCells * cellSizePx;
        int baseRgb = base.getRGB(), grout = FloorHash.shade(baseRgb, -60);
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        int[] buf = ((java.awt.image.DataBufferInt) img.getRaster().getDataBuffer()).getData();
        boolean[] colGrout = new boolean[w];
        int[] col = new int[w];
        for (int x = 0; x < w; x++) { colGrout[x] = isGrout(x0 + x, s); col[x] = (int) Math.floor((x0 + x) / s); }
        for (int y = 0; y < h; y++) {
            int py = y0 + y;
            boolean rowGrout = isGrout(py, s);
            int row = (int) Math.floor(py / s);
            int lastCol = Integer.MIN_VALUE, tileDelta = 0;
            for (int x = 0; x < w; x++) {
                int rgb;
                if (rowGrout || colGrout[x]) rgb = grout;
                else {
                    if (col[x] != lastCol) { lastCol = col[x]; tileDelta = FloorHash.symmetric(seed, lastCol, row, 1, variation); }
                    int d = tileDelta;
                    long hg = FloorHash.mix(seed, x0 + x, py, 2);
                    if ((hg & 0xffff) < 65536 / 25) d += (int) Math.floorMod(hg >>> 16, 31L) - 15;   // grana
                    rgb = FloorHash.shade(baseRgb, d);
                }
                buf[y * w + x] = rgb;
            }
        }
        g.drawImage(img, 0, 0, null);
    }

    /** Fuga: i due pixel attorno a ogni bordo di piastrella (round(k * s) - 1 e round(k * s)). */
    private static boolean isGrout(int v, double s) {
        long k = Math.round(v / s);
        long b = Math.round(k * s);
        return v == b || v == b - 1;
    }

    /** Schiarisce/scurisce un colore di {@code d} livelli su tutti i canali. */
    static Color shade(Color c, int d) {
        return new Color(clamp(c.getRed() + d), clamp(c.getGreen() + d), clamp(c.getBlue() + d));
    }

    private static int clamp(int v) { return Math.max(0, Math.min(255, v)); }

}
