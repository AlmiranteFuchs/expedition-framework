package com.expeditions.core;

import static org.junit.jupiter.api.Assertions.*;
import java.util.HashSet;
import org.junit.jupiter.api.Test;

class BloomSurfacePatternTest {
    @Test void samplesAreStableAcrossRepeatedVisits() {
        assertEquals(BloomSurfacePattern.overlapping(-315,81,42),
                BloomSurfacePattern.overlapping(-315,81,42));
    }

    @Test void sharedPatchesContinueAcrossPositiveAndNegativeBlockSeams() {
        int continued=0;
        for (int u=-40;u<40;u++) for (var p:BloomSurfacePattern.overlapping(u,-7,93)) {
            if (!p.covers(u+1,-7)) continue;
            assertTrue(BloomSurfacePattern.overlapping(u+1,-7,93).contains(p));
            continued++;
        }
        assertTrue(continued>40);
    }

    @Test void faceCoordinatesAlwaysRemainInsideTheirPatch() {
        for (int u=-70;u<70;u++) for (int v=-70;v<70;v++)
            for (var p:BloomSurfacePattern.overlapping(u,v,7)) {
                assertTrue(p.covers(u,v));
                assertTrue(u-p.u()>=0 && u-p.u()<BloomSurfacePattern.SIZE);
                assertTrue(v-p.v()>=0 && v-p.v()<BloomSurfacePattern.SIZE);
            }
    }

    @Test void overlapAndOpacityHaveBoundedRenderingCost() {
        for (int u=-60;u<60;u++) for (int v=-60;v<60;v++) {
            var patches=BloomSurfacePattern.overlapping(u,v,17);
            assertTrue(patches.size()<=4);
            for (var p:patches) {
                assertTrue(p.variant()>=0 && p.variant()<8);
                assertTrue(p.strength()>=.66f && p.strength()<=1);
            }
        }
    }

    @Test void colonyTemplatesVaryAcrossWorldCells() {
        var variants=new HashSet<Integer>();
        for (int i=-30;i<30;i++) variants.add(BloomSurfacePattern.at(i,4,17).variant());
        assertEquals(8,variants.size());
        assertNotEquals(BloomSurfacePattern.at(0,0,17).variant(),BloomSurfacePattern.at(16,0,17).variant());
    }

    @Test void DifferentPlanesDoNotCopyTheSameColonyLayout() {
        assertNotEquals(BloomSurfacePattern.overlapping(20,-20,1),
                BloomSurfacePattern.overlapping(20,-20,2));
    }
}
