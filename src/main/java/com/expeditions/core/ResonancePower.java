package com.expeditions.core;
/** Distinct sources share the FE transport contract, not a second incompatible energy currency. */
public final class ResonancePower {
    public enum Kind { DORMANT, LIVING, SEVERED, CONDENSER }
    public static final int LIVING_FUEL_TICKS=600;
    private ResonancePower() { }
    public static int output(Kind kind,boolean sheltered,int neighbors,boolean fueled,boolean proven,boolean simple){
        return simple&&kind!=Kind.CONDENSER?10:output(kind,sheltered,neighbors,fueled,proven);
    }
    public static int output(Kind kind,boolean sheltered,int neighbors,boolean fueled,boolean proven){
        return switch(kind){case DORMANT->sheltered?20/Math.max(1,neighbors):0;case LIVING->fueled?80:0;case SEVERED->proven?120:0;case CONDENSER->0;};
    }
}
