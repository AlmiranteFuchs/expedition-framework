package com.expeditions.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Lifecycle rules for {@link ExpeditionRun}. These tests are deliberately free
 * of Minecraft classes so they run without launching the game.
 */
class ExpeditionRunTest {
    private static final ExpeditionDefinition DEFINITION =
            new ExpeditionDefinition("expeditions:trial", "Trial Expedition", 1);

    private static ExpeditionRun newRun() {
        return new ExpeditionRun(UUID.randomUUID(), DEFINITION);
    }

    @Test
    void newRunStartsActive() {
        ExpeditionRun run = newRun();
        assertEquals(ExpeditionStatus.ACTIVE, run.status());
        assertTrue(run.isActive());
    }

    @Test
    void activeRunCanComplete() {
        ExpeditionRun run = newRun();
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
    void completedRunCannotCompleteAgain() {
        ExpeditionRun run = newRun();
        run.complete();
        assertThrows(IllegalStateException.class, run::complete);
    }

    @Test
    void completedRunCannotFail() {
        ExpeditionRun run = newRun();
        run.complete();
        assertThrows(IllegalStateException.class, run::fail);
    }

    @Test
    void failedRunCannotComplete() {
        ExpeditionRun run = newRun();
        run.fail();
        assertThrows(IllegalStateException.class, run::complete);
    }

    @Test
    void failedRunCannotFailAgain() {
        ExpeditionRun run = newRun();
        run.fail();
        assertThrows(IllegalStateException.class, run::fail);
    }
}
