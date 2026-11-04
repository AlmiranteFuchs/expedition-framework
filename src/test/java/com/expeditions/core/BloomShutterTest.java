package com.expeditions.core;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class BloomShutterTest {
    @Test void dormantNeverOpens(){for(int t=0;t<1000;t++)assertEquals(0,BloomShutter.stage(t,42,false));}
    @Test void livingCycleUsesEveryPose(){var seen=new java.util.HashSet<Integer>();for(int t=0;t<500;t++){int pose=BloomShutter.stage(t,42,true);assertTrue(pose>=0&&pose<=4);seen.add(pose);}assertEquals(java.util.Set.of(0,1,2,3,4),seen);}
    @Test void motionAdvancesThroughNeighboringPoses(){for(long seed:new long[]{0,42,Long.MIN_VALUE,Long.MAX_VALUE})for(int t=1;t<1000;t++)assertTrue(Math.abs(BloomShutter.stage(t,seed,true)-BloomShutter.stage(t-1,seed,true))<=1);}
    @Test void cycleIsStableAcrossReloads(){long seed=621;int period=PulseWave.periodTicks(seed)*3;for(int t=0;t<period;t++)assertEquals(BloomShutter.stage(t,seed,true),BloomShutter.stage(t+period,seed,true));}
}
