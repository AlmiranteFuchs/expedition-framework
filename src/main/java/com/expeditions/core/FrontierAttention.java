package com.expeditions.core;

/** Exposure is personal; liberation is world-wide. Time here is in effective seconds. */
public final class FrontierAttention {
    public enum Stage { QUIET, NOTICED, HUNTED, OVERRUN, RECKONING }
    private FrontierAttention() { }
    public static int step(int seconds,int delta,int limit) {
        if(limit<10) throw new IllegalArgumentException("Attention limit must be at least ten seconds");
        return (int)Math.clamp((long)Math.max(0,seconds)+delta,0L,limit);
    }
    public static Stage stage(int seconds,int limit) {
        if(limit<10) throw new IllegalArgumentException("Attention limit must be at least ten seconds");
        if(seconds>=limit) return Stage.RECKONING;
        if(seconds>=limit*7L/10) return Stage.OVERRUN;
        if(seconds>=limit*4L/10) return Stage.HUNTED;
        if(seconds>=limit*2L/10) return Stage.NOTICED;
        return Stage.QUIET;
    }
    public static int nextThreshold(int seconds,int limit) {
        return switch(stage(seconds,limit)) {
            case QUIET -> limit*2/10;
            case NOTICED -> limit*4/10;
            case HUNTED -> limit*7/10;
            case OVERRUN,RECKONING -> limit;
        };
    }
}
