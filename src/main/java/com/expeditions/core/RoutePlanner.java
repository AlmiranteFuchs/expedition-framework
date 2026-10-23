package com.expeditions.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Turns {@link RouteGeneration} parameters into a concrete
 * {@link ExpeditionRoute}: a seeded random walk starting at the expedition's
 * base position (the terminal the player activated). Each leg keeps the
 * configured spacing while the heading meanders up to ±45°, so routes read
 * as an outward journey rather than a straight line. The dimension adapter
 * still decides whether each anchor lands somewhere safe — generation only
 * produces candidates.
 */
public final class RoutePlanner {
    private static final double MAX_TURN = Math.PI / 4;

    private RoutePlanner() {
    }

    /**
     * Generates a route around {@code baseX}/{@code baseZ}. The same seed and
     * inputs always produce the same route, which keeps generation unit
     * testable; production passes a fresh seed per run.
     */
    public static ExpeditionRoute generate(RouteGeneration spec, int baseX, int baseZ, long seed) {
        Random rng = new Random(seed);
        double angle = rng.nextDouble() * Math.PI * 2;
        double x = baseX;
        double z = baseZ;

        List<RouteAnchor> checkpoints = new ArrayList<>();
        for (int i = 0; i < spec.checkpoints(); i++) {
            int step = spec.minSpacing()
                    + rng.nextInt(spec.maxSpacing() - spec.minSpacing() + 1);
            angle += (rng.nextDouble() - 0.5) * 2 * MAX_TURN;
            x += Math.cos(angle) * step;
            z += Math.sin(angle) * step;
            checkpoints.add(new RouteAnchor(Math.round((float) x), Math.round((float) z)));
        }

        int exitStep = spec.exitMin() + rng.nextInt(spec.exitMax() - spec.exitMin() + 1);
        angle += (rng.nextDouble() - 0.5) * 2 * MAX_TURN;
        RouteAnchor exit = new RouteAnchor(
                Math.round((float) (x + Math.cos(angle) * exitStep)),
                Math.round((float) (z + Math.sin(angle) * exitStep)));
        return new ExpeditionRoute(checkpoints, exit);
    }
}
