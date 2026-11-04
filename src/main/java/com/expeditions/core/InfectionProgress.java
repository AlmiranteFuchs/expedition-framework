package com.expeditions.core;

/** One exposure cadence. Physical contact is required; protection always wins. */
public final class InfectionProgress {
    private InfectionProgress() { }
    public static double step(double current,boolean contact,boolean protectedHost,
            double accrual,double decay,double maximum,double retainedFloor) {
        if(!Double.isFinite(accrual) || accrual<0 || !Double.isFinite(decay) || decay<0
                || !Double.isFinite(maximum) || maximum<=0 || !Double.isFinite(retainedFloor)
                || retainedFloor<0 || retainedFloor>maximum) throw new IllegalArgumentException("Invalid infection tuning");
        current=Double.isFinite(current)?Math.clamp(current,0,maximum):0;
        return contact && !protectedHost?Math.min(maximum,current+accrual)
                :Math.max(retainedFloor,Math.max(0,current-decay));
    }
}
