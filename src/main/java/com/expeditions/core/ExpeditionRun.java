package com.expeditions.core;

import java.util.Objects;
import java.util.UUID;

/**
 * State of one active expedition attempt. The lifecycle rules live here rather
 * than in Minecraft code so they can be validated by plain unit tests:
 *
 * <ul>
 *   <li>A new run is {@link ExpeditionStatus#ACTIVE}.</li>
 *   <li>An active run may transition once to {@link ExpeditionStatus#COMPLETED}
 *   or {@link ExpeditionStatus#FAILED}.</li>
 *   <li>Terminal runs reject every further transition, including re-completing.</li>
 * </ul>
 */
public final class ExpeditionRun {
    private final UUID runId;
    private final ExpeditionDefinition definition;
    private ExpeditionStatus status;

    public ExpeditionRun(UUID runId, ExpeditionDefinition definition) {
        this.runId = Objects.requireNonNull(runId, "runId");
        this.definition = Objects.requireNonNull(definition, "definition");
        this.status = ExpeditionStatus.ACTIVE;
    }

    /**
     * Reconstructs a run from persisted state. Only used when loading saved data;
     * terminal runs are restored with their terminal status intact.
     */
    public ExpeditionRun(UUID runId, ExpeditionDefinition definition, ExpeditionStatus status) {
        this.runId = Objects.requireNonNull(runId, "runId");
        this.definition = Objects.requireNonNull(definition, "definition");
        this.status = Objects.requireNonNull(status, "status");
    }

    public UUID runId() {
        return runId;
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
     * Marks the run completed. Only valid from {@link ExpeditionStatus#ACTIVE}.
     *
     * @throws IllegalStateException if the run is already in a terminal state
     */
    public void complete() {
        transitionTo(ExpeditionStatus.COMPLETED);
    }

    /**
     * Marks the run failed (e.g. the player died). Only valid from
     * {@link ExpeditionStatus#ACTIVE}.
     *
     * @throws IllegalStateException if the run is already in a terminal state
     */
    public void fail() {
        transitionTo(ExpeditionStatus.FAILED);
    }

    private void transitionTo(ExpeditionStatus target) {
        if (!status.canTransitionTo(target)) {
            throw new IllegalStateException(
                    "Cannot transition expedition run " + runId + " from " + status + " to " + target);
        }
        status = target;
    }
}
