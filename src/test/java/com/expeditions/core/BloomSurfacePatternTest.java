package com.expeditions.core;

import static org.junit.jupiter.api.Assertions.*;
import java.util.HashSet;
import org.junit.jupiter.api.Test;

class BloomSurfacePatternTest {
    @Test void materialSelectionSurvivesRevisits() {
        assertEquals(BloomSurfacePattern.at(-315,81,42),BloomSurfacePattern.at(-315,81,42));
    }
    @Test void horizontalNeighborsShareBothEdgeCorners() {
        for(int u=-40;u<40;u++) for(int v=-40;v<40;v++) {
            var a=BloomSurfacePattern.at(u,v,93); var b=BloomSurfacePattern.at(u+1,v,93);
            assertEquals(a.corner(1),b.corner(0)); assertEquals(a.corner(2),b.corner(3));
        }
    }
    @Test void verticalNeighborsShareBothEdgeCorners() {
        for(int u=-40;u<40;u++) for(int v=-40;v<40;v++) {
            var a=BloomSurfacePattern.at(u,v,93); var b=BloomSurfacePattern.at(u,v+1,93);
            assertEquals(a.corner(3),b.corner(0)); assertEquals(a.corner(2),b.corner(1));
        }
    }
    @Test void fourFacesAgreeOnTheirCommonCorner() {
        int common=BloomSurfacePattern.at(-1,-1,17).corner(2);
        assertEquals(common,BloomSurfacePattern.at(0,-1,17).corner(3));
        assertEquals(common,BloomSurfacePattern.at(-1,0,17).corner(1));
        assertEquals(common,BloomSurfacePattern.at(0,0,17).corner(0));
    }
    @Test void atlasAddressesAndResinFrequencyAreBounded() {
        int resin=0;
        for(int u=-50;u<50;u++) for(int v=-50;v<50;v++) {
            var tile=BloomSurfacePattern.at(u,v,19);
            assertTrue(tile.index()>=0 && tile.index()<64);
            if(tile.resin()) resin++;
        }
        assertTrue(resin>150 && resin<500,"Resin should remain rare, not occur on each face");
    }
    @Test void worldCoordinatesUseDiverseCompatibleMaterials() {
        var seen=new HashSet<Integer>();
        for(int u=-30;u<30;u++) for(int v=-30;v<30;v++) seen.add(BloomSurfacePattern.at(u,v,23).index());
        assertEquals(64,seen.size());
        assertNotEquals(BloomSurfacePattern.at(20,-20,1),BloomSurfacePattern.at(20,-20,2));
    }
}
