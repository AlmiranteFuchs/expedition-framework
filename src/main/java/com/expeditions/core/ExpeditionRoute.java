package com.expeditions.core;

import java.util.List;
import java.util.Objects;

/**
 * The ordered route of an expedition: a sequence of checkpoints that must be
 * activated in order, followed by an exit that completes the run. Anchors are
 * X/Z columns — either configured or produced by {@link RoutePlanner} — that
 * the server resolves to world positions when a run starts.
 */
public record ExpeditionRoute(List<RouteAnchor> checkpoints, RouteAnchor exit)
        implements RouteSpec {
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
