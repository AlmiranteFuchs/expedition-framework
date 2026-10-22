package com.expeditions.core;

import java.util.List;
import java.util.Objects;

/**
 * The ordered route of an expedition: a sequence of checkpoints that must be
 * activated in order, followed by an exit that completes the run. Anchors are
 * configured X/Z columns resolved to world positions when a run starts.
 */
public record ExpeditionRoute(List<RouteAnchor> checkpoints, RouteAnchor exit) {
    public ExpeditionRoute {
        Objects.requireNonNull(checkpoints, "checkpoints");
        Objects.requireNonNull(exit, "exit");
        if (checkpoints.isEmpty()) {
            throw new IllegalArgumentException("Route must contain at least one checkpoint");
        }
        checkpoints = List.copyOf(checkpoints);
        if (checkpoints.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Route must not contain null checkpoints");
        }
    }
}
