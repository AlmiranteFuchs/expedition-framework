package com.expeditions.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CompassPulseTest {

    private static final double RANGE = 10.0;

    @Test
    void pulsesWithinEitherAxis() {
        assertTrue(CompassPulse.pulses(5, 28, RANGE));   // close above, still within the horizon
        assertTrue(CompassPulse.pulses(0, 9, RANGE));    // directly under
        assertTrue(CompassPulse.pulses(9, 0, RANGE));    // beside it
    }

    @Test
    void silentWhenOffBothAxes() {
        assertFalse(CompassPulse.pulses(11, 11, RANGE));
        assertEquals(-1, CompassPulse.intervalTicks(15, 12, RANGE));
    }

    @Test
    void silentBeyondTheFarHorizon() {
        // Aligned on one axis but past range * FAR_RANGE_FACTOR total.
        assertFalse(CompassPulse.pulses(0, 31, RANGE));
        assertFalse(CompassPulse.pulses(31, 0, RANGE));
    }

    @Test
    void directlyAboveStillPulses() {
        // The needle already reads "here" in 2D — the pulse is what says
        // "but it's 25 blocks below". Slow, but felt.
        int interval = CompassPulse.intervalTicks(0, 25, RANGE);
        assertTrue(interval > 0);
        assertTrue(interval > CompassPulse.intervalTicks(5, 5, RANGE),
                "aligned-but-far must beat slower than near");
    }

    @Test
    void fasterAsItCloses() {
        int near = CompassPulse.intervalTicks(2, 0, RANGE);
        int mid = CompassPulse.intervalTicks(8, 0, RANGE);
        int far = CompassPulse.intervalTicks(0, 28, RANGE);
        assertTrue(near < mid && mid < far,
                "interval must shrink as distance shrinks");
        assertEquals(CompassPulse.MIN_INTERVAL_TICKS,
                CompassPulse.intervalTicks(0, 0, RANGE));
        assertTrue(CompassPulse.intervalTicks(0, 30, RANGE)
                <= CompassPulse.MAX_INTERVAL_TICKS);
    }

    @Test
    void pitchRisesAsItCloses() {
        assertTrue(CompassPulse.pitch(0, 0, RANGE)
                > CompassPulse.pitch(0, 29, RANGE));
    }
}
