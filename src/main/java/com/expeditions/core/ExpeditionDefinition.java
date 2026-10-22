package com.expeditions.core;

import java.util.Objects;

/**
 * Immutable description of an expedition: which dimension it lives in, its
 * route, difficulty and rewards. Kept free of Minecraft types so it can be
 * sourced from config (and later datapacks) and tested in isolation.
 *
 * @param id             stable identifier, e.g. {@code "expeditions:overworld_trial"}
 * @param displayName    human-readable name shown to players
 * @param difficulty     non-negative difficulty rating used by future reward scaling
 * @param dimension      dimension id the route resolves in, e.g.
 *                       {@code "minecraft:overworld"}
 * @param route          ordered checkpoints plus the exit
 * @param tokenReward    tokens granted exactly once on successful completion
 * @param bonusTokens    extra tokens for finishing within {@code bonusTimeTicks};
 *                       0 disables the time bonus
 * @param bonusTimeTicks elapsed-tick budget for the bonus; 0 disables it
 */
public record ExpeditionDefinition(String id, String displayName, int difficulty,
        String dimension, ExpeditionRoute route, int tokenReward,
        int bonusTokens, int bonusTimeTicks) {

    /** Definition without a time bonus, in the Overworld. */
    public ExpeditionDefinition(String id, String displayName, int difficulty,
            ExpeditionRoute route, int tokenReward) {
        this(id, displayName, difficulty, "minecraft:overworld", route, tokenReward, 0, 0);
    }

    public ExpeditionDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(route, "route");
        if (id.isBlank()) {
            throw new IllegalArgumentException("Expedition id must not be blank");
        }
        if (dimension.isBlank()) {
            throw new IllegalArgumentException("Dimension must not be blank");
        }
        if (difficulty < 0) {
            throw new IllegalArgumentException("Difficulty must be >= 0");
        }
        if (tokenReward < 0 || bonusTokens < 0) {
            throw new IllegalArgumentException("Rewards must be >= 0");
        }
        if (bonusTimeTicks < 0) {
            throw new IllegalArgumentException("Bonus time must be >= 0");
        }
    }

    /**
     * Total tokens a run earns when completed in {@code elapsedTicks} ticks:
     * the guaranteed reward plus the time bonus when configured and earned.
     * A negative {@code elapsedTicks} (unknown start time) never earns a bonus.
     */
    public int totalReward(long elapsedTicks) {
        boolean bonusEarned = bonusTokens > 0 && bonusTimeTicks > 0
                && elapsedTicks >= 0 && elapsedTicks <= bonusTimeTicks;
        return tokenReward + (bonusEarned ? bonusTokens : 0);
    }
}
