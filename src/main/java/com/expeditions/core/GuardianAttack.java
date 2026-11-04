package com.expeditions.core;

/** The warning circle is the damage footprint; height and range are bounded. */
public enum GuardianAttack {
    SWEEP(0, 5.0, 40, 64, 4.75),
    ROOT_BURST(0, 2.75, 36, 48, 12),
    ROOT_RING(2.5, 7.0, 44, 56, 7);

    private final double innerRadius;
    private final double radius;
    private final int warningTicks;
    private final int recoveryTicks;
    private final double triggerRange;
    GuardianAttack(double innerRadius, double radius, int warningTicks, int recoveryTicks, double triggerRange) {
        this.innerRadius = innerRadius; this.radius = radius; this.warningTicks = warningTicks;
        this.recoveryTicks = recoveryTicks; this.triggerRange = triggerRange;
    }
    public double innerRadius() { return innerRadius; }
    public double radius() { return radius; }
    public double triggerRange() { return triggerRange; }
    public int warningTicks() { return warningTicks; }
    public int recoveryTicks() { return recoveryTicks; }
    public boolean hits(double dx, double dy, double dz) {
        double distance = dx * dx + dz * dz;
        return Math.abs(dy) <= 2.5 && distance >= innerRadius * innerRadius && distance <= radius * radius;
    }
    public static GuardianAttack next(boolean enraged, int sequence) {
        return switch (Math.floorMod(sequence, enraged ? 3 : 2)) {
            case 1 -> ROOT_BURST;
            case 2 -> ROOT_RING;
            default -> SWEEP;
        };
    }
}
