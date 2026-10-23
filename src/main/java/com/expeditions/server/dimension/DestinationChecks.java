package com.expeditions.server.dimension;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.MagmaBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Shared safety predicates for expedition destinations. A spot is suitable
 * when the marker position and the space above it are free, the floor below
 * is sturdy, and none of the three blocks is hazardous — the same definition
 * of "standable" every adapter applies on real block state, never on
 * heightmap values alone.
 */
final class DestinationChecks {
    private DestinationChecks() {
    }

    /** The position itself can host a marker / let a player occupy it. */
    static boolean isFree(LevelReader level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return (state.isAir() || state.canBeReplaced()) && state.getFluidState().isEmpty();
    }

    /** The block below can be stood on. */
    static boolean isSturdyFloor(LevelReader level, BlockPos pos) {
        return level.getBlockState(pos).isFaceSturdy(level, pos, Direction.UP);
    }

    /** Standing in or on this block would hurt or trap a player. */
    static boolean isHazard(LevelReader level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return !state.getFluidState().isEmpty()
                || state.is(BlockTags.FIRE)
                || state.getBlock() instanceof MagmaBlock
                || state.is(Blocks.CACTUS)
                || state.is(Blocks.SWEET_BERRY_BUSH)
                || state.is(Blocks.WITHER_ROSE)
                || state.is(Blocks.POWDER_SNOW);
    }
}
