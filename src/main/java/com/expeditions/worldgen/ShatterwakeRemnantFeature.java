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
        var skyStone = material("sky_stone", Blocks.CALCITE.defaultBlockState());
        var ruins = material("forest_rubble", Blocks.MOSSY_COBBLESTONE.defaultBlockState());
        var comb = material("comb", Blocks.HONEYCOMB_BLOCK.defaultBlockState());
        var casing = material("foundry_rubble", Blocks.ANDESITE.defaultBlockState());
        var side = context.random().nextBoolean() ? Direction.EAST : Direction.SOUTH;
        return switch (biome) {
            case "pale_drift" -> {
                var log = material("skyroot", bark);
                yield column(level, origin, ruins, skyStone, log, skyStone, ember)
                        | column(level, origin.relative(side), ruins, casing, log, bark)
                        | column(level, origin.relative(side, 2), skyStone, casing, log)
                        | column(level, origin.relative(side.getOpposite()), skyStone, ruins, bark);
            }
            case "buried_canopy" -> {
                yield column(level, origin, ruins, ruins, skyStone, bark, bark)
                        | column(level, origin.relative(side), ruins, comb, bark)
                        | column(level, origin.relative(side, 3), casing, ruins, bark, ember)
                        | column(level, origin.relative(side.getClockWise()), ruins, skyStone);
            }
            case "amber_reef" -> {
                var wax = material("wax", Blocks.TERRACOTTA.defaultBlockState());
                yield column(level, origin, ruins, wax, comb, comb, bark)
                        | column(level, origin.relative(side), casing, comb, wax, ember)
                        | column(level, origin.relative(side, 2), skyStone, wax, comb)
                        | column(level, origin.relative(side.getClockWise()), ruins, comb, bark);
            }
            case "cinder_shards" -> {
                var basalt = Blocks.SMOOTH_BASALT.defaultBlockState();
                yield column(level, origin, basalt, casing, skyStone, casing, bark, ember)
                        | column(level, origin.relative(side), basalt, casing, ruins)
                        | column(level, origin.relative(side, 2), ruins, basalt, casing)
                        | column(level, origin.relative(side.getClockWise()), basalt, comb, bark);
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
