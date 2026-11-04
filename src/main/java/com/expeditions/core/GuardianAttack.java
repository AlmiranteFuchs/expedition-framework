package com.expeditions.core;

/** The warning circle is the damage footprint; height and range are bounded. */
public enum GuardianAttack {
    SWEEP(5.0, 40, 64), ROOT_BURST(2.75, 36, 48);

    private final double radius;
    private final int warningTicks;
    private final int recoveryTicks;
    GuardianAttack(double radius, int warningTicks, int recoveryTicks) {
        this.radius = radius; this.warningTicks = warningTicks; this.recoveryTicks = recoveryTicks;
    }
    public double radius() { return radius; }
    public int warningTicks() { return warningTicks; }
    public int recoveryTicks() { return recoveryTicks; }
    public boolean hits(double dx, double dy, double dz) {
        return Math.abs(dy) <= 2.5 && dx * dx + dz * dz <= radius * radius;
    }
    public static GuardianAttack next(boolean enraged, int sequence) {
        return enraged && (sequence & 1) == 1 ? ROOT_BURST : SWEEP;
    }
}
