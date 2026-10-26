package com.expeditions.core;

import java.util.Objects;

/**
 * A mob-effect declaration on an expedition: the registry id of the effect
 * (vanilla or modded — validated against the registry at the parse layer)
 * and its amplifier. Duration is owned by the application site: player
 * effects last while the run is active, horde effects are permanent on the
 * spawned mob.
 */
public record EffectSpec(String effectId, int amplifier) {

    public EffectSpec {
        Objects.requireNonNull(effectId, "effectId");
        if (effectId.isBlank()) {
            throw new IllegalArgumentException("Effect id must not be blank");
        }
        if (amplifier < 0 || amplifier > 255) {
            throw new IllegalArgumentException("Effect amplifier must be 0-255");
        }
    }
}
