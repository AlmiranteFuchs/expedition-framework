package com.expeditions.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Validation of {@link EffectSpec} bounds, {@link ExpeditionModifiers}
 * immutability and {@link WeatherModifier} name lookup.
 */
class ExpeditionModifiersTest {
    @Test
    void effectSpecRejectsBadValues() {
        assertThrows(IllegalArgumentException.class, () -> new EffectSpec("", 0));
        assertThrows(IllegalArgumentException.class, () -> new EffectSpec("  ", 0));
        assertThrows(NullPointerException.class, () -> new EffectSpec(null, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new EffectSpec("minecraft:speed", -1));
        assertThrows(IllegalArgumentException.class,
                () -> new EffectSpec("minecraft:speed", 256));
    }

    @Test
    void effectSpecAcceptsBoundaryAmplifiers() {
        assertEquals(0, new EffectSpec("minecraft:speed", 0).amplifier());
        assertEquals(255, new EffectSpec("minecraft:speed", 255).amplifier());
    }

    @Test
    void modifiersCopyEffectLists() {
        List<EffectSpec> effects = new ArrayList<>(
                List.of(new EffectSpec("minecraft:speed", 0)));
        ExpeditionModifiers modifiers = new ExpeditionModifiers(
                WeatherModifier.NONE, effects, List.of());
        effects.add(new EffectSpec("minecraft:strength", 1));
        assertEquals(1, modifiers.playerEffects().size());
        assertThrows(UnsupportedOperationException.class,
                () -> modifiers.playerEffects().add(new EffectSpec("x:y", 0)));
    }

    @Test
    void noneIsEmptyAndUnmodified() {
        assertTrue(ExpeditionModifiers.NONE.isEmpty());
        assertEquals(WeatherModifier.NONE, ExpeditionModifiers.NONE.weather());
        assertTrue(ExpeditionModifiers.NONE.playerEffects().isEmpty());
        assertTrue(ExpeditionModifiers.NONE.hordeEffects().isEmpty());
    }

    @Test
    void weatherLookupIsCaseInsensitive() {
        assertEquals(WeatherModifier.THUNDER, WeatherModifier.byName("thunder"));
        assertEquals(WeatherModifier.THUNDER, WeatherModifier.byName("THUNDER"));
        assertEquals(WeatherModifier.CLEAR, WeatherModifier.byName("Clear"));
        assertNull(WeatherModifier.byName("apocalypse"));
    }
}
