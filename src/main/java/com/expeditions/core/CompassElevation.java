package com.expeditions.core;

/** Signed vertical resonance for the compass; no world or rendering dependencies. */
public final class CompassElevation {
    public static final double LEVEL_TOLERANCE = 2.0;
    private static final double FULL_PULL_BLOCKS = 32.0;

    private CompassElevation() { }

    /** -1 below, 0 within a walkable height difference, +1 above. */
    public static int direction(double verticalGap) {
        if (Math.abs(verticalGap) <= LEVEL_TOLERANCE) {
            return 0;
        }
        return verticalGap > 0 ? 1 : -1;
    }

    /** Bounded signed displacement; even a small non-level gap remains visible. */
    public static double pull(double verticalGap) {
        int direction = direction(verticalGap);
        return direction == 0 ? 0
                : direction * Math.min(1.0, Math.max(0.35, Math.abs(verticalGap) / FULL_PULL_BLOCKS));
    }
}
