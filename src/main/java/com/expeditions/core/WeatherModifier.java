package com.expeditions.core;

/**
 * Weather a run forces onto its dimension while active. Set when the run
 * starts, cleared when it ends. Dimensions without weather (the Nether, the
 * End) simply ignore the applied state — the modifier is a no-op there.
 */
public enum WeatherModifier {
    NONE,
    CLEAR,
    RAIN,
    THUNDER;

    /** Parses a weather name case-insensitively; {@code null} when unknown. */
    public static WeatherModifier byName(String name) {
        for (WeatherModifier modifier : values()) {
            if (modifier.name().equalsIgnoreCase(name)) {
                return modifier;
            }
        }
        return null;
    }
}
