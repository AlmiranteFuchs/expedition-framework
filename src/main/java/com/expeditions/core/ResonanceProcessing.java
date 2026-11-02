package com.expeditions.core;
/** One flake fuels one batch; another is the condenser's feedstock. No machine recipe needs essence. */
public final class ResonanceProcessing {
    public static final int CAPACITY=16000,FUEL_ENERGY=4000,ESSENCE_ENERGY=4000,PROCESS_TICKS=200;
    private ResonanceProcessing() { }
    public static int restoredEnergy(int value) { return Math.clamp(value,0,CAPACITY); }
    public static int restoredProgress(int value,boolean reserved) { return reserved?Math.clamp(value,0,PROCESS_TICKS-1):0; }
    public static int energyPerTick() { return ESSENCE_ENERGY/PROCESS_TICKS; }
}
