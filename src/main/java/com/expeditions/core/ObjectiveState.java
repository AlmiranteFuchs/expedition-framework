package com.expeditions.core;

/**
 * Per-run progress toward one {@link ExpeditionObjective}: how far the run
 * has come (items collected, elapsed ticks, 0/1 for a checkpoint visit) and
 * whether the objective was satisfied. Completion latches — once
 * {@code completed} it never reverts, so rewards can never double-count.
 */
public record ObjectiveState(int progress, boolean completed) {
}
