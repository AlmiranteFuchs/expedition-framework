package com.expeditions.core;

/**
 * The kind of condition an {@link ExpeditionObjective} checks. Persisted by
 * enum name; the stable datapack strings are documented per constant.
 */
public enum ObjectiveKind {
    /**
     * Activate a specific checkpoint of the route. The target is the
     * one-based index into the run's checkpoint list ({@code visit_checkpoint}).
     */
    VISIT_CHECKPOINT,
    /**
     * Collect a number of matching items while the run is active
     * ({@code collect_items}). Matched server-side against an item id or an
     * item tag; the domain only counts progress.
     */
    COLLECT_ITEMS,
    /**
     * Finish the expedition within a time budget ({@code time_limit}).
     * Evaluated once at completion: completed iff elapsed ticks ≤ the limit.
     */
    TIME_LIMIT
}
