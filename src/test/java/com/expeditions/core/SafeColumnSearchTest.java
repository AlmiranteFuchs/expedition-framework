package com.expeditions.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.OptionalInt;
import java.util.function.IntPredicate;
import org.junit.jupiter.api.Test;

/**
 * The standable-column search behind the Nether adapter, exercised on
 * synthetic columns so the safety rules are testable without a world.
 */
class SafeColumnSearchTest {
    private static final IntPredicate ALWAYS = y -> true;
    private static final IntPredicate NEVER = y -> false;

    /**
     * Builds predicates from a column description: which y are solid (sturdy
     * floor), merely blocking (occupy space but cannot be stood on, like a
     * bottom slab or fence), or hazardous. Everything else is free.
     */
    private static OptionalInt search(int[] solidYs, int[] blockingYs, int[] hazardYs,
            int top, int bottom) {
        IntPredicate isFree = y -> !contains(solidYs, y) && !contains(blockingYs, y)
                && !contains(hazardYs, y);
        IntPredicate isSolid = y -> contains(solidYs, y);
        IntPredicate isHazard = y -> contains(hazardYs, y);
        return SafeColumnSearch.findStandingY(isFree, isSolid, isHazard, top, bottom);
    }

    private static OptionalInt search(int[] solidYs, int[] hazardYs, int top, int bottom) {
        return search(solidYs, new int[]{}, hazardYs, top, bottom);
    }

    private static boolean contains(int[] ys, int y) {
        for (int v : ys) {
            if (v == y) {
                return true;
            }
        }
        return false;
    }

    @Test
    void findsFloorWithHeadroom() {
        // Solid floor at 64, everything above free -> stand at 65.
        OptionalInt y = search(new int[]{64}, new int[]{}, 80, 40);
        assertTrue(y.isPresent());
        assertEquals(65, y.getAsInt());
    }

    @Test
    void prefersHighestStandingSpot() {
        // Two floors: at 64 and at 50 -> the higher one wins.
        OptionalInt y = search(new int[]{64, 50}, new int[]{}, 80, 40);
        assertEquals(65, y.getAsInt());
    }

    @Test
    void skipsSpotBlockedOverhead() {
        // Floor at 64 but a non-sturdy block occupies 66 -> no headroom over
        // 64's floor, and 66 is no floor either; falls to the floor at 50.
        OptionalInt y = search(new int[]{64, 50}, new int[]{66}, new int[]{}, 80, 40);
        assertEquals(51, y.getAsInt());
    }

    @Test
    void standsOnTopOfASolidObstacle() {
        // A solid block at 66 over floor 64 is itself a floor: stand at 67.
        OptionalInt y = search(new int[]{64, 66}, new int[]{}, 80, 40);
        assertEquals(67, y.getAsInt());
    }

    @Test
    void skipsHazardousFloor() {
        // Lava on top of the floor at 64 (hazard at 65), safe floor at 50.
        OptionalInt y = search(new int[]{64, 50}, new int[]{65}, 80, 40);
        assertEquals(51, y.getAsInt());
    }

    @Test
    void skipsHazardAboveCandidate() {
        // Fire floating at 66 makes standing at 65 unsafe.
        OptionalInt y = search(new int[]{64, 50}, new int[]{66}, 80, 40);
        assertEquals(51, y.getAsInt());
    }

    @Test
    void emptyWhenNoFloorInRange() {
        assertTrue(search(new int[]{}, new int[]{}, 80, 40).isEmpty());
    }

    @Test
    void emptyWhenColumnIsAllLava() {
        // A lava pool from 60 up to 70 over floor at 59 (outside headroom of
        // any candidate above the pool anyway) -> nothing safe.
        int[] hazards = new int[11];
        for (int i = 0; i < hazards.length; i++) {
            hazards[i] = 60 + i;
        }
        assertTrue(search(new int[]{59}, hazards, 80, 62).isEmpty());
    }

    @Test
    void respectsScanBounds() {
        // Floor just below the scan window must not be found.
        assertTrue(search(new int[]{30}, new int[]{}, 80, 40).isEmpty());
        // Floor exactly at the bottom bound is still usable (feet at bottom+1).
        OptionalInt y = search(new int[]{40}, new int[]{}, 80, 40);
        assertEquals(41, y.getAsInt());
    }
}
