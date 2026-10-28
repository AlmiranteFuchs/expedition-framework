package com.expeditions.core;

/**
 * The microbiome's heartbeat waveform — pure math, no Minecraft imports.
 *
 * <p>A colony beats like a heart: a sharp systole thump (lub), a softer
 * second beat (dub), then a long resting thrum. The wave is spatial — it
 * originates at the heart and travels outward through the colony, so each
 * cell's pulse is the same waveform delayed by its distance from the
 * source. Each colony keeps its own tempo derived deterministically from
 * the heart's position, so neighboring colonies never beat in lockstep.
 */
public final class PulseWave {

    /** Slowest colony tempo — a resting heart, ticks per beat. */
    public static final int MIN_PERIOD_TICKS = 44;
    /** Fastest colony tempo, ticks per beat. */
    public static final int MAX_PERIOD_TICKS = 60;
    /** Ticks the wave takes to cross one block of colony. */
    public static final double TICKS_PER_BLOCK = 2.0;
    /** Resting glow between beats — the membrane never goes fully dark. */
    public static final double THRUM = 0.07;

    /** Systole: sharp and strong, early in the cycle. */
    private static final double LUB_CENTER = 0.12;
    private static final double LUB_WIDTH = 0.045;
    /** Diastole's echo: softer, wider, about a fifth-cycle later. */
    private static final double DUB_CENTER = 0.30;
    private static final double DUB_WIDTH = 0.06;
    private static final double DUB_WEIGHT = 0.55;

    private PulseWave() {
    }

    /** Beat amplitude at cycle {@code phase} (any real value; wraps to 0..1). */
    public static double amplitude(double phase) {
        double p = phase - Math.floor(phase);
        double lub = gauss(p - LUB_CENTER, LUB_WIDTH);
        double dub = DUB_WEIGHT * gauss(p - DUB_CENTER, DUB_WIDTH);
        return Math.min(1.0, THRUM + lub + dub);
    }

    /**
     * Amplitude for a cell {@code distance} blocks from its heart at
     * {@code gameTime} (partial ticks allowed). {@code seed} — the heart's
     * position hash — picks this colony's tempo and phase offset, so two
     * colonies side by side beat on their own clocks.
     */
    public static double amplitudeAt(double gameTime, double distance, long seed) {
        double period = periodTicks(seed);
        double phase = (gameTime - distance * TICKS_PER_BLOCK) / period
                + phaseOffset(seed);
        return amplitude(phase);
    }

    /** This colony's beat period in ticks, derived deterministically. */
    public static int periodTicks(long seed) {
        return MIN_PERIOD_TICKS + (int) Math.floorMod(
                mix(seed), MAX_PERIOD_TICKS - MIN_PERIOD_TICKS + 1);
    }

    /** This colony's phase offset within its cycle, 0..1. */
    public static double phaseOffset(long seed) {
        return (mix(seed ^ 0x9E3779B97F4A7C15L) & 0xFFFFFF) / 16777216.0;
    }

    private static double gauss(double x, double sigma) {
        double t = x / sigma;
        return Math.exp(-t * t);
    }

    private static long mix(long seed) {
        long z = seed + 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
