package com.expeditions.core;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class InfectionProgressTest {
    @Test void proximityWithoutContactCannotInfect() {
        assertEquals(0,InfectionProgress.step(0,false,false,5,1,100,0));
    }
    @Test void physicalExposureAccruesOncePerCadence() {
        assertEquals(5,InfectionProgress.step(0,true,false,5,1,100,0));
    }
    @Test void disruptionBlocksExposureButDoesNotCureExistingInfection() {
        assertEquals(39,InfectionProgress.step(40,true,true,5,1,100,0));
        assertEquals(0,InfectionProgress.step(0,true,true,5,1,100,0));
    }
    @Test void convertedMobKeepsItsInfectedVariant() {
        assertEquals(25,InfectionProgress.step(25,false,false,5,1,100,25));
    }
    @Test void valuesSaturateAndMalformedSavedValuesRecoverSafely() {
        assertEquals(100,InfectionProgress.step(99,true,false,5,1,100,0));
        assertEquals(0,InfectionProgress.step(Double.NaN,false,false,5,1,100,0));
        assertEquals(0,InfectionProgress.step(-10,false,false,5,1,100,0));
    }
    @Test void invalidTuningIsRejected() {
        assertThrows(IllegalArgumentException.class,()->InfectionProgress.step(0,true,false,-1,0,100,0));
        assertThrows(IllegalArgumentException.class,()->InfectionProgress.step(0,true,false,1,0,100,101));
    }
}
