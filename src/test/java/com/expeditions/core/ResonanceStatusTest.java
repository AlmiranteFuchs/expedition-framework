package com.expeditions.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.expeditions.core.ResonanceStatus.*;
import static com.expeditions.core.ResonancePower.Kind.*;

class ResonanceStatusTest {
    @Test void reservedSampleResumesWithoutAnotherInput(){assertEquals(REFINING,condenser(20,true,false,true,false));}
    @Test void nextStepNeedsItsWholeEnergyPayment(){assertEquals(NO_POWER,condenser(19,true,false,true,false));assertEquals(REFINING,condenser(20,true,false,true,false));}
    @Test void outputBackpressureExplainsWhyPowerCannotHelp(){assertEquals(OUTPUT_BLOCKED,condenser(16000,true,true,false,false));}
    @Test void emptyWorkshopAsksForASampleFirst(){assertEquals(NO_SAMPLE,condenser(0,false,false,true,false));}
    @Test void deliberateBreachHasItsOwnOperatingState(){assertEquals(BREACH_OPEN,condenser(4000,true,true,true,true));}
    @Test void fullLivingSourceDoesNotAskForAnotherFuelPayment(){assertEquals(BUFFER_FULL,source(LIVING,16000,false,true,false,false,false,1));}
    @Test void progressionProofAndPresenceAreDistinct(){assertEquals(CORE_MISSING,source(SEVERED,0,false,true,false,false,false,1));assertEquals(CORE_UNBOUND,source(SEVERED,0,false,true,false,true,false,1));}
    @Test void dormantFieldAndRedstoneReasonsAreExplicit(){assertEquals(QUIET_GROUND_REQUIRED,source(DORMANT,0,false,false,false,false,false,1));assertEquals(REDSTONE_PAUSED,source(DORMANT,0,true,true,false,false,false,1));assertEquals(FIELD_CROWDED,source(DORMANT,0,false,true,false,false,false,21));}
    @Test void fillSignalIsBoundedAndReachesFull(){assertEquals(0,fillSignal(-1,64));assertEquals(0,fillSignal(1,0));assertEquals(1,fillSignal(1,64));assertEquals(8,fillSignal(32,64));assertEquals(15,fillSignal(64,64));assertEquals(15,fillSignal(Integer.MAX_VALUE,16000));}
}
