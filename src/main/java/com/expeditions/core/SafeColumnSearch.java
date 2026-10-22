package com.expeditions.core;

import java.util.OptionalInt;
import java.util.function.IntPredicate;

/**
 * Finds a safe standing position in a vertical column by scanning downward for
 * a floor with two free blocks above it. The block queries are injected as
 * predicates so the search rule stays testable without Minecraft classes;
 * dimension adapters supply the world-backed implementations.
 */
public final class SafeColumnSearch {
    private SafeColumnSearch() {
    }

    /**
     * Scans {@code topY} down to {@code bottomY} (inclusive) and returns the
     * first Y where a player could stand: {@code y} and {@code y + 1} are free,
     * {@code y - 1} is solid ground, and none of the three blocks is a hazard.
     *
     * @param isFree        block can host a marker / be stood in (air or replaceable)
     * @param isSolidGround block can be stood on (solid, sturdy top face)
     * @param isHazard      block that would hurt or kill (lava, fire, magma, ...)
     * @return the standing Y, or empty if no safe spot exists in the range
     */
    public static OptionalInt findStandingY(IntPredicate isFree, IntPredicate isSolidGround,
            IntPredicate isHazard, int topY, int bottomY) {
        for (int y = topY; y >= bottomY + 1; y--) {
            if (isFree.test(y) && isFree.test(y + 1)
                    && isSolidGround.test(y - 1)
                    && !isHazard.test(y - 1) && !isHazard.test(y) && !isHazard.test(y + 1)) {
                return OptionalInt.of(y);
            }
        }
        return OptionalInt.empty();
    }
}
