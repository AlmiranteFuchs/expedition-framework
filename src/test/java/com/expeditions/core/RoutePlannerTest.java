package com.expeditions.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Generated routes are seeded and deterministic: same inputs, same route.
 * Leg distances stay inside the configured spacing bounds.
 */
class RoutePlannerTest {
    private static final RouteGeneration SPEC = new RouteGeneration(3, 32, 64, 48, 96);

    private static double dist(RouteAnchor a, RouteAnchor b) {
        return Math.hypot(b.x() - a.x(), b.z() - a.z());
    }

    @Test
    void sameSeedProducesSameRoute() {
        assertEquals(RoutePlanner.generate(SPEC, 100, 100, 42L),
                RoutePlanner.generate(SPEC, 100, 100, 42L));
    }

    @Test
    void differentSeedsProduceDifferentRoutes() {
        // Statistically impossible for two different seeds to agree on all legs.
        assertTrue(!RoutePlanner.generate(SPEC, 100, 100, 1L)
                .equals(RoutePlanner.generate(SPEC, 100, 100, 2L)));
    }

    @Test
    void checkpointLegsRespectSpacing() {
        ExpeditionRoute route = RoutePlanner.generate(SPEC, 0, 0, 7L);
        RouteAnchor prev = new RouteAnchor(0, 0);
        for (RouteAnchor cp : route.checkpoints()) {
            double d = dist(prev, cp);
            // Rounded coordinates allow a small slack around exact leg length.
            assertTrue(d >= SPEC.minSpacing() - 1.5 && d <= SPEC.maxSpacing() + 1.5,
                    "leg distance " + d + " out of spacing bounds");
            prev = cp;
        }
    }

    @Test
    void exitLegRespectsDistance() {
        ExpeditionRoute route = RoutePlanner.generate(SPEC, 0, 0, 7L);
        double d = dist(route.checkpoints().getLast(), route.exit());
        assertTrue(d >= SPEC.exitMin() - 1.5 && d <= SPEC.exitMax() + 1.5,
                "exit distance " + d + " out of bounds");
    }

    @Test
    void generatesRequestedCheckpointCount() {
        ExpeditionRoute route = RoutePlanner.generate(
                new RouteGeneration(5, 16, 16, 32, 32), 0, 0, 3L);
        assertEquals(5, route.checkpoints().size());
    }

    @Test
    void specRejectsInvalidParameters() {
        assertThrows(IllegalArgumentException.class,
                () -> new RouteGeneration(0, 32, 64, 48, 96));
        assertThrows(IllegalArgumentException.class,
                () -> new RouteGeneration(17, 32, 64, 48, 96));
        assertThrows(IllegalArgumentException.class,
                () -> new RouteGeneration(3, 64, 32, 48, 96)); // min > max
        assertThrows(IllegalArgumentException.class,
                () -> new RouteGeneration(3, 0, 64, 48, 96));
        assertThrows(IllegalArgumentException.class,
                () -> new RouteGeneration(3, 32, 64, 96, 48)); // exitMin > exitMax
    }
}
