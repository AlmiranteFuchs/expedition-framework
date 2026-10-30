package com.expeditions.core;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class CompassElevationTest {
    @Test
    void distinguishesAboveAndBelow() {
        assertEquals(1, CompassElevation.direction(12));
        assertEquals(-1, CompassElevation.direction(-12));
        assertTrue(CompassElevation.pull(12) > 0);
        assertTrue(CompassElevation.pull(-12) < 0);
    }

    @Test
    void walkableHeightDifferencesSettleAtTheNeedle() {
        for (double gap : new double[]{-2, -1, 0, 1, 2}) {
            assertEquals(0, CompassElevation.direction(gap));
            assertEquals(0, CompassElevation.pull(gap));
        }
    }

    @Test
    void pullStrengthIncreasesWithoutLeavingTheCompass() {
        assertTrue(CompassElevation.pull(3) >= 0.35);
        assertTrue(CompassElevation.pull(24) > CompassElevation.pull(3));
        assertEquals(1, CompassElevation.pull(320));
        assertEquals(-1, CompassElevation.pull(-320));
    }

    @Test
    void risingAndFallingUseSymmetricStrength() {
        for (double gap : new double[]{3, 12, 24, 64}) {
            assertEquals(CompassElevation.pull(gap), -CompassElevation.pull(-gap));
        }
    }
}
