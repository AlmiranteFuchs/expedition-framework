package com.expeditions.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ExpeditionStatusTest {
    @Test
    void activeCanTransitionToTerminalStatesOnly() {
        assertTrue(ExpeditionStatus.ACTIVE.canTransitionTo(ExpeditionStatus.COMPLETED));
        assertTrue(ExpeditionStatus.ACTIVE.canTransitionTo(ExpeditionStatus.FAILED));
        assertFalse(ExpeditionStatus.ACTIVE.canTransitionTo(ExpeditionStatus.ACTIVE));
    }

    @Test
    void terminalStatesAcceptNoTransitions() {
        for (ExpeditionStatus target : ExpeditionStatus.values()) {
            assertFalse(ExpeditionStatus.COMPLETED.canTransitionTo(target));
            assertFalse(ExpeditionStatus.FAILED.canTransitionTo(target));
        }
    }

    @Test
    void terminalFlagMatchesLifecycle() {
        assertEquals(false, ExpeditionStatus.ACTIVE.isTerminal());
        assertEquals(true, ExpeditionStatus.COMPLETED.isTerminal());
        assertEquals(true, ExpeditionStatus.FAILED.isTerminal());
    }
}
