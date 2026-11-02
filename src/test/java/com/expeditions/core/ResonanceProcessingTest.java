package com.expeditions.core;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ResonanceProcessingTest {
    @Test void standaloneFuelCoversExactlyOneEssenceBatch(){assertEquals(ResonanceProcessing.FUEL_ENERGY,ResonanceProcessing.PROCESS_TICKS*ResonanceProcessing.energyPerTick());}
    @Test void invalidSavedEnergyCannotWrapOrExceedCapacity(){assertEquals(0,ResonanceProcessing.restoredEnergy(-1));assertEquals(16000,ResonanceProcessing.restoredEnergy(Integer.MAX_VALUE));}
    @Test void savedProgressRequiresPaidFeedstock(){assertEquals(0,ResonanceProcessing.restoredProgress(199,false));assertEquals(199,ResonanceProcessing.restoredProgress(Integer.MAX_VALUE,true));assertEquals(0,ResonanceProcessing.restoredProgress(-4,true));}
}
