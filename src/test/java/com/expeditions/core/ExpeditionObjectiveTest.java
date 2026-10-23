package com.expeditions.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Objective model rules: definition-side validation, per-run progress and
 * completion latching, required-objective gating, and bonus calculation.
 * Deliberately free of Minecraft classes.
 */
class ExpeditionObjectiveTest {
    private static final Waypoint CP1 = new Waypoint(10, 64, 10);
    private static final Waypoint CP2 = new Waypoint(20, 64, 10);
    private static final Waypoint CP3 = new Waypoint(30, 64, 10);
    private static final Waypoint EXIT = new Waypoint(40, 64, 10);

    private static ExpeditionRoute route() {
        return new ExpeditionRoute(
                List.of(new RouteAnchor(10, 10), new RouteAnchor(20, 10),
                        new RouteAnchor(30, 10)),
                new RouteAnchor(40, 10));
    }

    private static ExpeditionDefinition definition(ExpeditionObjective... objectives) {
        return new ExpeditionDefinition("expeditions:trial", "Trial Expedition", 1,
                "minecraft:overworld", route(), 8, 0, 0, List.of(objectives));
    }

    private static ExpeditionRun newRun(ExpeditionObjective... objectives) {
        return ExpeditionRun.start(UUID.randomUUID(), UUID.randomUUID(),
                definition(objectives), List.of(CP1, CP2, CP3), EXIT, 0L);
    }

    // ---- Objective construction validation --------------------------------

    @Test
    void objectivesRejectInvalidShapes() {
        assertThrows(IllegalArgumentException.class,
                () -> ExpeditionObjective.visitCheckpoint(" ", "d", false, 1, 0));
        assertThrows(IllegalArgumentException.class,
                () -> ExpeditionObjective.visitCheckpoint("a", "d", false, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> ExpeditionObjective.collectItems("a", "d", false,
                        "minecraft:bone", "minecraft:logs", 4, 0));
        assertThrows(IllegalArgumentException.class,
                () -> ExpeditionObjective.collectItems("a", "d", false,
                        null, null, 4, 0));
        assertThrows(IllegalArgumentException.class,
                () -> ExpeditionObjective.collectItems("a", "d", false,
                        "minecraft:bone", null, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> ExpeditionObjective.collectItems("a", "d", false,
                        "Not An Id!", null, 4, 0));
        assertThrows(IllegalArgumentException.class,
                () -> ExpeditionObjective.timeLimit("a", "d", false, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> ExpeditionObjective.visitCheckpoint("a", "d", false, 1, -1));
    }

    @Test
    void blankDescriptionFallsBackToId() {
        assertEquals("a", ExpeditionObjective.timeLimit("a", "", false, 100, 0)
                .description());
    }

    @Test
    void progressTargetsMatchKinds() {
        assertEquals(1, ExpeditionObjective.visitCheckpoint("a", "d", false, 1, 0)
                .progressTarget());
        assertEquals(5, ExpeditionObjective.collectItems("b", "d", false,
                "minecraft:bone", null, 5, 0).progressTarget());
        assertEquals(200, ExpeditionObjective.timeLimit("c", "d", false, 200, 0)
                .progressTarget());
    }

    // ---- Definition validation --------------------------------------------

    @Test
    void definitionRejectsDuplicateObjectiveIds() {
        ExpeditionObjective a = ExpeditionObjective.visitCheckpoint("dup", "d", false, 1, 0);
        ExpeditionObjective b = ExpeditionObjective.collectItems("dup", "d", false,
                "minecraft:bone", null, 4, 0);
        assertThrows(IllegalArgumentException.class, () -> definition(a, b));
    }

    @Test
    void visitObjectiveCannotTargetBeyondRoute() {
        ExpeditionObjective late = ExpeditionObjective.visitCheckpoint(
                "too_far", "d", false, 4, 0); // route has 3 checkpoints
        assertThrows(IllegalArgumentException.class, () -> definition(late));
        ExpeditionObjective valid = ExpeditionObjective.visitCheckpoint(
                "ok", "d", false, 3, 0);
        definition(valid); // boundary index accepted
    }

    // ---- Run progression ---------------------------------------------------

    @Test
    void runTracksEveryDefinedObjectiveFromStart() {
        ExpeditionRun run = newRun(
                ExpeditionObjective.visitCheckpoint("v", "d", false, 2, 3),
                ExpeditionObjective.collectItems("c", "d", false, "minecraft:bone",
                        null, 4, 5),
                ExpeditionObjective.timeLimit("t", "d", false, 6000, 7));
        assertEquals(3, run.objectiveStates().size());
        assertEquals(ObjectiveState.UNSTARTED, run.objectiveState("v").orElseThrow());
        assertTrue(run.completedObjectiveIds().isEmpty());
    }

    @Test
    void activatingCheckpointCompletesMatchingVisitObjective() {
        ExpeditionRun run = newRun(
                ExpeditionObjective.visitCheckpoint("first", "d", false, 1, 3),
                ExpeditionObjective.visitCheckpoint("last", "d", false, 3, 5));
        assertTrue(run.activateCheckpoint(CP1).stream().anyMatch(o -> o.id().equals("first")));
        assertEquals(new ObjectiveState(1, true), run.objectiveState("first").orElseThrow());
        assertEquals(ObjectiveState.UNSTARTED, run.objectiveState("last").orElseThrow());
        run.activateCheckpoint(CP2);
        assertTrue(run.activateCheckpoint(CP3).stream().anyMatch(o -> o.id().equals("last")));
    }

    @Test
    void collectProgressClampsCompletesAndLatches() {
        ExpeditionRun run = newRun(ExpeditionObjective.collectItems(
                "bones", "d", false, "minecraft:bone", null, 4, 5));
        assertFalse(run.addObjectiveProgress("bones", 3));
        assertEquals(new ObjectiveState(3, false), run.objectiveState("bones").orElseThrow());
        assertTrue(run.addObjectiveProgress("bones", 3)); // overshoot completes at target
        assertEquals(new ObjectiveState(4, true), run.objectiveState("bones").orElseThrow());
        assertFalse(run.addObjectiveProgress("bones", 2)); // latched: no double completion
        assertEquals(new ObjectiveState(4, true), run.objectiveState("bones").orElseThrow());
    }

    @Test
    void collectProgressRejectsInvalidCalls() {
        ExpeditionRun run = newRun(ExpeditionObjective.collectItems(
                "bones", "d", false, "minecraft:bone", null, 4, 5));
        assertThrows(IllegalArgumentException.class,
                () -> run.addObjectiveProgress("bones", -1));
        assertThrows(IllegalArgumentException.class,
                () -> run.addObjectiveProgress("missing", 1));
        assertThrows(IllegalArgumentException.class,
                () -> newRun(ExpeditionObjective.timeLimit("t", "d", false, 100, 0))
                        .addObjectiveProgress("t", 10));
    }

    @Test
    void timedObjectivesEvaluateAtCompletionTime() {
        ExpeditionRun run = newRun(ExpeditionObjective.timeLimit("fast", "d",
                false, 100, 5));
        assertTrue(run.evaluateTimedObjectives(50).stream()
                .anyMatch(o -> o.id().equals("fast")));
        assertEquals(new ObjectiveState(50, true), run.objectiveState("fast").orElseThrow());
        // Re-evaluating while still within the limit is not re-reported.
        assertTrue(run.evaluateTimedObjectives(80).isEmpty());
        assertEquals(new ObjectiveState(80, true), run.objectiveState("fast").orElseThrow());
        // A refused first completion retried after the limit must NOT pay:
        // the state tracks the latest evaluation, not the best.
        assertTrue(run.evaluateTimedObjectives(150).isEmpty());
        assertEquals(new ObjectiveState(150, false), run.objectiveState("fast").orElseThrow());
        assertTrue(run.completedObjectiveIds().isEmpty());

        ExpeditionRun slow = newRun(ExpeditionObjective.timeLimit("fast", "d",
                false, 100, 5));
        assertTrue(slow.evaluateTimedObjectives(150).isEmpty());
        assertEquals(new ObjectiveState(150, false), slow.objectiveState("fast").orElseThrow());

        // Unknown start time never completes a timed objective.
        ExpeditionRun legacy = ExpeditionRun.start(UUID.randomUUID(), UUID.randomUUID(),
                definition(ExpeditionObjective.timeLimit("fast", "d", false, 100, 5)),
                List.of(CP1, CP2, CP3), EXIT);
        assertTrue(legacy.evaluateTimedObjectives(ExpeditionRun.UNKNOWN_START).isEmpty());
        assertFalse(legacy.objectiveState("fast").orElseThrow().completed());
    }

    @Test
    void optionalObjectivesDoNotBlockCompletion() {
        ExpeditionRun run = newRun(
                ExpeditionObjective.collectItems("opt", "d", false, "minecraft:bone",
                        null, 4, 5),
                ExpeditionObjective.timeLimit("speed", "d", false, 10, 5));
        run.activateCheckpoint(CP1);
        run.activateCheckpoint(CP2);
        run.activateCheckpoint(CP3);
        run.evaluateTimedObjectives(9999); // timed objective missed
        run.complete(); // optional misses must not block
        assertEquals(ExpeditionStatus.COMPLETED, run.status());
    }

    @Test
    void requiredObjectivesBlockCompletionUntilDone() {
        ExpeditionRun run = newRun(ExpeditionObjective.collectItems(
                "bones", "Bring bones", true, "minecraft:bone", null, 2, 5));
        run.activateCheckpoint(CP1);
        run.activateCheckpoint(CP2);
        run.activateCheckpoint(CP3);
        assertEquals(List.of("bones"),
                run.missingRequiredObjectives().stream()
                        .map(ExpeditionObjective::id).toList());
        assertThrows(IllegalStateException.class, run::complete);
        assertEquals(ExpeditionStatus.ACTIVE, run.status());

        run.addObjectiveProgress("bones", 2);
        assertTrue(run.missingRequiredObjectives().isEmpty());
        run.complete();
        assertEquals(ExpeditionStatus.COMPLETED, run.status());
    }

    @Test
    void objectivesDoNotProgressOnTerminalRun() {
        ExpeditionRun run = newRun(ExpeditionObjective.collectItems(
                "bones", "d", false, "minecraft:bone", null, 2, 5));
        run.fail();
        assertThrows(IllegalStateException.class, () -> run.addObjectiveProgress("bones", 1));
    }

    @Test
    void restorePreservesObjectiveStates() {
        UUID playerId = UUID.randomUUID();
        ExpeditionRun run = ExpeditionRun.restore(UUID.randomUUID(), playerId,
                Set.of(playerId), definition(
                        ExpeditionObjective.collectItems("bones", "d", false,
                                "minecraft:bone", null, 4, 5),
                        ExpeditionObjective.visitCheckpoint("v", "d", false, 1, 3)),
                List.of(CP1, CP2, CP3), EXIT, EXIT, EXIT, CP1, 0L,
                ExpeditionStatus.ACTIVE, 1, false, 0, List.of(),
                java.util.Map.of("bones", new ObjectiveState(2, false),
                        "v", new ObjectiveState(1, true)));
        assertEquals(new ObjectiveState(2, false), run.objectiveState("bones").orElseThrow());
        assertEquals(new ObjectiveState(1, true), run.objectiveState("v").orElseThrow());
    }

    // ---- Rewards -----------------------------------------------------------

    @Test
    void completedObjectivesAddBonusesToBaseReward() {
        ExpeditionDefinition def = new ExpeditionDefinition("expeditions:trial",
                "Trial", 1, "minecraft:overworld", route(), 8, 4, 100,
                List.of(ExpeditionObjective.collectItems("a", "d", false,
                                "minecraft:bone", null, 4, 5),
                        ExpeditionObjective.timeLimit("b", "d", false, 200, 7)));
        assertEquals(12, def.totalReward(50, Set.of())); // 8 + 4 time bonus
        assertEquals(17, def.totalReward(50, Set.of("a"))); // +5
        assertEquals(24, def.totalReward(50, Set.of("a", "b"))); // 8+4+5+7
        assertEquals(13, def.totalReward(500, Set.of("a"))); // over time limit: 8+5
        // Unknown ids are ignored; objectives not in the set never pay.
        assertEquals(12, def.totalReward(50, Set.of("ghost")));
    }
}
