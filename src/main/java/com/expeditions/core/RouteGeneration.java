package com.expeditions.core;

/**
 * Parameters for a generated route: a meandering path of
 * {@code checkpoints} waypoints starting near the expedition's start
 * position, with successive legs of {@code minSpacing}–{@code maxSpacing}
 * blocks and a final exit leg of {@code exitMin}–{@code exitMax} blocks.
 * The route is generated fresh for every run.
 */
public record RouteGeneration(int checkpoints, int minSpacing, int maxSpacing,
        int exitMin, int exitMax) implements RouteSpec {

    private static final int MAX_CHECKPOINTS = 16;
    private static final int MAX_DISTANCE = 4096;

    public RouteGeneration {
        if (checkpoints < 1 || checkpoints > MAX_CHECKPOINTS) {
            throw new IllegalArgumentException(
                    "Checkpoints must be 1-" + MAX_CHECKPOINTS);
        }
        if (minSpacing < 1 || minSpacing > MAX_DISTANCE
                || maxSpacing < minSpacing || maxSpacing > MAX_DISTANCE) {
            throw new IllegalArgumentException(
                    "Spacing must satisfy 1 <= min <= max <= " + MAX_DISTANCE);
        }
        if (exitMin < 1 || exitMin > MAX_DISTANCE
                || exitMax < exitMin || exitMax > MAX_DISTANCE) {
            throw new IllegalArgumentException(
                    "Exit distance must satisfy 1 <= min <= max <= " + MAX_DISTANCE);
        }
    }
}
