package com.expeditions.core;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GuardianEncounterTest {
    private ExpeditionRun run() {
        var route = new ExpeditionRoute(List.of(new RouteAnchor(0, 0)), new RouteAnchor(12, 0));
        var def = new ExpeditionDefinition("test:guardian", "Guardian", 3, "minecraft:overworld", route,
                20, 0, 0, List.of(), ExpeditionModifiers.NONE, Set.of(), Set.of(), 0, CampaignState.FRONTIER, true);
        var run = ExpeditionRun.start(UUID.randomUUID(), UUID.randomUUID(), def, List.of(new Waypoint(0, 64, 0)), new Waypoint(12, 64, 0));
        run.activateCheckpoint(run.checkpoints().getFirst());
        return run;
    }

    @Test void aFinalCheckpointAndAnUnboundKillCannotReplaceTheFight() {
        var run = run();
        assertThrows(IllegalStateException.class, run::complete);
        assertFalse(run.recordGuardianDefeat(UUID.randomUUID()));
        assertThrows(IllegalStateException.class, run::complete);
    }

    @Test void onlyTheBoundGuardianCanOpenExtractionAndOnlyOnce() {
        var run = run(); var id = UUID.randomUUID();
        run.trackChallengeEntities(List.of(id)); run.bindGuardian(id);
        assertThrows(IllegalStateException.class, run::complete);
        assertFalse(run.recordGuardianDefeat(UUID.randomUUID()));
        assertTrue(run.recordGuardianDefeat(id));
        assertFalse(run.recordGuardianDefeat(id));
        run.complete();
        assertFalse(run.recordGuardianDefeat(id));
        assertEquals(ExpeditionStatus.COMPLETED, run.status());
    }

    @Test void cleanupDoesNotCountAsDefeatAndFailedRunsNeverGainProof() {
        var run = run(); var id = UUID.randomUUID();
        run.trackChallengeEntities(List.of(id)); run.bindGuardian(id);
        run.untrackChallengeEntity(id);
        assertFalse(run.guardianDefeated());
        run.fail();
        assertFalse(run.recordGuardianDefeat(id));
    }

    @Test void aLiveRestoredGuardianMustBeOwnedByTheChallenge() {
        var run = run();
        assertThrows(IllegalArgumentException.class, () -> run.restoreGuardian(new GuardianEncounter(UUID.randomUUID(), false)));
    }

    @Test void damageFootprintsMatchTheTelegraphAndRespectHeight() {
        assertTrue(GuardianAttack.SWEEP.hits(3, 0, 4));
        assertFalse(GuardianAttack.SWEEP.hits(3.1, 0, 4));
        assertFalse(GuardianAttack.SWEEP.hits(0, 3, 0));
        assertTrue(GuardianAttack.ROOT_BURST.hits(2.7, 2, 0));
        assertFalse(GuardianAttack.ROOT_BURST.hits(2.8, 0, 0));
        assertFalse(GuardianAttack.ROOT_RING.hits(2.4, 0, 0));
        assertTrue(GuardianAttack.ROOT_RING.hits(5, 0, 0));
        assertFalse(GuardianAttack.ROOT_RING.hits(7.1, 0, 0));
    }

    @Test void theSecondPhaseAlternatesAndEveryAttackHasTimeToReact() {
        assertEquals(GuardianAttack.ROOT_BURST, GuardianAttack.next(false, 1));
        assertEquals(GuardianAttack.SWEEP, GuardianAttack.next(false, 2));
        assertEquals(GuardianAttack.ROOT_BURST, GuardianAttack.next(true, 1));
        assertEquals(GuardianAttack.ROOT_RING, GuardianAttack.next(true, 2));
        for (var attack : GuardianAttack.values()) {
            assertTrue(attack.warningTicks() >= 30);
            assertTrue(attack.recoveryTicks() >= 40);
        }
    }
}
