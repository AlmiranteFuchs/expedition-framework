package com.expeditions.core;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ResonancePowerTest {
    @Test void casualModeOnlyChangesSourcePowerAndIsReduced(){
        for(var kind:new ResonancePower.Kind[]{ResonancePower.Kind.DORMANT,ResonancePower.Kind.LIVING,ResonancePower.Kind.SEVERED})
            assertEquals(10,ResonancePower.output(kind,false,100,false,false,true));
        assertEquals(0,ResonancePower.output(ResonancePower.Kind.CONDENSER,false,1,false,false,true));
        assertEquals(0,ResonancePower.output(ResonancePower.Kind.SEVERED,true,1,true,false,false));
    }
    @Test void dormantRequiresQuietGround(){assertEquals(20,ResonancePower.output(ResonancePower.Kind.DORMANT,true,1,false,false));assertEquals(0,ResonancePower.output(ResonancePower.Kind.DORMANT,false,1,true,true));}
    @Test void nearbyCollectorsShareTheirField(){assertEquals(10,ResonancePower.output(ResonancePower.Kind.DORMANT,true,2,false,false));assertEquals(5,ResonancePower.output(ResonancePower.Kind.DORMANT,true,4,false,false));assertEquals(0,ResonancePower.output(ResonancePower.Kind.DORMANT,true,21,false,false));}
    @Test void livingSourceNeedsFuelButNotQuietGround(){assertEquals(0,ResonancePower.output(ResonancePower.Kind.LIVING,true,1,false,true));assertEquals(80,ResonancePower.output(ResonancePower.Kind.LIVING,false,20,true,false));}
    @Test void severedSourceNeedsProof(){assertEquals(0,ResonancePower.output(ResonancePower.Kind.SEVERED,true,1,true,false));assertEquals(120,ResonancePower.output(ResonancePower.Kind.SEVERED,false,30,false,true));}
    @Test void livingFuelHasPositiveButBoundedReturn(){assertEquals(48000,ResonancePower.LIVING_FUEL_TICKS*80);assertTrue(48000>ResonanceProcessing.ESSENCE_ENERGY);}
    @Test void starterOutputMatchesRefiningDemand(){assertEquals(ResonanceProcessing.ESSENCE_ENERGY,20*ResonanceProcessing.PROCESS_TICKS);assertEquals(0,ResonancePower.output(ResonancePower.Kind.CONDENSER,true,1,true,true));}
}
