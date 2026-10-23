package io.github.raffaelegrandi.pianura.units.sensors;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

import io.github.raffaelegrandi.pianura.gui.floor.FloorStyle;
import io.github.raffaelegrandi.pianura.gui.floor.ImageFloor;
import io.github.raffaelegrandi.pianura.utils.Const;

/** The tiled floor must be the same floor, pixel for pixel, as one painted in a single image. */
class FloorTilesTest {

    /** Arena with sizes that are not multiples of the tile, so the last tiles are partial. */
    private static final double W = 37 * Const.cellSize, H = 23 * Const.cellSize;
    private static final double SCALE = 2;

    private static Map<String, FloorStyle> styles() {
        BufferedImage pic = new BufferedImage(50, 30, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = pic.createGraphics();
        g.setColor(Color.ORANGE); g.fillRect(0, 0, 50, 30);
        g.setColor(Color.BLUE); g.fillOval(5, 5, 30, 20);
        g.dispose();
        Map<String, FloorStyle> m = new LinkedHashMap<>();
        m.put("checkerboard", FloorStyle.checkerboard());
        m.put("checkerboard 2.5", FloorStyle.checkerboard(Color.WHITE, Color.GRAY, 2.5));
        m.put("grid", FloorStyle.grid(Color.WHITE, Color.LIGHT_GRAY, 1));
        m.put("plain", FloorStyle.plain(new Color(230, 230, 220)));
        m.put("randomTiles", FloorStyle.randomTiles());
        m.put("voronoi", FloorStyle.voronoiTiles());
        m.put("track", FloorStyle.track());
        for (ImageFloor.Mode mode : ImageFloor.Mode.values()) m.put("image " + mode, new ImageFloor(pic, mode));
        return m;
    }

    private static BufferedImage whole(FloorStyle f, int w, int h, double cell) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        f.paint(g, w, h, cell);
        g.dispose();
        return img;
    }

    @Test
    void tilesFitTogetherWithoutSeamsForEveryStyle() {
        for (Map.Entry<String, FloorStyle> e : styles().entrySet()) {
            FloorMap fm = new FloorMap(e.getValue(), W, H, SCALE);
            int w = fm.levelWidth(0), h = fm.levelHeight(0);
            BufferedImage ref = whole(e.getValue(), w, h, Const.cellSize * SCALE);
            int diff = 0;
            for (int ty = 0; ty < fm.tilesY(0); ty++) {
                for (int tx = 0; tx < fm.tilesX(0); tx++) {
                    BufferedImage t = fm.tile(0, tx, ty);
                    for (int y = 0; y < t.getHeight(); y++)
                        for (int x = 0; x < t.getWidth(); x++)
                            if (t.getRGB(x, y) != ref.getRGB(tx * FloorMap.TILE + x, ty * FloorMap.TILE + y)) diff++;
                }
            }
            assertEquals(0, diff, e.getKey() + ": pixels that differ from the single-image floor");
        }
    }

    @Test
    void sensorsReadTheSameColoursAsFromTheSingleImage() {
        FloorStyle f = FloorStyle.voronoiTiles();
        FloorMap fm = new FloorMap(f, W, H, SCALE);
        BufferedImage ref = whole(f, fm.levelWidth(0), fm.levelHeight(0), Const.cellSize * SCALE);
        Random r = new Random(5);
        for (int k = 0; k < 500; k++) {
            double x = r.nextDouble() * W, y = r.nextDouble() * H;
            int px = (int) Math.floor(x * SCALE), py = (int) Math.floor(y * SCALE);
            assertEquals(ref.getRGB(px, py) & 0xffffff, fm.sampleRGB(x, y, 0), "single pixel at " + x + "," + y);
        }
    }

    @Test
    void hugeArenaDoesNotPaintTheWholeFloor() {
        double side = 1000 * Const.cellSize;                     // 1000 x 1000 cells
        Runtime rt = Runtime.getRuntime();
        System.gc();
        long before = rt.totalMemory() - rt.freeMemory();
        long t0 = System.nanoTime();
        FloorMap fm = new FloorMap(FloorStyle.voronoiTiles(), side, side, FloorMap.defaultScale(side, side));
        double seconds = (System.nanoTime() - t0) / 1e9;
        assertTrue(fm.overview().getWidth() <= FloorMap.OVERVIEW_MAX && fm.overview().getHeight() <= FloorMap.OVERVIEW_MAX);
        assertTrue(seconds < 5, "overview in " + seconds + " s");

        Random r = new Random(1);
        int first = 0;
        for (int k = 0; k < 3000; k++) {                       // robots scattered over the whole arena
            int v = fm.sampleRGB(r.nextDouble() * side, r.nextDouble() * side, 2);
            if (k == 0) first = v;
        }
        assertTrue(fm.cachedTiles() <= 1536, "cache bounded: " + fm.cachedTiles());
        System.gc();
        long used = rt.totalMemory() - rt.freeMemory() - before;
        assertTrue(used < 200_000_000L, "memory for the floor: " + used / 1_000_000 + " MB");

        // the first point was evicted long ago: painted again, it is identical
        Random again = new Random(1);
        assertEquals(first, fm.sampleRGB(again.nextDouble() * side, again.nextDouble() * side, 2));
    }

    @Test
    void levelsHalveTheResolutionUpToTheOverview() {
        FloorMap fm = new FloorMap(FloorStyle.checkerboard(), 300 * Const.cellSize, 200 * Const.cellSize, 1);
        assertEquals(1.0, fm.levelScale(0));
        assertEquals(0.5, fm.levelScale(1));
        assertEquals(fm.levels() - 1, fm.levelFor(0.01), "far away: overview");
        assertEquals(0, fm.levelFor(1.5), "zoomed in: full resolution");
        assertEquals(1, fm.levelFor(0.4), "0.4 screen px per world px: level with 0.5 image px");
        assertSame(fm.tile(1, 2, 3), fm.tile(1, 2, 3), "cached");
    }

    @Test
    void backgroundTilesArriveAndCallBack() throws Exception {
        FloorMap fm = new FloorMap(FloorStyle.randomTiles(), W, H, SCALE);
        java.util.concurrent.CountDownLatch ready = new java.util.concurrent.CountDownLatch(1);
        assertNull(fm.tileIfReady(0, 1, 1, ready::countDown), "not painted yet");
        assertTrue(ready.await(5, java.util.concurrent.TimeUnit.SECONDS));
        assertNotNull(fm.tileIfReady(0, 1, 1, null));
        fm.dispose();
    }

}
