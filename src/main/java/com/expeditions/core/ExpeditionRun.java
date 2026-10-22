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
 *   <li>The completion reward may be marked granted at most once.</li>
 * </ul>
 */
public final class ExpeditionRun {
    private final UUID runId;
    private final UUID playerId;
    private final ExpeditionDefinition definition;
    private final List<Waypoint> checkpoints;
    private final Waypoint exit;
    private ExpeditionStatus status;
    private int completedCheckpoints;
    private boolean rewarded;

    private ExpeditionRun(UUID runId, UUID playerId, ExpeditionDefinition definition,
            List<Waypoint> checkpoints, Waypoint exit, ExpeditionStatus status,
            int completedCheckpoints, boolean rewarded) {
        this.runId = Objects.requireNonNull(runId, "runId");
        this.playerId = Objects.requireNonNull(playerId, "playerId");
        this.definition = Objects.requireNonNull(definition, "definition");
        this.checkpoints = List.copyOf(checkpoints);
        this.exit = Objects.requireNonNull(exit, "exit");
        this.status = Objects.requireNonNull(status, "status");
        this.completedCheckpoints = completedCheckpoints;
        this.rewarded = rewarded;
    }

    /**
     * Starts a fresh run for {@code playerId} over a route already resolved to
     * world positions.
     */
    public static ExpeditionRun start(UUID runId, UUID playerId, ExpeditionDefinition definition,
            List<Waypoint> resolvedCheckpoints, Waypoint resolvedExit) {
        if (resolvedCheckpoints.isEmpty()) {
            throw new IllegalArgumentException("Run must contain at least one checkpoint");
        }
        return new ExpeditionRun(runId, playerId, definition, resolvedCheckpoints, resolvedExit,
                ExpeditionStatus.ACTIVE, 0, false);
    }

    /**
     * Restores a run from persisted state. Only used by saved-data loading.
     */
    public static ExpeditionRun restore(UUID runId, UUID playerId, ExpeditionDefinition definition,
            List<Waypoint> checkpoints, Waypoint exit, ExpeditionStatus status,
            int completedCheckpoints, boolean rewarded) {
        return new ExpeditionRun(runId, playerId, definition, checkpoints, exit, status,
                completedCheckpoints, rewarded);
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

    /**
     * Records that the completion reward has been granted. Valid exactly once,
     * and only on a completed run.
     *
     * @throws IllegalStateException otherwise
     */
    public void markRewarded() {
        if (status != ExpeditionStatus.COMPLETED) {
            throw new IllegalStateException("Cannot reward run " + runId + " in state " + status);
        }
        if (rewarded) {
            throw new IllegalStateException("Run " + runId + " was already rewarded");
        }
        rewarded = true;
    }

    private void ensureActive() {
        if (status != ExpeditionStatus.ACTIVE) {
            throw new IllegalStateException("Run " + runId + " is " + status);
        }
    }
}
