package com.expeditions.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CompassRangeTest {
    @Test void closeSignalsNeverGiveExactDistances() {
        assertEquals(new CompassRange(0,32),CompassRange.estimate(0));
        assertEquals(CompassRange.estimate(1),CompassRange.estimate(31));
        assertEquals(new CompassRange(0,32),CompassRange.estimate(32));
    }
    @Test void expandingBandsRemainHonestAtBoundaries() {
        assertEquals(new CompassRange(32,64),CompassRange.estimate(32.01));
        assertEquals(new CompassRange(128,256),CompassRange.estimate(256));
        assertEquals(new CompassRange(256,512),CompassRange.estimate(274));
        for(int i=0;i<100000;i+=113) {
            var band=CompassRange.estimate(i);
            assertTrue(band.minimum()<=i && band.maximum()>=i);
        }
    }
    @Test void rejectsInvalidCoordinatesAndHandlesWorldScale() {
        assertThrows(IllegalArgumentException.class,()->CompassRange.estimate(Double.NaN));
        assertThrows(IllegalArgumentException.class,()->CompassRange.estimate(-1));
        assertThrows(IllegalArgumentException.class,()->CompassRange.estimate(Double.POSITIVE_INFINITY));
        assertTrue(CompassRange.estimate(85000000).maximum()>=85000000);
    }
}
