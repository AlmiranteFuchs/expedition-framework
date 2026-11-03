package com.expeditions.server;

import com.expeditions.block.ExpeditionExitBlock;
import com.expeditions.block.RecallTreePartBlock;
import com.expeditions.registry.ModBlocks;
import com.expeditions.server.dimension.DestinationChecks;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** One bounded, explicitly tracked hollow. No terrain replacement or chunk-spanning worldgen. */
public final class RecallTrees {
    private RecallTrees() { }
    public static final List<BlockPos> OFFSETS = List.of(new BlockPos(-1,0,0), new BlockPos(1,0,0),
            new BlockPos(-1,1,0), new BlockPos(1,1,0), new BlockPos(-1,2,0), new BlockPos(0,2,0),
            new BlockPos(1,2,0), new BlockPos(0,1,0));

    public static List<BlockPos> parts(BlockPos root) {
        return OFFSETS.stream().map(root::offset).toList();
    }

    public static boolean contains(BlockPos root, BlockPos pos) {
        return root.equals(pos) || OFFSETS.contains(pos.subtract(root));
    }

    public static boolean fits(ServerLevel level, BlockPos root) {
        for (BlockPos pos : java.util.stream.Stream.concat(java.util.stream.Stream.of(root), parts(root).stream()).toList()) {
            if (level.isOutsideBuildHeight(pos) || !level.getWorldBorder().isWithinBounds(pos)) return false;
            level.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
            if (!DestinationChecks.isFree(level, pos) || DestinationChecks.isHazard(level, pos)) return false;
        }
        for (int x = -1; x <= 1; x++) {
            BlockPos floor = root.offset(x,-1,0);
            if (!DestinationChecks.isSturdyFloor(level, floor) || DestinationChecks.isHazard(level, floor)) return false;
        }
        return true;
    }

    public static void place(ServerLevel level, BlockPos root, ExpeditionSavedData data) {
        String dimension = level.dimension().location().toString();
        level.setBlock(root, ModBlocks.EXPEDITION_EXIT.get().defaultBlockState().setValue(ExpeditionExitBlock.GROWN, true), 3);
        data.trackMarker(dimension, root);
        for (int i = 0; i < OFFSETS.size(); i++) {
            BlockPos pos = root.offset(OFFSETS.get(i));
            level.setBlock(pos, ModBlocks.RECALL_TREE_PART.get().defaultBlockState().setValue(RecallTreePartBlock.PART, i), 3);
            data.trackMarker(dimension, pos);
        }
    }

    public static boolean intact(ServerLevel level, BlockPos root) {
        var state = level.getBlockState(root);
        if (!state.is(ModBlocks.EXPEDITION_EXIT.get())) return false;
        if (!state.getValue(ExpeditionExitBlock.GROWN)) return true; // Existing compact exits remain valid.
        for (int i = 0; i < OFFSETS.size(); i++) {
            var part = level.getBlockState(root.offset(OFFSETS.get(i)));
            if (!part.is(ModBlocks.RECALL_TREE_PART.get()) || part.getValue(RecallTreePartBlock.PART) != i) return false;
        }
        return true;
    }
}
