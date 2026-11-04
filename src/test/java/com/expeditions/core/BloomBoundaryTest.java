package com.expeditions.core;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class BloomBoundaryTest {
    @Test void isolatedGrowthLeavesOriginalHostAtEveryOutsideEdge() {
        for(int variant=0;variant<4;variant++) for(int n=0;n<8;n++) {
            assertEquals(0,BloomBoundary.coverage(0,variant,n,0));
            assertEquals(0,BloomBoundary.coverage(0,variant,n,7));
            assertEquals(0,BloomBoundary.coverage(0,variant,0,n));
            assertEquals(0,BloomBoundary.coverage(0,variant,7,n));
        }
        assertEquals(1,BloomBoundary.coverage(0,0,3,3));
    }
    @Test void connectedInteriorsKeepTheirFullMaterial() {
        var regions=BloomBoundary.regions(255,0);
        assertEquals(1,regions.size());
        assertEquals(new BloomBoundary.Region(0,0,8,8,1),regions.getFirst());
    }
    @Test void missingDiagonalCutsOnlyTheConcaveCorner() {
        int missingNE=255^32;
        assertEquals(0,BloomBoundary.coverage(missingNE,0,7,0));
        assertEquals(1,BloomBoundary.coverage(missingNE,0,0,0));
        assertEquals(1,BloomBoundary.coverage(missingNE,0,3,3));
    }
    @Test void neighboringCoverageMatchesAtEverySharedEdge() {
        // Exhaust all 2x3 neighborhoods around an occupied horizontal pair.
        for(int bits=0;bits<64;bits++) {
            boolean nw=(bits&1)!=0,n=(bits&2)!=0,ne=(bits&4)!=0;
            boolean sw=(bits&8)!=0,s=(bits&16)!=0,se=(bits&32)!=0;
            int left=2|(nw?1:0)|(sw?4:0)|(n?32:0)|(s?64:0);
            int right=8|(n?1:0)|(s?4:0)|(nw?16:0)|(sw?128:0);
            // Far corners are irrelevant at this seam; vary them independently.
            left|=ne?8:0; right|=se?2:0;
            for(int a=0;a<4;a++) for(int b=0;b<4;b++) for(int y=0;y<8;y++)
                assertEquals(BloomBoundary.coverage(left,a,7,y),BloomBoundary.coverage(right,b,0,y));
        }
    }
    @Test void mergedRegionsReconstructEveryMaskWithoutOverlap() {
        for(int mask=0;mask<256;mask++) for(int variant=0;variant<4;variant++) {
            float[][] rebuilt=new float[8][8];
            for(var r:BloomBoundary.regions(mask,variant))
                for(int y=r.y0();y<r.y1();y++) for(int x=r.x0();x<r.x1();x++) {
                    assertEquals(0,rebuilt[y][x]); rebuilt[y][x]=r.alpha();
                }
            for(int y=0;y<8;y++) for(int x=0;x<8;x++)
                assertEquals(BloomBoundary.coverage(mask,variant,x,y),rebuilt[y][x]);
        }
    }
    @Test void frayedShapesVaryAndHavePartialCoverage() {
        assertNotEquals(BloomBoundary.regions(0,0),BloomBoundary.regions(0,1));
        assertTrue(BloomBoundary.regions(0,0).stream().anyMatch(r->r.alpha()>0 && r.alpha()<1));
    }
}
