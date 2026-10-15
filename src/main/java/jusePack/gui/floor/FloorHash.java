package jusePack.gui.floor;

/**
 * Pseudo-random values that depend only on a seed and a position, never on the order in which
 * they are asked: procedural floors can then be painted one region at a time without seams.
 */
final class FloorHash {

    private FloorHash() {}

    /** 64-bit hash of (seed, a, b, salt) (SplitMix64 finaliser). */
    static long mix(long seed, long a, long b, long salt) {
        long z = seed * 0x9E3779B97F4A7C15L ^ a * 0xBF58476D1CE4E5B9L ^ b * 0x94D049BB133111EBL ^ salt * 0xD6E8FEB86659FD93L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /** Uniform value in [0, 1). */
    static double unit(long seed, long a, long b, long salt) {
        return (mix(seed, a, b, salt) >>> 11) * 0x1.0p-53;
    }

    /** Uniform integer in [-range, +range]. */
    static int symmetric(long seed, long a, long b, long salt, int range) {
        if (range <= 0) return 0;
        return (int) Math.floorMod(mix(seed, a, b, salt), (long) (2 * range + 1)) - range;
    }

    /** Colour {@code rgb} lightened/darkened by {@code d} levels on every channel. */
    static int shade(int rgb, int d) {
        int r = clamp(((rgb >> 16) & 0xff) + d), g = clamp(((rgb >> 8) & 0xff) + d), b = clamp((rgb & 0xff) + d);
        return 0xff000000 | r << 16 | g << 8 | b;
    }

    private static int clamp(int v) { return v < 0 ? 0 : Math.min(255, v); }

}
