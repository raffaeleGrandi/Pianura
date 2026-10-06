package jusePack.gui.floor;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Random;

/**
 * Lastre irregolari (diagramma di Voronoi), stile pavimentazione in pietra.
 *
 * Si spargono punti a caso; ogni pixel prende il colore del punto più vicino e
 * diventa "fuga" dove i due punti più vicini sono quasi equidistanti. Il calcolo
 * è fatto una volta sola all'avvio (qualche decina di ms per l'arena standard).
 * Con lo stesso seed il pavimento è sempre identico.
 */
public class VoronoiFloor implements FloorStyle {

    private final Color base;
    private final double meanCellCells;
    private final int variation;
    private final long seed;

    /**
     * @param base          colore medio delle lastre
     * @param meanCellCells dimensione media di una lastra in celle
     * @param variation     variazione massima di luminosità tra lastre (0–255)
     * @param seed          seme del generatore casuale
     */
    public VoronoiFloor(Color base, double meanCellCells, int variation, long seed) {
        if (!(meanCellCells > 0)) throw new IllegalArgumentException("meanCellCells must be > 0");
        this.base = base;
        this.meanCellCells = meanCellCells;
        this.variation = Math.max(0, variation);
        this.seed = seed;
    }

    @Override
    public void paint(Graphics2D g, int width, int height, double cellSizePx) {
        Random rnd = new Random(seed);
        double cellPx = meanCellCells * cellSizePx;
        int n = Math.max(2, (int) Math.round((double) width * height / (cellPx * cellPx)));

        // punti su una griglia "jittered": distribuzione regolare ma non ordinata
        int gx = Math.max(1, (int) Math.round(Math.sqrt(n * (double) width / height)));
        int gy = Math.max(1, (int) Math.ceil((double) n / gx));
        double sx = (double) width / gx, sy = (double) height / gy;
        int count = gx * gy;
        double[] px = new double[count], py = new double[count];
        int[] colors = new int[count];
        for (int j = 0, k = 0; j < gy; j++) {
            for (int i = 0; i < gx; i++, k++) {
                px[k] = (i + 0.1 + 0.8 * rnd.nextDouble()) * sx;
                py[k] = (j + 0.1 + 0.8 * rnd.nextDouble()) * sy;
                int d = variation == 0 ? 0 : rnd.nextInt(2 * variation + 1) - variation;
                colors[k] = RandomTilesFloor.shade(base, d).getRGB();
            }
        }

        int grout = RandomTilesFloor.shade(base, -70).getRGB();
        double groutPx = 1.6;
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < height; y++) {
            int cj = Math.min(gy - 1, (int) (y / sy));
            for (int x = 0; x < width; x++) {
                int ci = Math.min(gx - 1, (int) (x / sx));
                // con la griglia jittered il punto più vicino sta nelle 5x5 celle attorno
                double d1 = Double.MAX_VALUE, d2 = Double.MAX_VALUE;
                int best = 0;
                for (int j = Math.max(0, cj - 2); j <= Math.min(gy - 1, cj + 2); j++) {
                    for (int i = Math.max(0, ci - 2); i <= Math.min(gx - 1, ci + 2); i++) {
                        int k = j * gx + i;
                        double dx = x - px[k], dy = y - py[k];
                        double d = dx * dx + dy * dy;
                        if (d < d1) { d2 = d1; d1 = d; best = k; }
                        else if (d < d2) d2 = d;
                    }
                }
                // distanza dalla linea di confine tra i due punti più vicini ≈ (√d2 − √d1)/2
                boolean edge = (Math.sqrt(d2) - Math.sqrt(d1)) / 2 < groutPx / 2 + 0.5;
                img.setRGB(x, y, edge ? grout : colors[best]);
            }
        }
        // grana leggera
        for (int k = 0, grains = width * height / 30; k < grains; k++) {
            int x = rnd.nextInt(width), y = rnd.nextInt(height);
            if (img.getRGB(x, y) != grout)
                img.setRGB(x, y, RandomTilesFloor.shade(new Color(img.getRGB(x, y)), rnd.nextInt(25) - 12).getRGB());
        }
        g.drawImage(img, 0, 0, null);
    }

}
