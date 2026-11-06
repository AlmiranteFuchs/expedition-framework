package com.expeditions.worldgen;

import com.expeditions.block.AssimilationLayerBlock;
import com.expeditions.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/** Patches of mature Bloom with occasional pale root crowns breaking through the shelf. */
public final class ShatterwakeBloomFeature extends Feature<NoneFeatureConfiguration> {
    private static final ResourceLocation SHATTERWAKE = ResourceLocation.parse("expeditions:shatterwake");

    public ShatterwakeBloomFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        var level = context.level();
        if (!level.getLevel().dimension().location().equals(SHATTERWAKE)) return false;
        var origin = context.origin();
        if (origin.getY() < 48 || origin.getY() > 192) return false;

        var random = context.random();
        var direction = Direction.Plane.HORIZONTAL.getRandomDirection(random);
        var across = direction.getClockWise();
        var chunk = new ChunkPos(origin);
        boolean crown = random.nextInt(4) == 0
                && inChunk(chunk, origin.relative(direction))
                && AssimilationLayerBlock.canAttachTo(level, origin.below(), Direction.UP)
                && level.isEmptyBlock(origin)
                && level.isEmptyBlock(origin.above())
                && level.isEmptyBlock(origin.above().relative(direction));
        if (crown) {
            var root = ModBlocks.STILLWOOD_ROOT.get().defaultBlockState();
            level.setBlock(origin, root, 2);
            level.setBlock(origin.above(), Blocks.OCHRE_FROGLIGHT.defaultBlockState(), 2);
            level.setBlock(origin.above().relative(direction),
                    root.setValue(RotatedPillarBlock.AXIS, direction.getAxis()), 2);
        }

        boolean placed = crown;
        for (int along = -4; along <= 4; along++) {
            for (int side = -2; side <= 2; side++) {
                if (along * along + 3 * side * side > 13 + random.nextInt(5)) continue;
                BlockPos column = origin.relative(direction, along).relative(across, side);
                if (!inChunk(chunk, column)) continue;
                BlockPos surface = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
                if (Math.abs(surface.getY() - origin.getY()) > 5) continue;

                boolean living = Math.abs(along) + Math.abs(side) < 5;
                placed |= coat(level, surface, Direction.DOWN, living);
                // Wrap down exposed ledges to join the horizontal film to the cliff.
                for (Direction face : Direction.Plane.HORIZONTAL) {
                    BlockPos edge = surface.below().relative(face);
                    if (inChunk(chunk, edge)) placed |= coat(level, edge, face.getOpposite(), living);
                }
            }
        }
        return placed;
    }

    private static boolean inChunk(ChunkPos chunk, BlockPos pos) {
        return chunk.x == (pos.getX() >> 4) && chunk.z == (pos.getZ() >> 4);
    }

    static boolean coat(WorldGenLevel level, BlockPos pos, Direction support, boolean living) {
        if (level.isOutsideBuildHeight(pos)) return false;
        var layer = living ? ModBlocks.ASSIMILATION_LAYER.get() : ModBlocks.DORMANT_LAYER.get();
        BlockState state = level.getBlockState(pos);
        if ((!state.isAir() && !state.is(layer))
                || !layer.isValidStateForPlacement(level, state, pos, support)) return false;
        var faces = state.is(layer) ? state : layer.defaultBlockState();
        return level.setBlock(pos, faces.setValue(MultifaceBlock.getFaceProperty(support), true), 2);
    }
}
