package com.expeditions.core;

import java.util.Objects;

/**
 * Per-run modifiers chosen at the terminal (screen or command). An empty
 * {@code expeditionId} means "the expedition of the player's dimension" —
 * the historical default; a non-empty id selects an explicit expedition,
 * possibly in another dimension. {@code checkpoints} overrides the
 * checkpoint count of generated routes (0 = definition default; ignored by
 * authored routes). {@code bonusTimePercent} scales the definition's bonus
 * time window (0 = definition window).
 */
public record ExpeditionOptions(String expeditionId, Difficulty difficulty,
        int checkpoints, int bonusTimePercent) {

    /** The historical default: dimension-local expedition, standard tier. */
    public static final ExpeditionOptions DEFAULT = new ExpeditionOptions(
            "", Difficulty.STANDARD, 0, 0);

    /** Maximum bonus-time scaling selectable. */
    public static final int MAX_TIME_PERCENT = 400;

    public ExpeditionOptions {
        Objects.requireNonNull(expeditionId, "expeditionId");
        Objects.requireNonNull(difficulty, "difficulty");
        if (checkpoints < 0 || checkpoints > RouteGeneration.MAX_CHECKPOINTS) {
            throw new IllegalArgumentException(
                    "Checkpoints override must be 0 or 1-"
                            + RouteGeneration.MAX_CHECKPOINTS);
        }
        if (bonusTimePercent < 0 || bonusTimePercent > MAX_TIME_PERCENT) {
            throw new IllegalArgumentException(
                    "Bonus time percent must be 0-" + MAX_TIME_PERCENT);
        }
    }
}
