package com.expeditions.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Boundary behaviour of the challenge roll — the pick itself is pure. */
class CheckpointChallengeTest {
    @Test
    void guaranteedChanceAlwaysHordes() {
        assertEquals(CheckpointChallenge.HORDE, CheckpointChallenge.roll(1.0, 0.0));
        assertEquals(CheckpointChallenge.HORDE, CheckpointChallenge.roll(1.0, 0.999));
    }

    @Test
    void zeroChanceNeverHordes() {
        assertEquals(CheckpointChallenge.NONE, CheckpointChallenge.roll(0.0, 0.0));
        assertEquals(CheckpointChallenge.NONE, CheckpointChallenge.roll(0.0, 0.999));
    }

    @Test
    void rollHonoursTheChanceBoundary() {
        assertEquals(CheckpointChallenge.HORDE, CheckpointChallenge.roll(0.5, 0.49));
        assertEquals(CheckpointChallenge.NONE, CheckpointChallenge.roll(0.5, 0.5));
    }
}
