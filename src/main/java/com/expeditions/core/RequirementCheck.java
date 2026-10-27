package com.expeditions.core;

import java.util.Objects;
import java.util.Set;

/**
 * What an expedition still demands before it may start: unlock keys the
 * player doesn't hold, the token shortfall against the start cost, and
 * the campaign-state gap of the expedition's dimension. Pure domain —
 * message building stays in the server layer.
 *
 * @param missingKeys     required unlock keys the player lacks
 * @param missingTokens   how many more tokens the start cost needs
 * @param requiredState   minimum campaign state the definition demands
 * @param currentState    the dimension's campaign state right now
 */
public record RequirementCheck(Set<String> missingKeys, int missingTokens,
        CampaignState requiredState, CampaignState currentState) {

    /** A check without a campaign gate (older saves, key/token-only rules). */
    public RequirementCheck(Set<String> missingKeys, int missingTokens) {
        this(missingKeys, missingTokens, CampaignState.FRONTIER,
                CampaignState.FRONTIER);
    }

    public RequirementCheck {
        missingKeys = Set.copyOf(missingKeys);
        Objects.requireNonNull(requiredState, "requiredState");
        Objects.requireNonNull(currentState, "currentState");
        if (missingTokens < 0) {
            throw new IllegalArgumentException("missingTokens must be >= 0");
        }
    }

    /** Whether nothing blocks the start. */
    public boolean isSatisfied() {
        return missingKeys.isEmpty() && missingTokens == 0
                && currentState.atLeast(requiredState);
    }
}
