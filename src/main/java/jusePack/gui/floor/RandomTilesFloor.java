package jusePack.gui.floor;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Random;

/**
 * Piastrelle quadrate con tonalità leggermente diverse, un po' di grana e fughe scure.
 * Generato proceduralmente: con lo stesso seed il pavimento è sempre identico.
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
        Random rnd = new Random(seed);
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        double s = tileCells * cellSizePx;
        int rows = (int) Math.ceil(height / s), cols = (int) Math.ceil(width / s);

        // 1. piastrelle: tonalità per piastrella
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                int d = variation == 0 ? 0 : rnd.nextInt(2 * variation + 1) - variation;
                int rgb = shade(base, d).getRGB();
                int x0 = (int) Math.round(c * s), y0 = (int) Math.round(r * s);
                int x1 = Math.min(width, (int) Math.round((c + 1) * s)), y1 = Math.min(height, (int) Math.round((r + 1) * s));
                for (int y = y0; y < y1; y++) for (int x = x0; x < x1; x++) img.setRGB(x, y, rgb);
            }
        }
        // 2. grana: piccole variazioni casuali su ~4% dei pixel
        int grains = width * height / 25;
        for (int k = 0; k < grains; k++) {
            int x = rnd.nextInt(width), y = rnd.nextInt(height);
            img.setRGB(x, y, shade(new Color(img.getRGB(x, y)), rnd.nextInt(31) - 15).getRGB());
        }
        // 3. fughe
        Graphics2D gi = img.createGraphics();
        gi.setColor(shade(base, -60));
        for (int c = 0; c <= cols; c++) { int x = (int) Math.round(c * s); gi.fillRect(x - 1, 0, 2, height); }
        for (int r = 0; r <= rows; r++) { int y = (int) Math.round(r * s); gi.fillRect(0, y - 1, width, 2); }
        gi.dispose();

        g.drawImage(img, 0, 0, null);
    }

    /** Schiarisce/scurisce un colore di {@code d} livelli su tutti i canali. */
    static Color shade(Color c, int d) {
        return new Color(clamp(c.getRed() + d), clamp(c.getGreen() + d), clamp(c.getBlue() + d));
    }

    private static int clamp(int v) { return Math.max(0, Math.min(255, v)); }

}
