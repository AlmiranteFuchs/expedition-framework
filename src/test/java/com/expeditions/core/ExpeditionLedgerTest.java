package com.expeditions.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    void tokenBalancesSaturateAtMaxValue() {
        ExpeditionLedger ledger = new ExpeditionLedger();
        UUID player = UUID.randomUUID();
        ledger.addTokens(player, Integer.MAX_VALUE - 5);
        ledger.addTokens(player, 100);
        assertEquals(Integer.MAX_VALUE, ledger.tokenBalance(player));
    }

    @Test
    void spendTokensDeductsOnlyWhenAffordable() {
        ExpeditionLedger ledger = new ExpeditionLedger();
        UUID player = UUID.randomUUID();
        assertTrue(ledger.spendTokens(player, 0)); // zero-cost spend always succeeds
        ledger.addTokens(player, 20);
        assertFalse(ledger.spendTokens(player, 21));
        assertEquals(20, ledger.tokenBalance(player)); // failed spend is atomic
        assertTrue(ledger.spendTokens(player, 7));
        assertTrue(ledger.spendTokens(player, 13));
        assertEquals(0, ledger.tokenBalance(player));
        assertThrows(IllegalArgumentException.class, () -> ledger.spendTokens(player, -1));
    }

    @Test
    void restoreRejectsNegativeBalance() {
        ExpeditionLedger ledger = new ExpeditionLedger();
        UUID player = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class,
                () -> ledger.restoreTokens(player, -12));
        assertEquals(0, ledger.tokenBalance(player));
    }

    @Test
    void dimensionScopedActiveRunCheck() {
        ExpeditionLedger ledger = new ExpeditionLedger();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        ExpeditionDefinition nether = new ExpeditionDefinition("expeditions:nether",
                "Nether", 0, "minecraft:the_nether",
                new ExpeditionRoute(List.of(new RouteAnchor(1, 1)), new RouteAnchor(2, 2)),
                16, 0, 0);
        ledger.startRun(runFor(a));
        ledger.startRun(ExpeditionRun.start(UUID.randomUUID(), b, nether,
                List.of(new Waypoint(1, 60, 1)), new Waypoint(2, 60, 2)));
        assertTrue(ledger.anyOtherActiveRunInDimension(a, "minecraft:overworld") == false);
        assertTrue(ledger.anyOtherActiveRunInDimension(a, "minecraft:the_nether"));
        assertTrue(ledger.anyOtherActiveRunInDimension(b, "minecraft:overworld"));
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

    @Test
    void unlocksAreGrantedIdempotently() {
        ExpeditionLedger ledger = new ExpeditionLedger();
        UUID player = UUID.randomUUID();
        assertFalse(ledger.hasUnlock(player, "expeditions:nether_clear"));
        assertTrue(ledger.grantUnlock(player, "expeditions:nether_clear"));
        assertTrue(ledger.hasUnlock(player, "expeditions:nether_clear"));
        assertFalse(ledger.grantUnlock(player, "expeditions:nether_clear"));
        assertEquals(java.util.Set.of("expeditions:nether_clear"), ledger.unlocks(player));
        assertThrows(IllegalArgumentException.class,
                () -> ledger.grantUnlock(player, "  "));
    }

    @Test
    void restoredUnlocksRoundTrip() {
        ExpeditionLedger ledger = new ExpeditionLedger();
        UUID player = UUID.randomUUID();
        ledger.restoreUnlocks(player,
                java.util.Set.of("a", "b"));
        assertEquals(java.util.Set.of("a", "b"), ledger.unlocks(player));
        assertEquals(java.util.Set.of("a", "b"), ledger.unlockRecords().get(player));
        assertThrows(IllegalArgumentException.class,
                () -> ledger.restoreUnlocks(player, java.util.Set.of("")));
    }

    @Test
    void checkRequirementsReportsMissingKeysAndTokens() {
        ExpeditionLedger ledger = new ExpeditionLedger();
        UUID player = UUID.randomUUID();
        ExpeditionDefinition gated = new ExpeditionDefinition(
                "expeditions:big_bad", "Big Bad", 5, "minecraft:overworld",
                new ExpeditionRoute(List.of(new RouteAnchor(0, 0)),
                        new RouteAnchor(10, 0)),
                50, 0, 0, List.of(), ExpeditionModifiers.NONE,
                java.util.Set.of("k1", "k2"), java.util.Set.of(), 40);
        RequirementCheck check = ledger.checkRequirements(player, gated);
        assertFalse(check.isSatisfied());
        assertEquals(java.util.Set.of("k1", "k2"), check.missingKeys());
        assertEquals(40, check.missingTokens());

        ledger.grantUnlock(player, "k1");
        ledger.addTokens(player, 40);
        check = ledger.checkRequirements(player, gated);
        assertFalse(check.isSatisfied());
        assertEquals(java.util.Set.of("k2"), check.missingKeys());
        assertEquals(0, check.missingTokens());

        ledger.grantUnlock(player, "k2");
        assertTrue(ledger.checkRequirements(player, gated).isSatisfied());
    }

    @Test
    void checkRequirementsSatisfiedWhenNothingDemanded() {
        ExpeditionLedger ledger = new ExpeditionLedger();
        assertTrue(ledger.checkRequirements(UUID.randomUUID(), DEFINITION).isSatisfied());
    }
}
