package com.expeditions.core;

/**
 * Lifecycle state of a single {@link ExpeditionRun}.
 *
 * <p>Runs begin {@link #ACTIVE} and may move exactly once into a terminal state
 * ({@link #COMPLETED} or {@link #FAILED}). Terminal states accept no further
 * transitions. This type is intentionally free of Minecraft dependencies so the
 * lifecycle rules can be unit tested without launching the game.
 */
public enum ExpeditionStatus {
    ACTIVE,
    COMPLETED,
    FAILED;

    /**
     * @return whether a run in this state may move to {@code target}.
     */
    public boolean canTransitionTo(ExpeditionStatus target) {
        return this == ACTIVE && target != ACTIVE;
    }

    /**
     * @return whether this state ends the run.
     */
    public boolean isTerminal() {
        return this != ACTIVE;
    }
}
