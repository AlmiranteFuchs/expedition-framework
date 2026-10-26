package com.expeditions.core;

import java.util.List;
import java.util.Objects;

/**
 * Environmental and combat modifiers an expedition declares: weather forced
 * on the run's dimension, effects applied to party members while the run is
 * active, and buffs baked into horde mobs at spawn. All three are optional;
 * {@link #NONE} means an unmodified expedition.
 */
public record ExpeditionModifiers(WeatherModifier weather,
        List<EffectSpec> playerEffects, List<EffectSpec> hordeEffects) {

    /** The unmodified expedition. */
    public static final ExpeditionModifiers NONE = new ExpeditionModifiers(
            WeatherModifier.NONE, List.of(), List.of());

    public ExpeditionModifiers {
        Objects.requireNonNull(weather, "weather");
        playerEffects = List.copyOf(playerEffects);
        hordeEffects = List.copyOf(hordeEffects);
    }

    /** Whether any modifier is set (weather alone counts). */
    public boolean isEmpty() {
        return this.equals(NONE);
    }
}
