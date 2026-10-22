package com.expeditions.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Validation and reward rules of {@link ExpeditionDefinition}: dimension,
 * guaranteed tokens and the optional time bonus.
 */
class ExpeditionDefinitionTest {
    private static ExpeditionRoute route() {
        return new ExpeditionRoute(List.of(new RouteAnchor(1, 1)), new RouteAnchor(2, 2));
    }

    @Test
    void rejectsInvalidValues() {
        assertThrows(IllegalArgumentException.class,
                () -> new ExpeditionDefinition("", "x", 0, "minecraft:overworld",
                        route(), 0, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new ExpeditionDefinition("id", "x", 0, "",
                        route(), 0, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new ExpeditionDefinition("id", "x", -1, "minecraft:overworld",
                        route(), 0, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new ExpeditionDefinition("id", "x", 0, "minecraft:overworld",
                        route(), -5, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new ExpeditionDefinition("id", "x", 0, "minecraft:overworld",
                        route(), 0, -1, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new ExpeditionDefinition("id", "x", 0, "minecraft:overworld",
                        route(), 0, 0, -1));
    }

    @Test
    void guaranteedRewardAlwaysApplies() {
        ExpeditionDefinition def = new ExpeditionDefinition("id", "x", 0,
                "minecraft:overworld", route(), 8, 4, 6000);
        assertEquals(8, def.totalReward(6001));
        assertEquals(8, def.totalReward(Long.MAX_VALUE));
    }

    @Test
    void timeBonusEarnedWithinBudget() {
        ExpeditionDefinition def = new ExpeditionDefinition("id", "x", 0,
                "minecraft:overworld", route(), 8, 4, 6000);
        assertEquals(12, def.totalReward(0));
        assertEquals(12, def.totalReward(6000));
        assertEquals(8, def.totalReward(6001));
    }

    @Test
    void timeBonusDisabledMeansGuaranteedOnly() {
        ExpeditionDefinition def = new ExpeditionDefinition("id", "x", 0,
                "minecraft:overworld", route(), 8, 0, 0);
        assertEquals(8, def.totalReward(0));
        assertEquals(8, def.totalReward(1));
    }

    @Test
    void unknownElapsedNeverEarnsBonus() {
        ExpeditionDefinition def = new ExpeditionDefinition("id", "x", 0,
                "minecraft:overworld", route(), 8, 4, 6000);
        assertEquals(8, def.totalReward(-1));
    }
}
