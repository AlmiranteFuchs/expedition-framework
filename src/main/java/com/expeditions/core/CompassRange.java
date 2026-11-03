package com.expeditions.core;

/** Coarse signal bands leave precise paid lens measurements useful. */
public record CompassRange(int minimum, int maximum) {
    public static CompassRange estimate(double distance) {
        if (!Double.isFinite(distance) || distance < 0 || distance > (1 << 30))
            throw new IllegalArgumentException("Invalid compass distance");
        int upper = 32;
        while (distance > upper) upper *= 2;
        return new CompassRange(upper == 32 ? 0 : upper / 2, upper);
    }
}
