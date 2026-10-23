package io.github.raffaelegrandi.pianura.units.collisions;

import java.awt.geom.Rectangle2D;
import java.util.Arrays;
import java.util.List;

/**
 * Uniform grid over the static shapes: answers "which shapes may touch this rectangle?"
 * without looking at all of them. Immutable and thread-safe once built.
 *
 * Each shape is registered in every bucket its bounding box overlaps; a query returns the
 * indices of the candidate shapes (sorted, without duplicates), which the caller then tests
 * exactly. With small sensor ranges a query touches a handful of buckets, so the cost of a
 * sensor reading no longer grows with the number of obstacles in the arena.
 */
final class ShapeGrid {

    private static final int[] NONE = new int[0];

    private final double bucket;
    private final double minX, minY;
    private final int cols, rows;
    private final int[][] buckets;            // bucket -> shape indices
    private final Rectangle2D[] bounds;

    /**
     * @param shapes   shapes to index (null entries are skipped)
     * @param bucketPx side of a grid bucket in pixels
     */
    ShapeGrid(List<? extends CollidableShape> shapes, double bucketPx) {
        this.bucket = bucketPx;
        int n = shapes.size();
        bounds = new Rectangle2D[n];
        double x0 = Double.POSITIVE_INFINITY, y0 = Double.POSITIVE_INFINITY;
        double x1 = Double.NEGATIVE_INFINITY, y1 = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < n; i++) {
            CollidableShape s = shapes.get(i);
            if (s == null) continue;
            Rectangle2D b = s.outline().getBounds2D();
            bounds[i] = b;
            x0 = Math.min(x0, b.getMinX()); y0 = Math.min(y0, b.getMinY());
            x1 = Math.max(x1, b.getMaxX()); y1 = Math.max(y1, b.getMaxY());
        }
        if (x0 > x1) { x0 = y0 = 0; x1 = y1 = 1; }   // no shapes
        minX = x0;
        minY = y0;
        cols = Math.max(1, (int) Math.ceil((x1 - x0) / bucket) + 1);
        rows = Math.max(1, (int) Math.ceil((y1 - y0) / bucket) + 1);

        int[] count = new int[cols * rows];
        for (Rectangle2D b : bounds) if (b != null) forEachBucket(b.getMinX(), b.getMinY(), b.getMaxX(), b.getMaxY(), k -> count[k]++);
        buckets = new int[cols * rows][];
        for (int k = 0; k < count.length; k++) buckets[k] = count[k] == 0 ? NONE : new int[count[k]];
        int[] fill = new int[cols * rows];
        for (int i = 0; i < n; i++) {
            Rectangle2D b = bounds[i];
            if (b == null) continue;
            final int idx = i;
            forEachBucket(b.getMinX(), b.getMinY(), b.getMaxX(), b.getMaxY(), k -> buckets[k][fill[k]++] = idx);
        }
    }

    private interface BucketAction { void at(int k); }

    private void forEachBucket(double ax, double ay, double bx, double by, BucketAction a) {
        int c0 = clampCol((int) Math.floor((ax - minX) / bucket)), c1 = clampCol((int) Math.floor((bx - minX) / bucket));
        int r0 = clampRow((int) Math.floor((ay - minY) / bucket)), r1 = clampRow((int) Math.floor((by - minY) / bucket));
        for (int r = r0; r <= r1; r++) for (int c = c0; c <= c1; c++) a.at(r * cols + c);
    }

    private int clampCol(int c) { return Math.max(0, Math.min(cols - 1, c)); }
    private int clampRow(int r) { return Math.max(0, Math.min(rows - 1, r)); }

    /**
     * Indices (ascending, no duplicates) of the shapes whose bounding box intersects the
     * rectangle [ax, bx] x [ay, by].
     */
    int[] query(double ax, double ay, double bx, double by) {
        if (bx < minX || by < minY || ax > minX + cols * bucket || ay > minY + rows * bucket) return NONE;
        int c0 = clampCol((int) Math.floor((ax - minX) / bucket)), c1 = clampCol((int) Math.floor((bx - minX) / bucket));
        int r0 = clampRow((int) Math.floor((ay - minY) / bucket)), r1 = clampRow((int) Math.floor((by - minY) / bucket));
        int total = 0;
        for (int r = r0; r <= r1; r++) for (int c = c0; c <= c1; c++) total += buckets[r * cols + c].length;
        if (total == 0) return NONE;
        int[] out = new int[total];
        int m = 0;
        for (int r = r0; r <= r1; r++) {
            for (int c = c0; c <= c1; c++) {
                for (int i : buckets[r * cols + c]) {
                    Rectangle2D b = bounds[i];
                    if (b.getMaxX() >= ax && b.getMinX() <= bx && b.getMaxY() >= ay && b.getMinY() <= by) out[m++] = i;
                }
            }
        }
        if (m == 0) return NONE;
        Arrays.sort(out, 0, m);
        int u = 1;
        for (int k = 1; k < m; k++) if (out[k] != out[u - 1]) out[u++] = out[k];
        return Arrays.copyOf(out, u);
    }

    /** Bounding box of shape {@code i} (null for a skipped entry). */
    Rectangle2D bounds(int i) { return bounds[i]; }

}
