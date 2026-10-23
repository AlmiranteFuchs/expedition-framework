package com.expeditions.core;

/**
 * Kinds of challenges a checkpoint activation can trigger. The set is
 * deliberately small — new kinds (boss encounters, ambush variants) extend
 * this enum and the pick in {@link #roll} without touching run lifecycle or
 * persistence code.
 */
public enum CheckpointChallenge {
    /** Nothing happens: the party keeps moving. */
    NONE,
    /** A group of hostile mobs spawns near the checkpoint and hunts the party. */
    HORDE;

    /**
     * Picks the challenge for one checkpoint activation. {@code uniform} is a
     * random value in {@code [0, 1)}; the horde triggers when it lands under
     * {@code hordeChance}. With a single kind today the chance is the whole
     * probability mass — future kinds split it here, keeping the roll pure
     * and deterministically testable.
     */
    public static CheckpointChallenge roll(double hordeChance, double uniform) {
        return uniform < hordeChance ? HORDE : NONE;
    }
}
