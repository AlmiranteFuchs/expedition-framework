package com.expeditions.core;

/** Operating reasons shared by server instruments and their read-only displays. */
public enum ResonanceStatus {
    NO_SAMPLE, NO_POWER, OUTPUT_BLOCKED, REFINING, BREACH_OPEN,
    GATHERING, BUFFER_FULL, REDSTONE_PAUSED, QUIET_GROUND_REQUIRED, NO_FUEL,
    CORE_MISSING, CORE_UNBOUND, FIELD_CROWDED, SIMPLE_GATHERING;

    public String translationKey(){return "machine.expeditions.state."+name().toLowerCase(java.util.Locale.ROOT);}
    public static ResonanceStatus byId(int id){return id>=0&&id<values().length?values()[id]:NO_POWER;}
    public static ResonanceStatus condenser(int energy,boolean reserved,boolean sample,boolean outputRoom,boolean breach){
        if(breach)return BREACH_OPEN;
        if(!outputRoom)return OUTPUT_BLOCKED;
        if(!reserved&&!sample)return NO_SAMPLE;
        return energy<ResonanceProcessing.energyPerTick()?NO_POWER:REFINING;
    }
    public static ResonanceStatus source(ResonancePower.Kind kind,int energy,boolean paused,boolean quiet,
            boolean fueled,boolean core,boolean proven,int collectors){
        if(paused)return REDSTONE_PAUSED;
        if(kind==ResonancePower.Kind.DORMANT&&!quiet)return QUIET_GROUND_REQUIRED;
        if(kind==ResonancePower.Kind.SEVERED&&!core)return CORE_MISSING;
        if(kind==ResonancePower.Kind.SEVERED&&!proven)return CORE_UNBOUND;
        if(energy>=ResonanceProcessing.CAPACITY)return BUFFER_FULL;
        if(kind==ResonancePower.Kind.LIVING&&!fueled)return NO_FUEL;
        if(kind==ResonancePower.Kind.DORMANT&&collectors>20)return FIELD_CROWDED;
        return GATHERING;
    }
    /** Vanilla-style 0/1..15 fill signal, including the full 15 endpoint. */
    public static int fillSignal(int amount,int capacity){
        if(amount<=0||capacity<=0)return 0;
        return 1+(int)(14L*Math.min(amount,capacity)/capacity);
    }
}
