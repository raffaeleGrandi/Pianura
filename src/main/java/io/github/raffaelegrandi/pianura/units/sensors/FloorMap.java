package io.github.raffaelegrandi.pianura.units.sensors;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.function.Consumer;

import io.github.raffaelegrandi.pianura.gui.floor.FloorStyle;
import io.github.raffaelegrandi.pianura.utils.Const;

/**
 * The arena floor as a pyramid of tiles, read by the optical floor sensors and shown by the view.
 *
 * The floor is never painted as one huge image: it is cut into tiles of {@value #TILE}x{@value #TILE}
 * pixels, painted by the {@link FloorStyle} only when somebody needs them and kept in a bounded cache.
 *
 * <ul>
 *   <li><b>Level 0</b> has {@code scale} image pixels per world pixel. The sensors always read it,
 *       so what they read does not depend on the zoom of the view.</li>
 *   <li>Each further level halves the resolution, down to the <b>overview</b>: the whole floor in one
 *       image of at most {@value #OVERVIEW_MAX} pixels per side, always ready (for the zoomed-out view and
 *       the minimap).</li>
 * </ul>
 *
 * Tiles depend only on the style and on their position, so the same tile is identical whenever it is
 * (re)painted: an evicted tile comes back pixel for pixel, and the sensors are reproducible.
 * Immutable from the outside and thread-safe: the engine samples it while the view draws it.
 */
public final class FloorMap {

    /** Side of a tile in image pixels. */
    public static final int TILE = 128;
    /** Maximum side of the overview image. */
    public static final int OVERVIEW_MAX = 1024;
    /** Tiles kept in memory (~64 KB each). */
    private static final int CACHE_TILES = 1536;

    private final FloorStyle style;
    private final double scale;
    private final double widthPx, heightPx;
    private final int levels;                    // levels 0 .. levels-1; the last one is the overview
    private final int[] levelW, levelH;
    private final BufferedImage overview;

    private final Map<Long, BufferedImage> cache = new LinkedHashMap<>(256, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Long, BufferedImage> e) { return size() > CACHE_TILES; }
    };

    // background painting of the tiles asked by the view (newest request first)
    private final LinkedBlockingDeque<Request> requests = new LinkedBlockingDeque<>();
    private final java.util.Set<Long> pending = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private volatile Thread painter;
    private record Request(long key, int level, int tx, int ty, Runnable onReady) {}

    /**
     * @param arenaWidthPx  arena width in world pixels (cells x Const.cellSize)
     * @param arenaHeightPx arena height in world pixels
     * @param scale         image pixels per world pixel at level 0 (see {@link #defaultScale})
     */
    public FloorMap(FloorStyle style, double arenaWidthPx, double arenaHeightPx, double scale) {
        this.style = style != null ? style : FloorStyle.checkerboard();
        this.scale = scale;
        this.widthPx = arenaWidthPx;
        this.heightPx = arenaHeightPx;
        int l = 0;
        while (Math.max(arenaWidthPx, arenaHeightPx) * scale / (1L << l) > OVERVIEW_MAX) l++;
        levels = l + 1;
        levelW = new int[levels];
        levelH = new int[levels];
        for (int k = 0; k < levels; k++) {
            levelW[k] = Math.max(1, (int) Math.round(arenaWidthPx * levelScale(k)));
            levelH[k] = Math.max(1, (int) Math.round(arenaHeightPx * levelScale(k)));
        }
        int top = levels - 1;
        overview = new BufferedImage(levelW[top], levelH[top], BufferedImage.TYPE_INT_RGB);
        Graphics2D g = overview.createGraphics();
        this.style.paintRegion(g, 0, 0, levelW[top], levelH[top], levelW[top], levelH[top], Const.cellSize * levelScale(top));
        g.dispose();
    }

    /** 2 image pixels per world pixel, 1 for very large arenas (more than ~8 Mpixel at level 0). */
    public static double defaultScale(double arenaWidthPx, double arenaHeightPx) {
        return arenaWidthPx * arenaHeightPx * 4 <= 8e6 ? 2 : 1;
    }

    public FloorStyle getStyle() { return style; }

    /** Image pixels per world pixel at level 0. */
    public double getScale() { return scale; }

    public double getWidthPx() { return widthPx; }
    public double getHeightPx() { return heightPx; }

    /** Number of levels; level {@code levels() - 1} is the overview. */
    public int levels() { return levels; }

    /** Image pixels per world pixel at level {@code k}. */
    public double levelScale(int k) { return scale / (1L << k); }

    public int levelWidth(int k) { return levelW[k]; }
    public int levelHeight(int k) { return levelH[k]; }

    /** The whole floor at the coarsest level (at most {@value #OVERVIEW_MAX} pixels per side). Do not modify. */
    public BufferedImage overview() { return overview; }

    /** Level whose resolution is just enough for {@code screenPerWorld} screen pixels per world pixel. */
    public int levelFor(double screenPerWorld) {
        int k = 0;
        while (k < levels - 1 && levelScale(k + 1) >= screenPerWorld) k++;
        return k;
    }

    // ------------------------------------------------------------------ tiles

    private static long key(int level, int tx, int ty) { return ((long) level << 56) | ((long) tx << 28) | ty; }

    /** Tile (level, tx, ty), painted now if it is not in the cache. Thread-safe. */
    public BufferedImage tile(int level, int tx, int ty) {
        long k = key(level, tx, ty);
        BufferedImage img;
        synchronized (cache) { img = cache.get(k); }
        if (img != null) return img;
        img = paintTile(level, tx, ty);
        synchronized (cache) {
            BufferedImage other = cache.putIfAbsent(k, img);
            return other != null ? other : img;
        }
    }

    /**
     * Tile (level, tx, ty) if it is ready, otherwise null: it is then painted in the background and
     * {@code onReady} is called when it is (from the painter thread). For the view, which must not wait.
     */
    public BufferedImage tileIfReady(int level, int tx, int ty, Runnable onReady) {
        long k = key(level, tx, ty);
        synchronized (cache) {
            BufferedImage img = cache.get(k);
            if (img != null) return img;
        }
        if (pending.add(k)) {
            requests.addFirst(new Request(k, level, tx, ty, onReady));
            while (requests.size() > 256) { Request old = requests.pollLast(); if (old != null) pending.remove(old.key()); }
            startPainter();
        }
        return null;
    }

    /** Forgets the background requests not yet served (e.g. the view moved elsewhere). */
    public void cancelPending() {
        requests.clear();
        pending.clear();
    }

    /** Stops the background painter (when this floor is replaced). The map can still be read. */
    public void dispose() {
        disposed = true;
        cancelPending();
        Thread t = painter;
        if (t != null) t.interrupt();
    }

    private volatile boolean disposed;

    private void startPainter() {
        if (painter != null || disposed) return;
        synchronized (requests) {
            if (painter != null) return;
            painter = Thread.ofPlatform().name("juse-floor-tiles").daemon(true).start(() -> {
                while (true) {
                    Request r;
                    try { r = requests.takeFirst(); } catch (InterruptedException e) { return; }
                    try {
                        tile(r.level(), r.tx(), r.ty());
                    } finally {
                        pending.remove(r.key());
                    }
                    if (r.onReady() != null) r.onReady().run();
                }
            });
        }
    }

    private BufferedImage paintTile(int level, int tx, int ty) {
        int x0 = tx * TILE, y0 = ty * TILE;
        int w = Math.max(1, Math.min(TILE, levelW[level] - x0)), h = Math.max(1, Math.min(TILE, levelH[level] - y0));
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        style.paintRegion(g, x0, y0, w, h, levelW[level], levelH[level], Const.cellSize * levelScale(level));
        g.dispose();
        return img;
    }

    /** Number of tiles across / down at level {@code k}. */
    public int tilesX(int k) { return (levelW[k] + TILE - 1) / TILE; }
    public int tilesY(int k) { return (levelH[k] + TILE - 1) / TILE; }

    /** Tiles currently in memory (for tests and diagnostics). */
    public int cachedTiles() { synchronized (cache) { return cache.size(); } }

    // ------------------------------------------------------------------ sensors

    /** RGB of level-0 pixel (px, py), already clamped to the image. */
    private int pixel(int px, int py, BufferedImage[] last, int[] lastKey) {
        int tx = px / TILE, ty = py / TILE;
        int k = tx * 65536 + ty;
        if (last[0] == null || lastKey[0] != k) { last[0] = tile(0, tx, ty); lastKey[0] = k; }
        return last[0].getRGB(px - tx * TILE, py - ty * TILE);
    }

    /**
     * Mean colour (packed RGB) of the floor within {@code radiusPx} world pixels of (x, y), at level 0.
     * With a very small radius the single pixel is read. Outside the arena the nearest border is read.
     */
    public int sampleRGB(double x, double y, double radiusPx) {
        int iw = levelW[0], ih = levelH[0];
        double cx = clamp(x, 0, widthPx - 1e-6) * scale, cy = clamp(y, 0, heightPx - 1e-6) * scale;
        double r = Math.max(0, radiusPx * scale);
        int x0 = (int) Math.floor(cx - r), x1 = (int) Math.floor(cx + r);
        int y0 = (int) Math.floor(cy - r), y1 = (int) Math.floor(cy + r);
        BufferedImage[] last = new BufferedImage[1];
        int[] lastKey = new int[1];
        long sr = 0, sg = 0, sb = 0, n = 0;
        for (int py = Math.max(0, y0); py <= Math.min(ih - 1, y1); py++) {
            for (int px = Math.max(0, x0); px <= Math.min(iw - 1, x1); px++) {
                double dx = px + 0.5 - cx, dy = py + 0.5 - cy;
                if (r > 0.5 && dx * dx + dy * dy > r * r) continue;   // circular footprint
                int rgb = pixel(px, py, last, lastKey);
                sr += (rgb >> 16) & 0xff; sg += (rgb >> 8) & 0xff; sb += rgb & 0xff; n++;
            }
        }
        if (n == 0) return pixel((int) clamp(cx, 0, iw - 1), (int) clamp(cy, 0, ih - 1), last, lastKey) & 0xffffff;
        return (int) ((sr / n) << 16 | (sg / n) << 8 | (sb / n));
    }

    /** Perceived brightness 0 (black) – 255 (white) of an RGB colour. */
    public static int gray(int rgb) {
        int r = (rgb >> 16) & 0xff, g = (rgb >> 8) & 0xff, b = rgb & 0xff;
        return (int) Math.round(0.299 * r + 0.587 * g + 0.114 * b);
    }

    public static Color color(int rgb) { return new Color(rgb & 0xffffff); }

    /** Calls {@code action} for each tile of level {@code k} that intersects the world rectangle (pixels). */
    public void forEachTile(int k, double wx0, double wy0, double wx1, double wy1, Consumer<int[]> action) {
        double s = levelScale(k);
        int tx0 = Math.max(0, (int) Math.floor(wx0 * s / TILE)), tx1 = Math.min(tilesX(k) - 1, (int) Math.floor(wx1 * s / TILE));
        int ty0 = Math.max(0, (int) Math.floor(wy0 * s / TILE)), ty1 = Math.min(tilesY(k) - 1, (int) Math.floor(wy1 * s / TILE));
        for (int ty = ty0; ty <= ty1; ty++) for (int tx = tx0; tx <= tx1; tx++) action.accept(new int[] { tx, ty });
    }

    private static double clamp(double v, double lo, double hi) { return Math.max(lo, Math.min(hi, v)); }

}
