package jusePack.units.sensors;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import jusePack.gui.floor.FloorStyle;
import jusePack.utils.Const;

/**
 * Pavimento come immagine campionabile dai sensori ottici.
 *
 * L'immagine è disegnata dallo stesso {@link FloorStyle} mostrato nella vista, a
 * {@code scale} pixel di immagine per pixel di mondo: la vista usa questa stessa
 * immagine, quindi quello che si vede è esattamente quello che i sensori leggono.
 * Immutabile: per cambiare pavimento se ne crea una nuova.
 */
public final class FloorMap {

    private final BufferedImage image;
    private final double scale;
    private final double widthPx, heightPx;

    public FloorMap(FloorStyle style, double arenaWidthPx, double arenaHeightPx, double scale) {
        this.scale = scale;
        this.widthPx = arenaWidthPx;
        this.heightPx = arenaHeightPx;
        int w = Math.max(1, (int) Math.round(arenaWidthPx * scale)), h = Math.max(1, (int) Math.round(arenaHeightPx * scale));
        image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        (style != null ? style : FloorStyle.checkerboard()).paint(g, w, h, Const.cellSize * scale);
        g.dispose();
    }

    /** Risoluzione adatta all'arena: 2 pixel per pixel di mondo, meno per arene molto grandi (~8 Mpixel max). */
    public static double defaultScale(double arenaWidthPx, double arenaHeightPx) {
        return Math.max(1, Math.min(2, Math.sqrt(8e6 / (arenaWidthPx * arenaHeightPx))));
    }

    public BufferedImage getImage() { return image; }

    public double getScale() { return scale; }

    /**
     * Colore medio (RGB impacchettato) dei pixel entro {@code radiusPx} (pixel di mondo) dal punto (x, y).
     * Con raggio molto piccolo si legge il singolo pixel. Fuori dall'arena si legge il bordo più vicino.
     */
    public int sampleRGB(double x, double y, double radiusPx) {
        double cx = clamp(x, 0, widthPx - 1e-6) * scale, cy = clamp(y, 0, heightPx - 1e-6) * scale;
        double r = Math.max(0, radiusPx * scale);
        int x0 = (int) Math.floor(cx - r), x1 = (int) Math.floor(cx + r);
        int y0 = (int) Math.floor(cy - r), y1 = (int) Math.floor(cy + r);
        long sr = 0, sg = 0, sb = 0, n = 0;
        for (int py = Math.max(0, y0); py <= Math.min(image.getHeight() - 1, y1); py++) {
            for (int px = Math.max(0, x0); px <= Math.min(image.getWidth() - 1, x1); px++) {
                double dx = px + 0.5 - cx, dy = py + 0.5 - cy;
                if (r > 0.5 && dx * dx + dy * dy > r * r) continue;   // impronta circolare
                int rgb = image.getRGB(px, py);
                sr += (rgb >> 16) & 0xff; sg += (rgb >> 8) & 0xff; sb += rgb & 0xff; n++;
            }
        }
        if (n == 0) return image.getRGB((int) clamp(cx, 0, image.getWidth() - 1), (int) clamp(cy, 0, image.getHeight() - 1)) & 0xffffff;
        return (int) ((sr / n) << 16 | (sg / n) << 8 | (sb / n));
    }

    /** Luminosità percepita 0 (nero) – 255 (bianco) di un colore RGB. */
    public static int gray(int rgb) {
        int r = (rgb >> 16) & 0xff, g = (rgb >> 8) & 0xff, b = rgb & 0xff;
        return (int) Math.round(0.299 * r + 0.587 * g + 0.114 * b);
    }

    public static Color color(int rgb) { return new Color(rgb & 0xffffff); }

    private static double clamp(double v, double lo, double hi) { return Math.max(lo, Math.min(hi, v)); }

}
