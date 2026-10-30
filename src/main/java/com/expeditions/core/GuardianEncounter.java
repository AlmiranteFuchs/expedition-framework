package com.expeditions.core;

import java.util.Objects;
import java.util.UUID;

/** The exact guardian whose confirmed death permits this run's extraction. */
public record GuardianEncounter(UUID entityId, boolean defeated) {
    public GuardianEncounter { Objects.requireNonNull(entityId, "guardian entity id"); }
}
