package com.expeditions.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/**
 * Validation of {@link ExpeditionOptions} bounds and {@link Difficulty}
 * lookup/scaling factors.
 */
class ExpeditionOptionsTest {
    @Test
    void rejectsOutOfRangeModifiers() {
        assertThrows(IllegalArgumentException.class,
                () -> new ExpeditionOptions("id", Difficulty.STANDARD, -1, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new ExpeditionOptions("id", Difficulty.STANDARD,
                        RouteGeneration.MAX_CHECKPOINTS + 1, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new ExpeditionOptions("id", Difficulty.STANDARD, 0, -1));
        assertThrows(IllegalArgumentException.class,
                () -> new ExpeditionOptions("id", Difficulty.STANDARD, 0,
                        ExpeditionOptions.MAX_TIME_PERCENT + 1));
    }

    @Test
    void acceptsBoundaryValues() {
        new ExpeditionOptions("id", Difficulty.STANDARD, 0, 0);
        new ExpeditionOptions("id", Difficulty.STANDARD,
                RouteGeneration.MAX_CHECKPOINTS, ExpeditionOptions.MAX_TIME_PERCENT);
        assertEquals(Difficulty.STANDARD, ExpeditionOptions.DEFAULT.difficulty());
        assertEquals("", ExpeditionOptions.DEFAULT.expeditionId());
    }

    @Test
    void difficultyTiersScaleHordeAndReward() {
        assertEquals(50, Difficulty.RELAXED.hordePercent());
        assertEquals(50, Difficulty.RELAXED.rewardPercent());
        assertEquals(200, Difficulty.BRUTAL.hordePercent());
        assertEquals(200, Difficulty.BRUTAL.rewardPercent());
        assertEquals(Difficulty.HARD, Difficulty.byName("hard"));
        assertNull(Difficulty.byName("impossible"));
    }

    @Test
    void generatedRouteKeepsParamsWhenResized() {
        RouteGeneration gen = new RouteGeneration(3, 48, 96, 96, 160, 4, 128, 256);
        RouteGeneration resized = gen.withCheckpoints(7);
        assertEquals(7, resized.checkpoints());
        assertEquals(gen.minSpacing(), resized.minSpacing());
        assertEquals(gen.entryMax(), resized.entryMax());
        assertThrows(IllegalArgumentException.class, () -> gen.withCheckpoints(0));
    }
}
