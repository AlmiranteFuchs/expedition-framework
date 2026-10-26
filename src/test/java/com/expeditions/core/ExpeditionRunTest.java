package com.expeditions.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Lifecycle and checkpoint rules for {@link ExpeditionRun}. These tests are
 * deliberately free of Minecraft classes so they run without launching the game.
 */
class ExpeditionRunTest {
    private static final Waypoint CP1 = new Waypoint(10, 64, 10);
    private static final Waypoint CP2 = new Waypoint(20, 64, 10);
    private static final Waypoint CP3 = new Waypoint(30, 64, 10);
    private static final Waypoint EXIT = new Waypoint(40, 64, 10);

    private static ExpeditionDefinition definition() {
        return new ExpeditionDefinition("expeditions:trial", "Trial Expedition", 1,
                new ExpeditionRoute(
                        List.of(new RouteAnchor(10, 10), new RouteAnchor(20, 10),
                                new RouteAnchor(30, 10)),
                        new RouteAnchor(40, 10)),
                8);
    }

    private static ExpeditionRun newRun() {
        return ExpeditionRun.start(UUID.randomUUID(), UUID.randomUUID(), definition(),
                List.of(CP1, CP2, CP3), EXIT);
    }

    private static ExpeditionRun fullyActivatedRun() {
        ExpeditionRun run = newRun();
        run.activateCheckpoint(CP1);
        run.activateCheckpoint(CP2);
        run.activateCheckpoint(CP3);
        return run;
    }

    @Test
    void newRunStartsActiveWithZeroCheckpoints() {
        ExpeditionRun run = newRun();
        assertEquals(ExpeditionStatus.ACTIVE, run.status());
        assertTrue(run.isActive());
        assertEquals(0, run.completedCheckpoints());
        assertEquals(3, run.totalCheckpoints());
        assertEquals(CP1, run.nextCheckpoint().orElseThrow());
    }

    @Test
    void checkpointsMustActivateInOrder() {
        ExpeditionRun run = newRun();
        assertThrows(IllegalStateException.class, () -> run.activateCheckpoint(CP2));
        assertThrows(IllegalStateException.class, () -> run.activateCheckpoint(CP3));
        assertEquals(0, run.completedCheckpoints());

        run.activateCheckpoint(CP1);
        run.activateCheckpoint(CP2);
        assertEquals(CP3, run.nextCheckpoint().orElseThrow());
        run.activateCheckpoint(CP3);
        assertTrue(run.allCheckpointsDone());
    }

    @Test
    void duplicateCheckpointActivationIsRejected() {
        ExpeditionRun run = newRun();
        run.activateCheckpoint(CP1);
        assertThrows(IllegalStateException.class, () -> run.activateCheckpoint(CP1));
        assertEquals(1, run.completedCheckpoints());
    }

    @Test
    void unknownPositionIsRejected() {
        ExpeditionRun run = newRun();
        assertThrows(IllegalStateException.class,
                () -> run.activateCheckpoint(new Waypoint(999, 64, 999)));
    }

    @Test
    void exitCannotCompleteUnfinishedRun() {
        ExpeditionRun run = newRun();
        assertThrows(IllegalStateException.class, run::complete);
        run.activateCheckpoint(CP1);
        run.activateCheckpoint(CP2);
        assertThrows(IllegalStateException.class, run::complete);
        assertEquals(ExpeditionStatus.ACTIVE, run.status());
    }

    @Test
    void validRouteCompletes() {
        ExpeditionRun run = fullyActivatedRun();
        run.complete();
        assertEquals(ExpeditionStatus.COMPLETED, run.status());
    }

    @Test
    void activeRunCanFail() {
        ExpeditionRun run = newRun();
        run.fail();
        assertEquals(ExpeditionStatus.FAILED, run.status());
    }

    @Test
    void failedRunCannotCompleteOrActivate() {
        ExpeditionRun run = fullyActivatedRun();
        run.fail();
        assertThrows(IllegalStateException.class, run::complete);
        assertThrows(IllegalStateException.class, () -> run.activateCheckpoint(CP1));
    }

    @Test
    void completedRunCannotFailOrCompleteAgain() {
        ExpeditionRun run = fullyActivatedRun();
        run.complete();
        assertThrows(IllegalStateException.class, run::fail);
        assertThrows(IllegalStateException.class, run::complete);
    }

    @Test
    void rewardCanBeMarkedExactlyOnceAfterCompletion() {
        ExpeditionRun run = fullyActivatedRun();
        assertThrows(IllegalStateException.class, () -> run.markRewarded(8));
        run.complete();
        run.markRewarded(8);
        assertTrue(run.isRewarded());
        assertEquals(8, run.tokensAwarded());
        assertThrows(IllegalStateException.class, () -> run.markRewarded(8));
    }

    @Test
    void failedRunCannotBeRewarded() {
        ExpeditionRun run = fullyActivatedRun();
        run.fail();
        assertThrows(IllegalStateException.class, () -> run.markRewarded(8));
        assertFalse(run.isRewarded());
    }

    @Test
    void elapsedTicksCountsFromStartTime() {
        ExpeditionRun run = ExpeditionRun.start(UUID.randomUUID(), UUID.randomUUID(),
                definition(), List.of(CP1, CP2, CP3), EXIT, 1000L);
        assertEquals(1000L, run.startedGameTime());
        assertEquals(0L, run.elapsedTicks(1000L));
        assertEquals(250L, run.elapsedTicks(1250L));
    }

    @Test
    void unknownStartTimeNeverElapses() {
        ExpeditionRun run = newRun(); // 5-arg start leaves timing unknown
        assertEquals(ExpeditionRun.UNKNOWN_START, run.startedGameTime());
        assertEquals(ExpeditionRun.UNKNOWN_START, run.elapsedTicks(99999L));
    }

    @Test
    void restoredRunPreservesState() {
        ExpeditionRun run = ExpeditionRun.restore(UUID.randomUUID(), UUID.randomUUID(),
                definition(), List.of(CP1, CP2, CP3), EXIT, 500L,
                ExpeditionStatus.ACTIVE, 2, false, 0);
        assertEquals(2, run.completedCheckpoints());
        assertEquals(500L, run.startedGameTime());
        assertEquals(CP3, run.nextCheckpoint().orElseThrow());
    }

    @Test
    void restoreRejectsCorruptShapes() {
        UUID runId = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();
        // No checkpoints at all: the run could never progress.
        assertThrows(IllegalArgumentException.class,
                () -> ExpeditionRun.restore(runId, playerId, definition(),
                        List.of(), EXIT, 0L, ExpeditionStatus.ACTIVE, 0, false, 0));
        // Progress beyond the checkpoint list.
        assertThrows(IllegalArgumentException.class,
                () -> ExpeditionRun.restore(runId, playerId, definition(),
                        List.of(CP1, CP2, CP3), EXIT, 0L,
                        ExpeditionStatus.ACTIVE, 4, false, 0));
        assertThrows(IllegalArgumentException.class,
                () -> ExpeditionRun.restore(runId, playerId, definition(),
                        List.of(CP1, CP2, CP3), EXIT, 0L,
                        ExpeditionStatus.ACTIVE, -1, false, 0));
        // A failed run must not persist a granted reward.
        assertThrows(IllegalArgumentException.class,
                () -> ExpeditionRun.restore(runId, playerId, definition(),
                        List.of(CP1, CP2, CP3), EXIT, 0L,
                        ExpeditionStatus.FAILED, 3, true, 8));
    }

    @Test
    void partyJoinsWhilePortalOpenAndSealsOnFirstCheckpoint() {
        ExpeditionRun run = newRun();
        assertTrue(run.portalOpen());
        UUID member = UUID.randomUUID();
        run.joinParty(member);
        assertTrue(run.isMember(member));
        run.joinParty(member); // idempotent
        assertEquals(2, run.party().size());

        run.activateCheckpoint(CP1);
        assertFalse(run.portalOpen());
        assertThrows(IllegalStateException.class, () -> run.joinParty(UUID.randomUUID()));
    }

    @Test
    void partyLeaveRemovesMemberButLastMemberCannotLeave() {
        UUID owner = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        ExpeditionRun run = ExpeditionRun.start(UUID.randomUUID(), owner,
                java.util.Set.of(owner, member), definition(),
                List.of(CP1, CP2, CP3), EXIT, new Waypoint(0, 64, 0),
                new Waypoint(0, 65, 0), new Waypoint(5, 64, 0), 0L);

        run.leaveParty(member);
        assertFalse(run.isMember(member));
        assertThrows(IllegalStateException.class, () -> run.leaveParty(member));
        assertThrows(IllegalStateException.class, () -> run.leaveParty(owner));
        assertTrue(run.isActive());
    }

    @Test
    void routeExtendsOnlyWhilePortalOpen() {
        ExpeditionRun run = newRun();
        run.extendRoute(List.of(new Waypoint(35, 64, 10)));
        assertEquals(4, run.totalCheckpoints());
        assertEquals(new Waypoint(35, 64, 10), run.checkpoints().get(3));

        run.activateCheckpoint(CP1);
        assertThrows(IllegalStateException.class,
                () -> run.extendRoute(List.of(new Waypoint(36, 64, 10))));
    }

    @Test
    void joinRejectedOnTerminalRun() {
        ExpeditionRun run = fullyActivatedRun();
        run.complete();
        assertThrows(IllegalStateException.class, () -> run.joinParty(UUID.randomUUID()));
    }

    @Test
    void challengeEntitiesTrackAndUntrackWhileActive() {
        ExpeditionRun run = newRun();
        assertTrue(run.challengeEntities().isEmpty());
        List<UUID> horde = List.of(UUID.randomUUID(), UUID.randomUUID());
        run.trackChallengeEntities(horde);
        assertEquals(horde, run.challengeEntities());
        run.untrackChallengeEntity(horde.get(0));
        assertEquals(List.of(horde.get(1)), run.challengeEntities());
    }

    @Test
    void challengeEntitiesRejectedOnTerminalRun() {
        ExpeditionRun run = fullyActivatedRun();
        run.complete();
        assertThrows(IllegalStateException.class,
                () -> run.trackChallengeEntities(List.of(UUID.randomUUID())));
    }

    @Test
    void restoredRunPreservesChallengeEntities() {
        List<UUID> entities = List.of(UUID.randomUUID());
        UUID playerId = UUID.randomUUID();
        ExpeditionRun run = ExpeditionRun.restore(UUID.randomUUID(), playerId,
                java.util.Set.of(playerId), definition(), List.of(CP1, CP2, CP3), EXIT,
                EXIT, EXIT, CP1, 0L, ExpeditionStatus.ACTIVE, 1, false, 0, entities);
        assertEquals(entities, run.challengeEntities());
    }

    // ---- Physical mission artifact -----------------------------------------

    private static ExpeditionDefinition artifactDefinition(boolean required) {
        return new ExpeditionDefinition("expeditions:artifact_run", "Cargo", 1,
                "minecraft:overworld", new ExpeditionRoute(
                        List.of(new RouteAnchor(10, 10), new RouteAnchor(20, 10),
                                new RouteAnchor(30, 10)),
                        new RouteAnchor(40, 10)),
                8, 0, 0,
                List.of(ExpeditionObjective.recoverArtifact(
                        "cargo", "Recover the artifact", required, 2, 3)),
                ExpeditionModifiers.NONE);
    }

    private static ExpeditionRun artifactRun(boolean required) {
        return ExpeditionRun.start(UUID.randomUUID(), UUID.randomUUID(),
                artifactDefinition(required), List.of(CP1, CP2, CP3), EXIT, 0L);
    }

    @Test
    void artifactIssuesExactlyOncePerRun() {
        ExpeditionRun run = artifactRun(true);
        assertFalse(run.artifactIssued());
        run.markArtifactIssued();
        assertTrue(run.artifactIssued());
        // The latch guards re-issuance on repeat activations/restarts; the
        // manager only grants while this is false.
    }

    @Test
    void artifactDeliveryLatchesAndUnlatchesWithPossession() {
        ExpeditionRun run = artifactRun(true);
        ExpeditionObjective objective = run.definition().objectives().get(0);
        // Not in hand → not delivered.
        assertFalse(run.deliverArtifact(objective.id(), false));
        assertFalse(run.objectiveState(objective.id()).orElseThrow().completed());
        // In hand → delivered.
        assertTrue(run.deliverArtifact(objective.id(), true));
        assertTrue(run.objectiveState(objective.id()).orElseThrow().completed());
        // Clicking again while still holding: already delivered, no re-complete.
        assertFalse(run.deliverArtifact(objective.id(), true));
        // Dropped before extraction → pending again (at-risk rule).
        run.deliverArtifact(objective.id(), false);
        assertFalse(run.objectiveState(objective.id()).orElseThrow().completed());
    }

    @Test
    void requiredArtifactGatesCompletionUntilDelivered() {
        ExpeditionRun run = artifactRun(true);
        run.activateCheckpoint(CP1);
        run.activateCheckpoint(CP2);
        run.activateCheckpoint(CP3);
        // All checkpoints done but the artifact was never delivered.
        assertThrows(IllegalStateException.class, run::complete);
        run.deliverArtifact("cargo", true);
        run.complete();
        assertEquals(ExpeditionStatus.COMPLETED, run.status());
    }

    @Test
    void optionalArtifactSkipsWithoutBlockingCompletion() {
        ExpeditionRun run = artifactRun(false);
        run.activateCheckpoint(CP1);
        run.activateCheckpoint(CP2);
        run.activateCheckpoint(CP3);
        run.complete();
        assertEquals(ExpeditionStatus.COMPLETED, run.status());
        assertFalse(run.objectiveState("cargo").orElseThrow().completed());
    }

    @Test
    void artifactDeliveryRejectedOnTerminalRun() {
        ExpeditionRun run = artifactRun(false);
        run.activateCheckpoint(CP1);
        run.activateCheckpoint(CP2);
        run.activateCheckpoint(CP3);
        run.complete();
        assertThrows(IllegalStateException.class,
                () -> run.deliverArtifact("cargo", true));
        assertThrows(IllegalStateException.class, run::markArtifactIssued);
    }

    @Test
    void deliverArtifactRejectsWrongObjectiveKind() {
        ExpeditionDefinition def = new ExpeditionDefinition("expeditions:t", "T", 1,
                "minecraft:overworld", new ExpeditionRoute(
                        List.of(new RouteAnchor(10, 10), new RouteAnchor(20, 10),
                                new RouteAnchor(30, 10)),
                        new RouteAnchor(40, 10)),
                8, 0, 0,
                List.of(ExpeditionObjective.visitCheckpoint(
                        "visit", "Visit", true, 2, 0)),
                ExpeditionModifiers.NONE);
        ExpeditionRun run = ExpeditionRun.start(UUID.randomUUID(), UUID.randomUUID(),
                def, List.of(CP1, CP2, CP3), EXIT, 0L);
        assertThrows(IllegalArgumentException.class,
                () -> run.deliverArtifact("visit", true));
    }

    // ---- Defense events ----------------------------------------------------

    private static ExpeditionDefinition defenseDefinition(boolean required) {
        return new ExpeditionDefinition("expeditions:siege", "Siege", 1,
                "minecraft:overworld", new ExpeditionRoute(
                        List.of(new RouteAnchor(10, 10), new RouteAnchor(20, 10),
                                new RouteAnchor(30, 10)),
                        new RouteAnchor(40, 10)),
                8, 0, 0,
                List.of(ExpeditionObjective.defendDevice(
                        "siege", "Hold the device", required, 2, 600, 4)),
                ExpeditionModifiers.NONE);
    }

    private static ExpeditionRun defenseRun(boolean required) {
        return ExpeditionRun.start(UUID.randomUUID(), UUID.randomUUID(),
                defenseDefinition(required), List.of(CP1, CP2, CP3), EXIT, 0L);
    }

    @Test
    void defenseStartsOnceOnActiveRun() {
        ExpeditionRun run = defenseRun(true);
        assertTrue(run.defense().isEmpty());
        run.startDefense("siege", CP2, 20, 100L, 600L, 200L);
        DefenseState defense = run.defense().orElseThrow();
        assertEquals(20, defense.hp());
        assertEquals(700L, defense.endTick());
        assertEquals(300L, defense.nextWaveTick());
        assertThrows(IllegalStateException.class,
                () -> run.startDefense("siege", CP2, 20, 100L, 600L, 200L));
    }

    @Test
    void startDefenseRejectsWrongKindAndUnknownObjective() {
        ExpeditionDefinition def = new ExpeditionDefinition("expeditions:t", "T", 1,
                "minecraft:overworld", new ExpeditionRoute(
                        List.of(new RouteAnchor(10, 10), new RouteAnchor(20, 10),
                                new RouteAnchor(30, 10)),
                        new RouteAnchor(40, 10)),
                8, 0, 0,
                List.of(ExpeditionObjective.visitCheckpoint(
                        "visit", "Visit", true, 2, 0)),
                ExpeditionModifiers.NONE);
        ExpeditionRun run = ExpeditionRun.start(UUID.randomUUID(), UUID.randomUUID(),
                def, List.of(CP1, CP2, CP3), EXIT, 0L);
        assertThrows(IllegalArgumentException.class,
                () -> run.startDefense("visit", CP2, 20, 0L, 600L, 200L));
        assertThrows(IllegalArgumentException.class,
                () -> run.startDefense("missing", CP2, 20, 0L, 600L, 200L));
    }

    @Test
    void deviceDamageDestroysExactlyOnceAndFailsObjective() {
        ExpeditionRun run = defenseRun(true);
        run.startDefense("siege", CP2, 20, 0L, 600L, 200L);
        assertFalse(run.damageDevice(5));
        assertEquals(15, run.defense().orElseThrow().hp());
        assertTrue(run.damageDevice(15));
        // Exactly once: the siege is gone and the objective marked failed.
        assertTrue(run.defense().isEmpty());
        assertTrue(run.objectiveFailed("siege"));
        assertTrue(run.failedObjectiveIds().contains("siege"));
        assertFalse(run.damageDevice(99), "no live device — nothing to destroy");
    }

    @Test
    void defenseSurvivesByTimerAndCompletesObjective() {
        ExpeditionRun run = defenseRun(false);
        run.startDefense("siege", CP2, 20, 0L, 600L, 200L);
        assertTrue(run.completeDefenseIfExpired(599L).isEmpty(),
                "the siege must not complete early");
        assertTrue(run.completeDefenseIfExpired(600L).isPresent());
        assertTrue(run.objectiveState("siege").orElseThrow().completed());
        assertTrue(run.defense().isEmpty(), "a survived siege stands down");
    }

    @Test
    void defenseWavePacingTracksNextTick() {
        ExpeditionRun run = defenseRun(false);
        run.startDefense("siege", CP2, 20, 0L, 600L, 200L);
        assertFalse(run.defenseWaveDue(100L));
        assertTrue(run.defenseWaveDue(200L));
        run.defenseWaveSpawned(400L);
        assertFalse(run.defenseWaveDue(300L));
        assertTrue(run.defenseWaveDue(400L));
    }

    @Test
    void defenseRejectedOnTerminalRun() {
        ExpeditionRun run = defenseRun(false);
        run.fail();
        assertThrows(IllegalStateException.class,
                () -> run.startDefense("siege", CP2, 20, 0L, 600L, 200L));
        assertThrows(IllegalStateException.class, () -> run.damageDevice(1));
    }

    @Test
    void restoredRunPreservesDefenseAndFailures() {
        UUID playerId = UUID.randomUUID();
        DefenseState defense = new DefenseState("siege", CP2, 12, 20, 700L, 300L);
        ExpeditionRun run = ExpeditionRun.restore(UUID.randomUUID(), playerId,
                java.util.Set.of(playerId), defenseDefinition(false),
                List.of(CP1, CP2, CP3), EXIT, EXIT, EXIT, CP1, 0L,
                ExpeditionStatus.ACTIVE, 1, false, 0, false, List.of(),
                java.util.Map.of(), ExpeditionOptions.DEFAULT, "minecraft:overworld",
                defense, java.util.Set.of());
        assertEquals(defense, run.defense().orElseThrow());

        ExpeditionRun failed = ExpeditionRun.restore(UUID.randomUUID(), playerId,
                java.util.Set.of(playerId), defenseDefinition(false),
                List.of(CP1, CP2, CP3), EXIT, EXIT, EXIT, CP1, 0L,
                ExpeditionStatus.ACTIVE, 1, false, 0, false, List.of(),
                java.util.Map.of(), ExpeditionOptions.DEFAULT, "minecraft:overworld",
                null, java.util.Set.of("siege"));
        assertTrue(failed.objectiveFailed("siege"));
    }
}
