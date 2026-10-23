package com.expeditions.core;

/**
 * Per-run progress toward one {@link ExpeditionObjective}: how far the run
 * has come (items collected, elapsed ticks, 0/1 for a checkpoint visit) and
 * whether the objective was satisfied. Completion of collect and visit
 * objectives latches — once {@code completed} it never reverts, so rewards
 * can never double-count. Timed objectives record their latest evaluation
 * instead: the recorded state always answers "would the run complete within
 * the limit at this elapsed time".
 */
public record ObjectiveState(int progress, boolean completed) {
    /** The state every tracked objective starts in. */
    public static final ObjectiveState UNSTARTED = new ObjectiveState(0, false);
}
