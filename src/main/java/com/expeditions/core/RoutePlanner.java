package com.expeditions.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Turns {@link RouteGeneration} parameters into a concrete
 * {@link ExpeditionRoute}: a seeded random walk starting at a base position.
 * Each leg keeps the configured spacing while the heading meanders up to
 * ±45°, so routes read as an outward journey rather than a straight line.
 * The dimension adapter still decides whether each anchor lands somewhere
 * safe — generation only produces candidates.
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
        List<RouteAnchor> checkpoints = generateLegs(spec, baseX, baseZ, spec.checkpoints(), seed);

        Random rng = new Random(seed);
        // Re-consume the per-leg draws so the exit leg continues the walk
        // exactly as the checkpoint generation left it.
        double angle = rng.nextDouble() * Math.PI * 2;
        double x = baseX;
        double z = baseZ;
        for (int i = 0; i < spec.checkpoints(); i++) {
            int step = spec.minSpacing()
                    + rng.nextInt(spec.maxSpacing() - spec.minSpacing() + 1);
            angle += (rng.nextDouble() - 0.5) * 2 * MAX_TURN;
            x += Math.cos(angle) * step;
            z += Math.sin(angle) * step;
        }

        int exitStep = spec.exitMin() + rng.nextInt(spec.exitMax() - spec.exitMin() + 1);
        angle += (rng.nextDouble() - 0.5) * 2 * MAX_TURN;
        RouteAnchor exit = new RouteAnchor(
                Math.round((float) (x + Math.cos(angle) * exitStep)),
                Math.round((float) (z + Math.sin(angle) * exitStep)));
        return new ExpeditionRoute(checkpoints, exit);
    }

    /**
     * Generates {@code count} checkpoint legs walking outward from
     * {@code baseX}/{@code baseZ}. Used for full routes and for party-size
     * route extensions appended at the last checkpoint while the run's portal
     * is still open.
     */
    public static List<RouteAnchor> generateLegs(RouteGeneration spec, int baseX, int baseZ,
            int count, long seed) {
        Random rng = new Random(seed);
        double angle = rng.nextDouble() * Math.PI * 2;
        double x = baseX;
        double z = baseZ;

        List<RouteAnchor> legs = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            int step = spec.minSpacing()
                    + rng.nextInt(spec.maxSpacing() - spec.minSpacing() + 1);
            angle += (rng.nextDouble() - 0.5) * 2 * MAX_TURN;
            x += Math.cos(angle) * step;
            z += Math.sin(angle) * step;
            legs.add(new RouteAnchor(Math.round((float) x), Math.round((float) z)));
        }
        return legs;
    }
}
