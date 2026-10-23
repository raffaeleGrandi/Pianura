package io.github.raffaelegrandi.pianura.comm;

/**
 * Radio channel of a scenario. Defaults: range 6 cells, no line of sight, no losses,
 * delivery at the next physics step.
 *
 * <pre>
 *   getCommSettings().setRange(8).setLineOfSight(true).setLossProbability(0.05);
 * </pre>
 */
public final class CommSettings {

    private double rangeCells = 6;
    private boolean lineOfSight = false;
    private double lossProbability = 0;
    private double latency = 0;
    private long seed = 1;

    /** Default transmission range in cells (a robot can override it with {@code setCommRange}). */
    public double getRange() { return rangeCells; }

    public CommSettings setRange(double cells) {
        if (!(cells >= 0)) throw new IllegalArgumentException("range must be >= 0");
        rangeCells = cells;
        return this;
    }

    /** If true, obstacles between two robots break their link. */
    public boolean isLineOfSight() { return lineOfSight; }

    public CommSettings setLineOfSight(boolean on) { lineOfSight = on; return this; }

    /** Probability (0–1) that a message to one receiver is lost. */
    public double getLossProbability() { return lossProbability; }

    public CommSettings setLossProbability(double p) {
        if (!(p >= 0 && p <= 1)) throw new IllegalArgumentException("loss probability must be in [0, 1]");
        lossProbability = p;
        return this;
    }

    /** Delivery delay in simulated seconds (at least one physics step). */
    public double getLatency() { return latency; }

    public CommSettings setLatency(double seconds) {
        if (!(seconds >= 0)) throw new IllegalArgumentException("latency must be >= 0");
        latency = seconds;
        return this;
    }

    /** Seed of the random generator used for losses: same seed, same losses. */
    public long getSeed() { return seed; }

    public CommSettings setSeed(long s) { seed = s; return this; }

}
