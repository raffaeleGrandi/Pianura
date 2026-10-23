package io.github.raffaelegrandi.pianura.gui.floor;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.raffaelegrandi.pianura.utils.Const;

class FloorStyleTest {

    private static final int W = Const.arenaWidth, H = Const.arenaHeight;

    private static BufferedImage render(FloorStyle f) {
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        f.paint(g, W, H, Const.cellSize);
        g.dispose();
        return img;
    }

    private static Color px(BufferedImage img, int x, int y) { return new Color(img.getRGB(x, y)); }

    @Test
    void defaultCheckerboardMatchesOriginalPattern() {
        BufferedImage img = render(FloorStyle.checkerboard());
        int c = Const.cellSize;
        // algoritmo originale: cella (riga i, colonna j) = colore2 se i+j pari, altrimenti colore1
        for (int i = 0; i < Const.gridCellCount; i++) {
            for (int j = 0; j < Const.gridCellCount; j++) {
                if (i == Const.gridCellCount - 2 && j >= 35) continue;   // celle con le lettere del marchio
                Color expected = ((i + j) % 2 == 0) ? Const.arenaFloorColor2 : Const.arenaFloorColor1;
                assertEquals(expected, px(img, j * c + 1, i * c + 1), "cella " + i + "," + j);
                assertEquals(expected, px(img, j * c + c - 2, i * c + c - 2), "cella " + i + "," + j);
            }
        }
    }

    @Test
    void customCheckerboardSquareSize() {
        BufferedImage img = render(FloorStyle.checkerboard(Color.WHITE, Color.BLACK, 2));   // quadrati da 30 px
        assertEquals(Color.BLACK, px(img, 5, 5));
        assertEquals(Color.BLACK, px(img, 25, 25));
        assertEquals(Color.WHITE, px(img, 35, 5));
    }

    @Test
    void gridDrawsLinesOnBackground() {
        BufferedImage img = render(FloorStyle.grid(Color.WHITE, Color.GRAY, 2));   // linee ogni 30 px
        assertEquals(Color.WHITE, px(img, 10, 10));
        assertEquals(Color.GRAY, px(img, 30, 10));
        assertEquals(Color.GRAY, px(img, 10, 60));
    }

    @Test
    void plainFillsEverything() {
        BufferedImage img = render(FloorStyle.plain(Color.ORANGE));
        assertEquals(Color.ORANGE, px(img, 0, 0));
        assertEquals(Color.ORANGE, px(img, W - 1, H - 1));
    }

    /** 2x2: rosso | verde / blu | bianco */
    private static BufferedImage quad() {
        BufferedImage q = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        q.setRGB(0, 0, Color.RED.getRGB());  q.setRGB(1, 0, Color.GREEN.getRGB());
        q.setRGB(0, 1, Color.BLUE.getRGB()); q.setRGB(1, 1, Color.WHITE.getRGB());
        return q;
    }

    @Test
    void imageStretchCoversArena() {
        BufferedImage img = render(new ImageFloor(quad(), ImageFloor.Mode.STRETCH));
        assertEquals(Color.RED, px(img, 10, 10));
        assertEquals(Color.GREEN, px(img, W - 10, 10));
        assertEquals(Color.BLUE, px(img, 10, H - 10));
        assertEquals(Color.WHITE, px(img, W - 10, H - 10));
    }

    @Test
    void imageFitKeepsAspectWithBands() {
        BufferedImage wide = new BufferedImage(200, 100, BufferedImage.TYPE_INT_RGB);   // 2:1 → bande sopra e sotto
        Graphics2D g = wide.createGraphics(); g.setColor(Color.RED); g.fillRect(0, 0, 200, 100); g.dispose();
        BufferedImage img = render(new ImageFloor(wide, ImageFloor.Mode.FIT).withBackground(Color.BLACK));
        assertEquals(Color.BLACK, px(img, W / 2, 10));        // banda superiore
        assertEquals(Color.RED, px(img, W / 2, H / 2));
        assertEquals(Color.BLACK, px(img, W / 2, H - 10));    // banda inferiore
    }

    @Test
    void imageTileRepeats() {
        BufferedImage img = render(new ImageFloor(quad(), ImageFloor.Mode.TILE));
        assertEquals(Color.RED, px(img, 0, 0));
        assertEquals(Color.RED, px(img, 2, 0));
        assertEquals(Color.WHITE, px(img, 3, 3));
    }

    @Test
    void opacityBlendsWithBackground() {
        BufferedImage black = new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB);   // tutta nera
        BufferedImage img = render(new ImageFloor(black, ImageFloor.Mode.STRETCH).withOpacity(0.25f));
        Color c = px(img, W / 2, H / 2);   // 25% nero su bianco ≈ 191
        assertEquals(191, c.getRed(), 2);
    }

    private static boolean sameImage(BufferedImage a, BufferedImage b) {
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) if (a.getRGB(x, y) != b.getRGB(x, y)) return false;
        return true;
    }

    @Test
    void proceduralFloorsAreReproducibleWithSeed() {
        assertTrue(sameImage(render(FloorStyle.randomTiles()), render(FloorStyle.randomTiles())));
        assertTrue(sameImage(render(FloorStyle.voronoiTiles()), render(FloorStyle.voronoiTiles())));
        Color c = new Color(180, 176, 168);
        assertFalse(sameImage(render(FloorStyle.randomTiles(c, 3, 1)), render(FloorStyle.randomTiles(c, 3, 2))));
        assertFalse(sameImage(render(FloorStyle.voronoiTiles(c, 3, 1)), render(FloorStyle.voronoiTiles(c, 3, 2))));
    }

    @Test
    void randomTilesHaveGroutOnTileBorders() {
        BufferedImage img = render(FloorStyle.randomTiles());   // piastrelle da 3 celle = 45 px
        Color grout = RandomTilesFloor.shade(new Color(180, 176, 168), -60);
        assertEquals(grout, px(img, 45, 20));
        assertEquals(grout, px(img, 20, 90));
        assertNotEquals(grout, px(img, 22, 22));
    }

    @Test
    void voronoiHasGroutAndManyDistinctSlabs() {
        BufferedImage img = render(FloorStyle.voronoiTiles());
        Color grout = RandomTilesFloor.shade(new Color(196, 186, 168), -70);
        int groutPx = 0;
        java.util.Set<Integer> colors = new java.util.HashSet<>();
        for (int y = 0; y < H; y += 3) for (int x = 0; x < W; x += 3) {
            int rgb = img.getRGB(x, y);
            if (rgb == grout.getRGB()) groutPx++; else colors.add(rgb);
        }
        double groutFraction = groutPx / (double) ((W / 3) * (H / 3));
        assertTrue(groutFraction > 0.03 && groutFraction < 0.25, "frazione fughe " + groutFraction);
        assertTrue(colors.size() > 20, "poche tonalità: " + colors.size());
    }

    @Test
    void loadFromFileAndFallbackOnError(@TempDir Path dir) throws Exception {
        File f = dir.resolve("floor.png").toFile();
        ImageIO.write(quad(), "png", f);
        FloorStyle ok = FloorStyle.image(f.getPath(), ImageFloor.Mode.STRETCH);
        assertInstanceOf(ImageFloor.class, ok);
        assertEquals(Color.RED, px(render(ok), 10, 10));

        FloorStyle missing = FloorStyle.image(dir.resolve("non-esiste.png").toString());
        assertInstanceOf(CheckerboardFloor.class, missing);
    }

}
