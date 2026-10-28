package com.expeditions.core;

/**
 * The expedition compass's proximity pulse — pure math, no Minecraft imports.
 *
 * <p>The compass needle only resolves the horizontal plane; a checkpoint in a
 * Nether cave below your feet reads as "arrived" while still far below. The
 * pulse fixes that: when the holder is within {@code range} of the target on
 * EITHER axis — close horizontally OR vertically — the compass beats like a
 * heart. The rate scales with true 3D distance from racing ({@link
 * #MIN_INTERVAL_TICKS}) to faint ({@link #MAX_INTERVAL_TICKS}), bounded by a
 * far horizon of {@code range * FAR_RANGE_FACTOR} so aligned-but-distant
 * holders hear only the slowest pulse.
 */
public final class CompassPulse {

    /** Fastest pulse — the heart races when the target is nearly reached. */
    public static final int MIN_INTERVAL_TICKS = 8;
    /** Slowest pulse — a faint thump at the far edge of the sense. */
    public static final int MAX_INTERVAL_TICKS = 40;
    /** Beyond {@code range * this} the pulse cannot be felt at all. */
    public static final double FAR_RANGE_FACTOR = 3.0;
    /** Ticks between the two beats of one lub-dub pair. */
    public static final int DUB_DELAY_TICKS = 4;

    private CompassPulse() {
    }

    /**
     * Ticks between pulse pairs at the given axis gaps, or {@code -1} when the
     * target is out of the sense entirely. {@code horizontal} is the XZ-plane
     * gap, {@code vertical} the Y gap — both must be non-negative.
     */
    public static int intervalTicks(double horizontal, double vertical, double range) {
        if (horizontal > range && vertical > range) {
            return -1; // outside the sense on both axes
        }
        double distance = Math.hypot(horizontal, vertical);
        double far = range * FAR_RANGE_FACTOR;
        if (distance > far) {
            return -1; // aligned but too far to feel
        }
        double t = Math.min(1.0, distance / far);
        return (int) Math.round(MIN_INTERVAL_TICKS
                + t * (MAX_INTERVAL_TICKS - MIN_INTERVAL_TICKS));
    }

    /** Whether the pulse is felt at all at these axis gaps. */
    public static boolean pulses(double horizontal, double vertical, double range) {
        return intervalTicks(horizontal, vertical, range) > 0;
    }

    /**
     * Playback pitch for a beat at this distance — the heart runs slightly
     * higher-pitched as it races, easing toward neutral when faint.
     */
    public static float pitch(double horizontal, double vertical, double range) {
        double distance = Math.hypot(horizontal, vertical);
        double t = Math.min(1.0, distance / (range * FAR_RANGE_FACTOR));
        return (float) (1.2 - t * 0.2);
    }
}
