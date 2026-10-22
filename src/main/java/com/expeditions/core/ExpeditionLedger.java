package com.expeditions.core;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Per-player expedition runs and token balances. Pure domain so the
 * "one active run per player" and "reward granted exactly once" rules are
 * testable without the game; persistence is delegated to saved data.
 *
 * <p>Each player keeps at most one run record: the latest one, which is either
 * active or in a terminal state. History beyond the latest run is a later
 * milestone.
 */
public final class ExpeditionLedger {
    private final Map<UUID, ExpeditionRun> runs = new LinkedHashMap<>();
    private final Map<UUID, Integer> tokens = new LinkedHashMap<>();

    /** The player's current or most recent run. */
    public Optional<ExpeditionRun> run(UUID playerId) {
        return Optional.ofNullable(runs.get(playerId));
    }

    /** The player's run if it exists and has not reached a terminal state. */
    public Optional<ExpeditionRun> activeRun(UUID playerId) {
        return run(playerId).filter(ExpeditionRun::isActive);
    }

    /**
     * Registers {@code run} for its player.
     *
     * @throws IllegalStateException if the player already has an active run
     */
    public void startRun(ExpeditionRun run) {
        Objects.requireNonNull(run, "run");
        activeRun(run.playerId()).ifPresent(existing -> {
            throw new IllegalStateException(
                    "Player " + run.playerId() + " already has active run " + existing.runId());
        });
        runs.put(run.playerId(), run);
    }

    /** All tracked runs (any state). */
    public Collection<ExpeditionRun> runs() {
        return Collections.unmodifiableCollection(runs.values());
    }

    /** Whether any player other than {@code excludePlayerId} has an active run. */
    public boolean anyOtherActiveRun(UUID excludePlayerId) {
        return runs.values().stream()
                .anyMatch(r -> r.isActive() && !r.playerId().equals(excludePlayerId));
    }

    /**
     * Whether another player has an active run in {@code dimension}. Marker
     * blocks are shared per dimension, so this scopes the cleanup check.
     */
    public boolean anyOtherActiveRunInDimension(UUID excludePlayerId, String dimension) {
        return runs.values().stream()
                .anyMatch(r -> r.isActive() && !r.playerId().equals(excludePlayerId)
                        && r.definition().dimension().equals(dimension));
    }

    public int tokenBalance(UUID playerId) {
        return tokens.getOrDefault(playerId, 0);
    }

    /**
     * Adds tokens to a player's balance.
     *
     * @throws IllegalArgumentException if {@code amount} is negative
     */
    public void addTokens(UUID playerId, int amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("Token amount must be >= 0");
        }
        tokens.merge(playerId, amount, Integer::sum);
    }

    /** All token balances, for persistence. */
    public Map<UUID, Integer> tokenBalances() {
        return Collections.unmodifiableMap(tokens);
    }

    /** Restores a run record loaded from persistence. */
    public void restoreRun(ExpeditionRun run) {
        runs.put(run.playerId(), run);
    }

    /** Restores a token balance loaded from persistence. */
    public void restoreTokens(UUID playerId, int balance) {
        tokens.put(playerId, balance);
    }
}
