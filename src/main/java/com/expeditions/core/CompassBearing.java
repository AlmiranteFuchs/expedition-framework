package com.expeditions.core;

/** Relative horizontal bearing, using Minecraft's south-zero yaw convention. */
public final class CompassBearing {
    private CompassBearing() { }

    public static double turn(double east, double south, double yaw) {
        if (east == 0 && south == 0) return 0;
        return Math.IEEEremainder(Math.toDegrees(Math.atan2(-east, south)) - yaw, 360);
    }
}
