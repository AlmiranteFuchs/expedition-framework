package com.expeditions.core;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class ExpeditionRouteTest {
    @Test
    void routeRequiresAtLeastOneCheckpoint() {
        assertThrows(IllegalArgumentException.class,
                () -> new ExpeditionRoute(List.of(), new RouteAnchor(5, 5)));
    }

    @Test
    void routeIsImmutable() {
        java.util.List<RouteAnchor> anchors = new java.util.ArrayList<>();
        anchors.add(new RouteAnchor(1, 1));
        ExpeditionRoute route = new ExpeditionRoute(anchors, new RouteAnchor(2, 2));
        anchors.add(new RouteAnchor(9, 9));
        assertEquals(1, route.checkpoints().size());
        assertThrows(UnsupportedOperationException.class,
                () -> route.checkpoints().add(new RouteAnchor(3, 3)));
    }

    @Test
    void definitionRejectsInvalidValues() {
        ExpeditionRoute route = new ExpeditionRoute(
                List.of(new RouteAnchor(1, 1)), new RouteAnchor(2, 2));
        assertThrows(IllegalArgumentException.class,
                () -> new ExpeditionDefinition("", "x", 0, route, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new ExpeditionDefinition("id", "x", -1, route, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new ExpeditionDefinition("id", "x", 0, route, -5));
    }
}
