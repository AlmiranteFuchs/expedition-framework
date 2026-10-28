package com.expeditions.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PulseWaveTest {

    @Test
    void lubIsTheStrongestBeat() {
        double lub = PulseWave.amplitude(0.12);
        double dub = PulseWave.amplitude(0.30);
        assertTrue(lub > dub, "the systole thump must beat harder than its echo");
        assertTrue(dub > PulseWave.THRUM * 3,
                "the second beat must still read as a beat, got " + dub);
    }

    @Test
    void restsBetweenBeats() {
        assertTrue(PulseWave.amplitude(0.7) < PulseWave.THRUM * 2,
                "the late cycle must rest near baseline");
    }

    @Test
    void amplitudeIsBounded() {
        for (double p = 0; p < 1.0; p += 0.005) {
            double a = PulseWave.amplitude(p);
            assertTrue(a >= PulseWave.THRUM - 1e-6 && a <= 1.0,
                    "amplitude out of range at " + p + ": " + a);
        }
    }

    @Test
    void waveTravelsFromTheHeart() {
        // A cell 5 blocks out beats exactly like the heart did
        // 5 * TICKS_PER_BLOCK ticks ago.
        long seed = 0xC0FFEE;
        for (double t = 0; t < 200; t += 7.3) {
            assertEquals(
                    PulseWave.amplitudeAt(t, 0, seed),
                    PulseWave.amplitudeAt(t + 5 * PulseWave.TICKS_PER_BLOCK, 5, seed),
                    1e-9, "distance must delay, not distort, the wave at " + t);
        }
    }

    @Test
    void coloniesHaveTheirOwnTempo() {
        int distinct = 0;
        for (long seed = 0; seed < 64; seed++) {
            distinct |= 1 << (PulseWave.periodTicks(seed) - PulseWave.MIN_PERIOD_TICKS);
        }
        assertTrue(Integer.bitCount(distinct) > 3,
                "tempos must vary across colonies");
        for (long seed = -100; seed < 100; seed++) {
            int period = PulseWave.periodTicks(seed);
            assertTrue(period >= PulseWave.MIN_PERIOD_TICKS
                    && period <= PulseWave.MAX_PERIOD_TICKS,
                    "period out of range: " + period);
            double offset = PulseWave.phaseOffset(seed);
            assertTrue(offset >= 0 && offset < 1.0,
                    "phase offset out of range: " + offset);
        }
    }
}
