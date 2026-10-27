package com.expeditions.core;

import java.util.Set;

/**
 * What an expedition still demands of a player before it may start:
 * unlock keys they don't hold and the token shortfall against the
 * definition's start cost. Pure domain — message building stays in the
 * server layer.
 *
 * @param missingKeys   required unlock keys the player lacks
 * @param missingTokens how many more tokens the start cost needs
 */
public record RequirementCheck(Set<String> missingKeys, int missingTokens) {

    public RequirementCheck {
        missingKeys = Set.copyOf(missingKeys);
        if (missingTokens < 0) {
            throw new IllegalArgumentException("missingTokens must be >= 0");
        }
    }

    /** Whether nothing blocks the start. */
    public boolean isSatisfied() {
        return missingKeys.isEmpty() && missingTokens == 0;
    }
}
