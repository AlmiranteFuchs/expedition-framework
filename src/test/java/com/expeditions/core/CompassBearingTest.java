package com.expeditions.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CompassBearingTest {
    @Test void directionsMatchThePlayersView() {
        assertEquals(0, CompassBearing.turn(0, 1, 0), 1e-9);
        assertEquals(90, CompassBearing.turn(-1, 0, 0));
        assertEquals(-90, CompassBearing.turn(1, 0, 0));
        assertEquals(0, CompassBearing.turn(1, 0, -90));
    }

    @Test void rotationsWrapAcrossNorthAndHandleStraightUpOrDown() {
        assertEquals(1, CompassBearing.turn(0, -1, 179));
        assertEquals(-1, CompassBearing.turn(0, -1, -179));
        assertEquals(0, CompassBearing.turn(0, 0, 270));
    }
}
