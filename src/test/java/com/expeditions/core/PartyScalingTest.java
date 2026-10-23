package com.expeditions.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Party reward scaling: the pool grows with members, shares follow the split mode. */
class PartyScalingTest {
    @Test
    void soloOrZeroBonusKeepsBaseReward() {
        assertEquals(10, PartyScaling.scaledTotal(10, 1, 0.5));
        assertEquals(10, PartyScaling.scaledTotal(10, 4, 0.0));
        assertEquals(10, PartyScaling.scaledTotal(10, 1, 2.0));
    }

    @Test
    void poolScalesWithPartySize() {
        // base * (1 + 0.5 * extra members): 8 -> 12 for a pair, 16 for a trio.
        assertEquals(12, PartyScaling.scaledTotal(8, 2, 0.5));
        assertEquals(16, PartyScaling.scaledTotal(8, 3, 0.5));
        assertEquals(24, PartyScaling.scaledTotal(8, 3, 1.0));
    }

    @Test
    void shareSplitsOrPaysFull() {
        assertEquals(6, PartyScaling.memberShare(12, 2, true));
        assertEquals(12, PartyScaling.memberShare(12, 2, false));
        // Rounds down rather than overpaying the pool.
        assertEquals(4, PartyScaling.memberShare(13, 3, true));
    }
}
