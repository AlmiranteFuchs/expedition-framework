package com.expeditions.core;

/**
 * Per-run difficulty tier chosen at the terminal. Each tier carries two
 * percentage multipliers: one scaling checkpoint-challenge size and one
 * scaling the final token reward — harder runs are nastier and pay more,
 * relaxed runs are gentler and pay less.
 */
public enum Difficulty {
    RELAXED(50, 50),
    STANDARD(100, 100),
    HARD(150, 150),
    BRUTAL(200, 200);

    private final int hordePercent;
    private final int rewardPercent;

    Difficulty(int hordePercent, int rewardPercent) {
        this.hordePercent = hordePercent;
        this.rewardPercent = rewardPercent;
    }

    /** Percentage applied to the configured horde size (50-200). */
    public int hordePercent() {
        return hordePercent;
    }

    /** Percentage applied to the scaled token reward pool (50-200). */
    public int rewardPercent() {
        return rewardPercent;
    }

    /** Parses a tier name case-insensitively; {@code null} when unknown. */
    public static Difficulty byName(String name) {
        for (Difficulty tier : values()) {
            if (tier.name().equalsIgnoreCase(name)) {
                return tier;
            }
        }
        return null;
    }
}
