package com.expeditions.worldgen;

import com.expeditions.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/** Small exposed fragments of architecture and material caught in the Bloom. */
public final class ShatterwakeRemnantFeature extends Feature<NoneFeatureConfiguration> {
    private static final ResourceLocation SHATTERWAKE = ResourceLocation.parse("expeditions:shatterwake");

    public ShatterwakeRemnantFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        var level = context.level();
        if (!level.getLevel().dimension().location().equals(SHATTERWAKE)) return false;
        var origin = context.origin();
        if (origin.getY() < 48 || origin.getY() > 192) return false;
        var biome = level.getBiome(origin).unwrapKey().orElseThrow().location().getPath();
        var bark = ModBlocks.STILLWOOD_ROOT.get().defaultBlockState();
        var ember = Blocks.OCHRE_FROGLIGHT.defaultBlockState();
        var side = context.random().nextBoolean() ? Direction.EAST : Direction.SOUTH;
        return switch (biome) {
            case "pale_drift" -> {
                var stone = material("sky_stone", Blocks.CALCITE.defaultBlockState());
                var log = material("skyroot", bark);
                yield column(level, origin, stone, stone, log, stone, ember)
                        | column(level, origin.relative(side, 2), stone, log, stone);
            }
            case "buried_canopy" -> {
                var stone = material("forest_rubble", Blocks.MOSSY_COBBLESTONE.defaultBlockState());
                yield column(level, origin, stone, stone, stone, bark, bark)
                        | column(level, origin.relative(side, 1), stone, stone, bark)
                        | column(level, origin.relative(side, 3), stone, bark, ember);
            }
            case "amber_reef" -> {
                var comb = material("comb", Blocks.HONEYCOMB_BLOCK.defaultBlockState());
                var wax = material("wax", Blocks.YELLOW_TERRACOTTA.defaultBlockState());
                yield column(level, origin, bark, wax, comb, comb, bark)
                        | column(level, origin.relative(side, 1), bark, comb, wax, ember)
                        | column(level, origin.relative(side, 2), bark, wax, comb);
            }
            case "cinder_shards" -> {
                var stone = material("foundry_rubble", Blocks.ANDESITE.defaultBlockState());
                var basalt = Blocks.SMOOTH_BASALT.defaultBlockState();
                yield column(level, origin, basalt, stone, stone, bark, ember)
                        | column(level, origin.relative(side, 2), basalt, basalt, stone);
            }
            default -> false;
        };
    }

    private static BlockState material(String name, BlockState fallback) {
        return EchoMaterialRule.resolve(ResourceLocation.parse("expeditions:shatterwake/" + name), fallback);
    }

    private static boolean column(WorldGenLevel level, BlockPos anchor, BlockState... states) {
        BlockPos start = level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE_WG, anchor);
        if (Math.abs(start.getY() - anchor.getY()) > 3 || level.isOutsideBuildHeight(start.above(states.length))
                || !level.getBlockState(start.below()).isFaceSturdy(level, start.below(), Direction.UP)) return false;
        for (int i = 0; i < states.length; i++) {
            if (!level.isEmptyBlock(start.above(i))) return false;
        }
        for (int i = 0; i < states.length; i++) level.setBlock(start.above(i), states[i], 2);
        return true;
    }
}
