package com.expeditions.core;

import java.util.Objects;

/**
 * Immutable description of an expedition: which route/objectives apply and how
 * hard it is. Kept free of Minecraft types so it can later be sourced from
 * configs or datapacks and tested in isolation.
 *
 * @param id          stable identifier, e.g. {@code "expeditions:overworld_trial"}
 * @param displayName human-readable name shown to players
 * @param difficulty  non-negative difficulty rating used by future reward scaling
 */
public record ExpeditionDefinition(String id, String displayName, int difficulty) {
    public ExpeditionDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(displayName, "displayName");
        if (id.isBlank()) {
            throw new IllegalArgumentException("Expedition id must not be blank");
        }
        if (difficulty < 0) {
            throw new IllegalArgumentException("Difficulty must be >= 0");
        }
    }
}
