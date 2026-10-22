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
}
