package com.expeditions.core;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * State of one player's expedition attempt. Lifecycle rules live here rather
 * than in Minecraft code so they can be validated by plain unit tests:
 *
 * <ul>
 *   <li>A new run is {@link ExpeditionStatus#ACTIVE} with zero checkpoints done.</li>
 *   <li>Checkpoints activate strictly in order, matched by resolved position.</li>
 *   <li>An active run may complete only once every checkpoint is done, or fail
 *   at any time. Terminal runs reject every further transition.</li>
 *   <li>The completion reward may be recorded at most once.</li>
 * </ul>
 */
public final class ExpeditionRun {
    /** Sentinel for "start time unknown" (e.g. restored from an older save). */
    public static final long UNKNOWN_START = -1L;

    private final UUID runId;
    private final UUID playerId;
    private final ExpeditionDefinition definition;
    private final List<Waypoint> checkpoints;
    private final Waypoint exit;
    private final long startedGameTime;
    private ExpeditionStatus status;
    private int completedCheckpoints;
    private boolean rewarded;
    private int tokensAwarded;

    private ExpeditionRun(UUID runId, UUID playerId, ExpeditionDefinition definition,
            List<Waypoint> checkpoints, Waypoint exit, long startedGameTime,
            ExpeditionStatus status, int completedCheckpoints,
            boolean rewarded, int tokensAwarded) {
        this.runId = Objects.requireNonNull(runId, "runId");
        this.playerId = Objects.requireNonNull(playerId, "playerId");
        this.definition = Objects.requireNonNull(definition, "definition");
        this.checkpoints = List.copyOf(checkpoints);
        this.exit = Objects.requireNonNull(exit, "exit");
        this.startedGameTime = startedGameTime;
        this.status = Objects.requireNonNull(status, "status");
        this.completedCheckpoints = completedCheckpoints;
        this.rewarded = rewarded;
        this.tokensAwarded = tokensAwarded;
    }

    /**
     * Starts a fresh run for {@code playerId} over a route already resolved to
     * world positions. {@code startedGameTime} is the level's game time at
     * start, used for the optional time bonus.
     */
    public static ExpeditionRun start(UUID runId, UUID playerId, ExpeditionDefinition definition,
            List<Waypoint> resolvedCheckpoints, Waypoint resolvedExit, long startedGameTime) {
        if (resolvedCheckpoints.isEmpty()) {
            throw new IllegalArgumentException("Run must contain at least one checkpoint");
        }
        return new ExpeditionRun(runId, playerId, definition, resolvedCheckpoints, resolvedExit,
                startedGameTime, ExpeditionStatus.ACTIVE, 0, false, 0);
    }

    /** Starts a run with unknown timing: never earns a time bonus. */
    public static ExpeditionRun start(UUID runId, UUID playerId, ExpeditionDefinition definition,
            List<Waypoint> resolvedCheckpoints, Waypoint resolvedExit) {
        return start(runId, playerId, definition, resolvedCheckpoints, resolvedExit, UNKNOWN_START);
    }

    /**
     * Restores a run from persisted state. Only used by saved-data loading.
     */
    public static ExpeditionRun restore(UUID runId, UUID playerId, ExpeditionDefinition definition,
            List<Waypoint> checkpoints, Waypoint exit, long startedGameTime,
            ExpeditionStatus status, int completedCheckpoints, boolean rewarded,
            int tokensAwarded) {
        return new ExpeditionRun(runId, playerId, definition, checkpoints, exit, startedGameTime,
                status, completedCheckpoints, rewarded, tokensAwarded);
    }

    public UUID runId() {
        return runId;
    }

    public UUID playerId() {
        return playerId;
    }

    public ExpeditionDefinition definition() {
        return definition;
    }

    public ExpeditionStatus status() {
        return status;
    }

    public boolean isActive() {
        return status == ExpeditionStatus.ACTIVE;
    }

    /**
     * Game time at which the run started, or {@value #UNKNOWN_START} when not
     * known. Exposed for persistence; use {@link #elapsedTicks(long)} for rules.
     */
    public long startedGameTime() {
        return startedGameTime;
    }

    /**
     * Ticks elapsed since the run started, or {@value #UNKNOWN_START} when the
     * start time is not known (restored legacy runs never earn a time bonus).
     */
    public long elapsedTicks(long gameTime) {
        return startedGameTime == UNKNOWN_START
                ? UNKNOWN_START
                : Math.max(0, gameTime - startedGameTime);
    }

    /** Resolved checkpoint positions in activation order. */
    public List<Waypoint> checkpoints() {
        return checkpoints;
    }

    /** Resolved exit position. */
    public Waypoint exit() {
        return exit;
    }

    public int completedCheckpoints() {
        return completedCheckpoints;
    }

    public int totalCheckpoints() {
        return checkpoints.size();
    }

    public boolean allCheckpointsDone() {
        return completedCheckpoints == checkpoints.size();
    }

    /** The position of the checkpoint the run expects next, if any remain. */
    public Optional<Waypoint> nextCheckpoint() {
        return completedCheckpoints < checkpoints.size()
                ? Optional.of(checkpoints.get(completedCheckpoints))
                : Optional.empty();
    }

    /**
     * Activates the checkpoint at {@code position}. Only the next expected
     * checkpoint may advance the run: out-of-order, duplicate or foreign
     * positions are rejected.
     *
     * @throws IllegalStateException if the run is not active or the position is
     *         not the expected next checkpoint
     */
    public void activateCheckpoint(Waypoint position) {
        ensureActive();
        Waypoint expected = nextCheckpoint().orElseThrow(
                () -> new IllegalStateException("Run " + runId + " has no checkpoints left"));
        if (!expected.equals(position)) {
            throw new IllegalStateException(
                    "Run " + runId + " expected checkpoint at " + expected + " but got " + position);
        }
        completedCheckpoints++;
    }

    /**
     * Marks the run completed. Requires an active run with all checkpoints done.
     *
     * @throws IllegalStateException otherwise
     */
    public void complete() {
        ensureActive();
        if (!allCheckpointsDone()) {
            throw new IllegalStateException("Run " + runId + " still has "
                    + (checkpoints.size() - completedCheckpoints) + " checkpoints remaining");
        }
        status = ExpeditionStatus.COMPLETED;
    }

    /**
     * Marks the run failed (e.g. the player died). Only valid while active.
     *
     * @throws IllegalStateException if the run is already in a terminal state
     */
    public void fail() {
        ensureActive();
        status = ExpeditionStatus.FAILED;
    }

    public boolean isRewarded() {
        return rewarded;
    }

    /** Tokens granted by {@link #markRewarded(int)}, or 0 if not rewarded. */
    public int tokensAwarded() {
        return tokensAwarded;
    }

    /**
     * Records that the completion reward of {@code tokens} has been granted.
     * Valid exactly once, and only on a completed run.
     *
     * @throws IllegalStateException otherwise
     */
    public void markRewarded(int tokens) {
        if (status != ExpeditionStatus.COMPLETED) {
            throw new IllegalStateException("Cannot reward run " + runId + " in state " + status);
        }
        if (rewarded) {
            throw new IllegalStateException("Run " + runId + " was already rewarded");
        }
        if (tokens < 0) {
            throw new IllegalArgumentException("Token amount must be >= 0");
        }
        rewarded = true;
        tokensAwarded = tokens;
    }

    private void ensureActive() {
        if (status != ExpeditionStatus.ACTIVE) {
            throw new IllegalStateException("Run " + runId + " is " + status);
        }
    }
}
