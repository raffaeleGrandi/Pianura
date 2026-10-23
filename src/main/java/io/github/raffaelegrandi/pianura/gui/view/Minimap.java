package io.github.raffaelegrandi.pianura.gui.view;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.List;

import io.github.raffaelegrandi.pianura.units.sensors.FloorMap;

/**
 * Miniature of the whole arena in a corner of the view, as in strategy games: floor and obstacles,
 * a bright dot for each robot and the rectangle of the part currently visible.
 *
 * Floor and obstacles are painted once into a cached image (they change only when the scene is
 * edited or the floor is changed); robots and the visible rectangle are drawn on top every frame.
 */
final class Minimap {

    /** Longest side of the minimap, in screen pixels. */
    static final int MAX_SIDE = 200;
    static final int MARGIN = 12;

    private static final Color FRAME = new Color(20, 22, 26, 210);
    private static final Color VIEWPORT = new Color(255, 255, 255, 235);
    private static final Color VIEWPORT_SHADOW = new Color(0, 0, 0, 140);
    private static final Color ROBOT = new Color(255, 236, 90);
    private static final Color ROBOT_EDGE = new Color(0, 0, 0, 170);
    private static final Color SELECTED = new Color(255, 120, 40);

    private BufferedImage background;
    private Object bgFloor, bgOutlines;
    private int bgW, bgH;

    /** Screen rectangle of the minimap in the bottom-right corner (arena proportions kept). */
    static Rectangle bounds(int viewW, int viewH, double arenaW, double arenaH) {
        double s = Math.min(MAX_SIDE / arenaW, MAX_SIDE / arenaH);
        int w = Math.max(20, (int) Math.round(arenaW * s)), h = Math.max(20, (int) Math.round(arenaH * s));
        return new Rectangle(viewW - MARGIN - w, viewH - MARGIN - h, w, h);
    }

    /** World point (pixels) under the screen point (x, y) of the minimap, clamped to the arena. */
    static Point2D toWorld(Rectangle mm, double x, double y, double arenaW, double arenaH) {
        double fx = Math.max(0, Math.min(1, (x - mm.x) / mm.width));
        double fy = Math.max(0, Math.min(1, (y - mm.y) / mm.height));
        return new Point2D.Double(fx * arenaW, fy * arenaH);
    }

    /**
     * @param visible   world rectangle visible in the main view
     * @param robots    robot positions in world pixels
     * @param highlight index of the robot to highlight (selected or followed), or -1
     */
    void paint(Graphics2D g0, Rectangle mm, double arenaW, double arenaH, FloorMap floor, List<Shape> outlines,
               List<Point2D> robots, int highlight, Rectangle2D visible) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        // frame
        g.setColor(FRAME);
        g.fillRoundRect(mm.x - 4, mm.y - 4, mm.width + 8, mm.height + 8, 8, 8);
        // floor + obstacles (cached)
        if (background == null || bgFloor != floor || bgOutlines != outlines || bgW != mm.width || bgH != mm.height) {
            background = renderBackground(mm.width, mm.height, arenaW, arenaH, floor, outlines);
            bgFloor = floor;
            bgOutlines = outlines;
            bgW = mm.width;
            bgH = mm.height;
        }
        g.drawImage(background, mm.x, mm.y, null);

        double sx = mm.width / arenaW, sy = mm.height / arenaH;
        Shape oldClip = g.getClip();
        g.clipRect(mm.x, mm.y, mm.width, mm.height);
        // robots: bright dots
        double r = 2.2;
        for (int i = 0; i < robots.size(); i++) {
            Point2D p = robots.get(i);
            double x = mm.x + p.getX() * sx, y = mm.y + p.getY() * sy;
            boolean hl = i == highlight;
            double rr = hl ? r + 1.4 : r;
            Ellipse2D dot = new Ellipse2D.Double(x - rr, y - rr, 2 * rr, 2 * rr);
            g.setColor(hl ? SELECTED : ROBOT);
            g.fill(dot);
            g.setStroke(new BasicStroke(1f));
            g.setColor(ROBOT_EDGE);
            g.draw(dot);
        }
        // visible part of the arena
        Rectangle2D v = visible.createIntersection(new Rectangle2D.Double(0, 0, arenaW, arenaH));
        if (!v.isEmpty()) {
            Rectangle2D box = new Rectangle2D.Double(mm.x + v.getX() * sx, mm.y + v.getY() * sy,
                Math.max(3, v.getWidth() * sx), Math.max(3, v.getHeight() * sy));
            g.setStroke(new BasicStroke(3f));
            g.setColor(VIEWPORT_SHADOW);
            g.draw(box);
            g.setStroke(new BasicStroke(1.4f));
            g.setColor(VIEWPORT);
            g.draw(box);
        }
        g.setClip(oldClip);
        g.dispose();
    }

    private static BufferedImage renderBackground(int w, int h, double arenaW, double arenaH, FloorMap floor, List<Shape> outlines) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        if (floor != null) {
            // average of the pixels (not a sample): the fine pattern of the floor becomes its mean colour
            java.awt.Image small = floor.overview().getScaledInstance(w, h, java.awt.Image.SCALE_AREA_AVERAGING);
            g.drawImage(small, 0, 0, null);
        } else { g.setColor(Color.LIGHT_GRAY); g.fillRect(0, 0, w, h); }
        // floor washed out, so that obstacles and robots stand out
        g.setColor(new Color(255, 255, 255, 110));
        g.fillRect(0, 0, w, h);
        g.transform(AffineTransform.getScaleInstance(w / arenaW, h / arenaH));
        g.setColor(new Color(40, 42, 50));
        for (Shape s : outlines) if (s != null) g.fill(s);
        g.dispose();
        return img;
    }

}
