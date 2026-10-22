package com.expeditions.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Ledger rules: one active run per player, token balances.
 */
class ExpeditionLedgerTest {
    private static final ExpeditionDefinition DEFINITION = new ExpeditionDefinition(
            "expeditions:trial", "Trial Expedition", 1,
            new ExpeditionRoute(List.of(new RouteAnchor(10, 10)), new RouteAnchor(40, 10)), 8);

    private static ExpeditionRun runFor(UUID playerId) {
        return ExpeditionRun.start(UUID.randomUUID(), playerId, DEFINITION,
                List.of(new Waypoint(10, 64, 10)), new Waypoint(40, 64, 10));
    }

    @Test
    void playerCannotStartSecondRunWhileActive() {
        ExpeditionLedger ledger = new ExpeditionLedger();
        UUID player = UUID.randomUUID();
        ledger.startRun(runFor(player));
        assertThrows(IllegalStateException.class, () -> ledger.startRun(runFor(player)));
    }

    @Test
    void playerCanStartNewRunAfterTerminalState() {
        ExpeditionLedger ledger = new ExpeditionLedger();
        UUID player = UUID.randomUUID();
        ExpeditionRun first = runFor(player);
        ledger.startRun(first);
        first.fail();
        ledger.startRun(runFor(player));
        assertTrue(ledger.activeRun(player).isPresent());
    }

    @Test
    void differentPlayersCanRunConcurrently() {
        ExpeditionLedger ledger = new ExpeditionLedger();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        ledger.startRun(runFor(a));
        ledger.startRun(runFor(b));
        assertTrue(ledger.activeRun(a).isPresent());
        assertTrue(ledger.activeRun(b).isPresent());
        assertTrue(ledger.anyOtherActiveRun(a));
        assertEquals(false, ledger.anyOtherActiveRun(UUID.randomUUID()) && ledger.runs().size() != 2);
    }

    @Test
    void tokenBalancesAccumulateAndRejectNegatives() {
        ExpeditionLedger ledger = new ExpeditionLedger();
        UUID player = UUID.randomUUID();
        assertEquals(0, ledger.tokenBalance(player));
        ledger.addTokens(player, 8);
        ledger.addTokens(player, 4);
        assertEquals(12, ledger.tokenBalance(player));
        assertThrows(IllegalArgumentException.class, () -> ledger.addTokens(player, -1));
    }

    @Test
    void restoredStateRoundTrips() {
        ExpeditionLedger ledger = new ExpeditionLedger();
        UUID player = UUID.randomUUID();
        ExpeditionRun run = runFor(player);
        ledger.restoreRun(run);
        ledger.restoreTokens(player, 5);
        assertEquals(run, ledger.run(player).orElseThrow());
        assertEquals(5, ledger.tokenBalance(player));
    }
}
