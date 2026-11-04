package com.expeditions.server;

import com.expeditions.registry.ModBlocks;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.levelgen.Heightmap;

/** One bounded, persisted archaeological find near spawn; never replaces a build. */
public final class FirstTrace {
    private FirstTrace() { }
    public static void seed(ServerLevel level,ExpeditionSavedData data){
        if(Boolean.getBoolean("expeditions.gameTestRun")||data.firstTraceAttempted())return;
        var found=findAndPlace(level,level.getSharedSpawnPos());data.recordFirstTrace(found);
    }
    public static BlockPos findAndPlace(ServerLevel level,BlockPos spawn){
        // At most 32 candidate patches. A difficult ocean spawn may have no safe land.
        for(int radius:new int[]{32,64,96,128})for(int i=0;i<8;i++){
            double angle=i*Math.PI/4;int x=spawn.getX()+(int)Math.round(Math.cos(angle)*radius),z=spawn.getZ()+(int)Math.round(Math.sin(angle)*radius);
            var center=level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,new BlockPos(x,0,z));
            var candidates=new java.util.ArrayList<BlockPos>();
            for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++){
                var p=center.offset(dx,0,dz);var floor=level.getBlockState(p.below());
                if(level.getWorldBorder().isWithinBounds(p)&&level.isEmptyBlock(p)&&floor.isFaceSturdy(level,p.below(),Direction.UP)
                        && (floor.is(BlockTags.DIRT)||floor.is(BlockTags.BASE_STONE_OVERWORLD))&&level.getBlockEntity(p.below())==null)candidates.add(p);
            }
            if(candidates.size()<12)continue;
            for(var p:candidates)level.setBlockAndUpdate(p,ModBlocks.DORMANT_LAYER.get().defaultBlockState().setValue(MultifaceBlock.getFaceProperty(Direction.DOWN),true));
            return candidates.get(candidates.size()/2);
        }
        return null;
    }
}
