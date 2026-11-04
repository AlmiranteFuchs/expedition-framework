package com.expeditions.worldgen;
import com.expeditions.registry.ModBlocks;
import com.mojang.serialization.Codec;
import net.minecraft.core.*;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.levelgen.feature.*;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
/** Small cold archaeological traces, never live hearts or infection, in natural Overworld terrain. */
public final class DormantScarFeature extends Feature<NoneFeatureConfiguration> {
    private static volatile boolean suppressed;
    public static void suppressForTests(boolean value){suppressed=value && Boolean.getBoolean("expeditions.gameTestRun");}
    public DormantScarFeature(Codec<NoneFeatureConfiguration> codec) { super(codec); }
    @Override public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        if(suppressed)return false;
        var level=context.level();if(!level.getLevel().dimension().equals(Level.OVERWORLD))return false;
        int placed=0;var origin=context.origin();
        for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++) {
            if(x*x+z*z>10 || context.random().nextInt(3)==0)continue;
            var start=origin.offset(x,4,z);
            for(int dy=0;dy<36;dy++) {
                var p=start.below(dy);if(p.getY()<=level.getMinBuildHeight())break;
                var floor=level.getBlockState(p.below());
                if(level.isEmptyBlock(p) && floor.isFaceSturdy(level,p.below(),Direction.UP)
                        && (floor.is(BlockTags.DIRT)||floor.is(BlockTags.BASE_STONE_OVERWORLD)||floor.is(BlockTags.LOGS))) {
                    level.setBlock(p,ModBlocks.DORMANT_LAYER.get().defaultBlockState().setValue(MultifaceBlock.getFaceProperty(Direction.DOWN),true),2);
                    placed++;break;
                }
            }
        }
        return placed>0;
    }
}
