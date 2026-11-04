package com.expeditions.core;

/** Slow staggered wooden shutters; a colony signal, not an anatomical heartbeat. */
public final class BloomShutter {
    private BloomShutter() { }
    public static int stage(double time,long seed,boolean active){
        if(!active)return 0;
        double phase=time/(PulseWave.periodTicks(seed)*3.0)+PulseWave.phaseOffset(seed);
        phase-=Math.floor(phase);
        if(phase<.12)return 0;
        if(phase<.18)return 1;
        if(phase<.24)return 2;
        if(phase<.30)return 3;
        if(phase<.50)return 4;
        if(phase<.56)return 3;
        if(phase<.62)return 2;
        if(phase<.68)return 1;
        return 0;
    }
}
