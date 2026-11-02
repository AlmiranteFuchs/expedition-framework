package com.expeditions.server;
import com.expeditions.block.entity.ResonanceMachineBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
/** The Overworld is sheltered by default. Only a deliberately powered local breach makes live Bloom possible. */
public final class Stillness {
    private static volatile boolean suppressed;
    private Stillness() { }
    public static void suppressForTests(boolean value) { suppressed=value && Boolean.getBoolean("expeditions.gameTestRun"); }
    public static boolean shelters(ServerLevel level) { return !suppressed && level.dimension().equals(Level.OVERWORLD); }
    public static boolean hasBreach(ServerLevel level) {
        if(!shelters(level))return false;
        for(BlockPos p:ExpeditionManager.get(level.getServer()).savedData().stillnessBreaches())
            if(active(level,p))return true;
        return false;
    }
    private static boolean active(ServerLevel level,BlockPos p) {
        return level.hasChunkAt(p) && level.getBlockEntity(p) instanceof ResonanceMachineBlockEntity be && be.breaching();
    }
    public static boolean suppresses(ServerLevel level,BlockPos pos) {
        if(!shelters(level))return false;
        for(BlockPos p:ExpeditionManager.get(level.getServer()).savedData().stillnessBreaches())
            if(p.distSqr(pos)<=24*24 && active(level,p))return false;
        return true;
    }
}
