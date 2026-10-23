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

    /**
     * The player's current or most recent run. Membership-aware: returns the
     * most recently registered run that {@link ExpeditionRun#isMember includes
     * them}, so party members find their shared run even though records are
     * keyed by owner.
     */
    public Optional<ExpeditionRun> run(UUID playerId) {
        ExpeditionRun found = null;
        for (ExpeditionRun candidate : runs.values()) {
            if (candidate.isMember(playerId)) {
                found = candidate;
            }
        }
        return Optional.ofNullable(found);
    }

    /**
     * The player's run if it exists and has not reached a terminal state.
     * Membership-aware: a player participates in a run when it
     * {@link ExpeditionRun#isMember includes them}, so shared party runs are
     * found the same way once parties land.
     */
    public Optional<ExpeditionRun> activeRun(UUID playerId) {
        return runs.values().stream()
                .filter(ExpeditionRun::isActive)
                .filter(r -> r.isMember(playerId))
                .findFirst();
    }

    /**
     * Registers {@code run} for its owner.
     *
     * @throws IllegalStateException if the owner already has an active run
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

    /** Whether any run the player is not a member of is still active. */
    public boolean anyOtherActiveRun(UUID excludePlayerId) {
        return runs.values().stream()
                .anyMatch(r -> r.isActive() && !r.isMember(excludePlayerId));
    }

    /**
     * Whether a run the player is not a member of is active in
     * {@code dimension}. Marker blocks are shared per dimension, so this
     * scopes the cleanup check.
     */
    public boolean anyOtherActiveRunInDimension(UUID excludePlayerId, String dimension) {
        return runs.values().stream()
                .anyMatch(r -> r.isActive() && !r.isMember(excludePlayerId)
                        && r.definition().dimension().equals(dimension));
    }

    public int tokenBalance(UUID playerId) {
        return tokens.getOrDefault(playerId, 0);
    }

    /**
     * Adds tokens to a player's balance. Saturates at
     * {@link Integer#MAX_VALUE} rather than overflowing into a negative
     * balance.
     *
     * @throws IllegalArgumentException if {@code amount} is negative
     */
    public void addTokens(UUID playerId, int amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("Token amount must be >= 0");
        }
        tokens.merge(playerId, amount,
                (balance, add) -> (int) Math.min(Integer.MAX_VALUE, (long) balance + add));
    }

    /**
     * Deducts tokens from a player's balance — the economy sink. Atomic with
     * the rest of the ledger: either the full amount was available and is
     * removed, or nothing changes.
     *
     * @return whether the spend succeeded
     * @throws IllegalArgumentException if {@code amount} is negative
     */
    public boolean spendTokens(UUID playerId, int amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("Token amount must be >= 0");
        }
        int balance = tokenBalance(playerId);
        if (balance < amount) {
            return false;
        }
        tokens.put(playerId, balance - amount);
        return true;
    }

    /** All token balances, for persistence. */
    public Map<UUID, Integer> tokenBalances() {
        return Collections.unmodifiableMap(tokens);
    }

    /** Restores a run record loaded from persistence. */
    public void restoreRun(ExpeditionRun run) {
        runs.put(run.playerId(), run);
    }

    /**
     * Restores a token balance loaded from persistence.
     *
     * @throws IllegalArgumentException if {@code balance} is negative — a
     *         corrupt record; callers skip it rather than trusting the value
     */
    public void restoreTokens(UUID playerId, int balance) {
        if (balance < 0) {
            throw new IllegalArgumentException("Token balance must be >= 0");
        }
        tokens.put(playerId, balance);
    }
}
