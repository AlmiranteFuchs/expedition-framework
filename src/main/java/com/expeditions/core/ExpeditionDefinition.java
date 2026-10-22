package com.expeditions.core;

import java.util.Objects;

/**
 * Immutable description of an expedition: its route, difficulty and guaranteed
 * token reward. Kept free of Minecraft types so it can be sourced from config
 * (and later datapacks) and tested in isolation.
 *
 * @param id          stable identifier, e.g. {@code "expeditions:overworld_trial"}
 * @param displayName human-readable name shown to players
 * @param difficulty  non-negative difficulty rating used by future reward scaling
 * @param route       ordered checkpoints plus the exit
 * @param tokenReward tokens granted exactly once on successful completion
 */
public record ExpeditionDefinition(String id, String displayName, int difficulty,
        ExpeditionRoute route, int tokenReward) {
    public ExpeditionDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(route, "route");
        if (id.isBlank()) {
            throw new IllegalArgumentException("Expedition id must not be blank");
        }
        if (difficulty < 0) {
            throw new IllegalArgumentException("Difficulty must be >= 0");
        }
        if (tokenReward < 0) {
            throw new IllegalArgumentException("Token reward must be >= 0");
        }
    }
}
