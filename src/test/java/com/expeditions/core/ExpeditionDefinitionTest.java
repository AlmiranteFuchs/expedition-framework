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

    @Test
    void bonusTimePercentScalesWindow() {
        ExpeditionDefinition def = new ExpeditionDefinition("id", "x", 0,
                "minecraft:overworld", route(), 8, 4, 6000);
        // 200% doubles the window: 12000 now earns the bonus.
        assertEquals(12, def.totalReward(12000, List.of(), 200));
        assertEquals(8, def.totalReward(12001, List.of(), 200));
        // 50% halves it: 3001 now misses.
        assertEquals(8, def.totalReward(3001, List.of(), 50));
        assertEquals(12, def.totalReward(3000, List.of(), 50));
        // 0 keeps the definition window unchanged.
        assertEquals(12, def.totalReward(6000, List.of(), 0));
        assertEquals(8, def.totalReward(6001, List.of(), 0));
    }

    @Test
    void progressionFieldsDefaultAndValidate() {
        ExpeditionDefinition plain = new ExpeditionDefinition("id", "x", 0,
                "minecraft:overworld", route(), 8, 0, 0);
        assertEquals(java.util.Set.of(), plain.requires());
        assertEquals(java.util.Set.of(), plain.grants());
        assertEquals(0, plain.costTokens());

        ExpeditionDefinition gated = new ExpeditionDefinition("id", "x", 0,
                "minecraft:overworld", route(), 8, 0, 0, List.of(),
                ExpeditionModifiers.NONE, java.util.Set.of("k"), java.util.Set.of("g"), 30);
        assertEquals(java.util.Set.of("k"), gated.requires());
        assertEquals(java.util.Set.of("g"), gated.grants());
        assertEquals(30, gated.costTokens());

        assertThrows(IllegalArgumentException.class,
                () -> new ExpeditionDefinition("id", "x", 0, "minecraft:overworld",
                        route(), 8, 0, 0, List.of(), ExpeditionModifiers.NONE,
                        java.util.Set.of(""), java.util.Set.of(), 0));
        assertThrows(IllegalArgumentException.class,
                () -> new ExpeditionDefinition("id", "x", 0, "minecraft:overworld",
                        route(), 8, 0, 0, List.of(), ExpeditionModifiers.NONE,
                        java.util.Set.of(), java.util.Set.of(), -1));
    }
}
