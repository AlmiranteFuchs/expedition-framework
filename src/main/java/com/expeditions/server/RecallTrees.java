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
    public static final List<BlockPos> OFFSETS = RecallTreeLayout.OFFSETS;

    public static List<BlockPos> parts(BlockPos root) {
        return OFFSETS.subList(RecallTreeLayout.LEGACY_COUNT,OFFSETS.size()).stream().map(root::offset).toList();
    }
    public static List<BlockPos> allParts(BlockPos root) {return OFFSETS.stream().map(root::offset).distinct().toList();}

    public static boolean contains(BlockPos root, BlockPos pos) {
        return root.equals(pos) || OFFSETS.contains(pos.subtract(root));
    }

    public static boolean fits(ServerLevel level, BlockPos root) {
        var cells=new java.util.ArrayList<>(parts(root));cells.add(root);
        RecallTreeLayout.CLEARANCE.stream().map(root::offset).forEach(cells::add);
        for (BlockPos pos : cells) {
            if (level.isOutsideBuildHeight(pos) || !level.getWorldBorder().isWithinBounds(pos)) return false;
            level.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
            if (!DestinationChecks.isFree(level, pos) || DestinationChecks.isHazard(level, pos)) return false;
        }
        for (BlockPos offset : RecallTreeLayout.FLOORS) {
            BlockPos floor = root.offset(offset);
            if (!DestinationChecks.isSturdyFloor(level, floor) || DestinationChecks.isHazard(level, floor)) return false;
        }
        return true;
    }

    public static void place(ServerLevel level, BlockPos root, ExpeditionSavedData data) {
        String dimension = level.dimension().location().toString();
        level.setBlock(root, ModBlocks.EXPEDITION_EXIT.get().defaultBlockState().setValue(ExpeditionExitBlock.GROWN, true)
                .setValue(ExpeditionExitBlock.FULL_TREE,true), 3);
        data.trackMarker(dimension, root);
        for (int i = RecallTreeLayout.LEGACY_COUNT; i < OFFSETS.size(); i++) {
            BlockPos pos = root.offset(OFFSETS.get(i));
            level.setBlock(pos, ModBlocks.RECALL_TREE_PART.get().defaultBlockState().setValue(RecallTreePartBlock.PART, i), 3);
            data.trackMarker(dimension, pos);
        }
    }

    public static boolean intact(ServerLevel level, BlockPos root) {
        var state = level.getBlockState(root);
        if (!state.is(ModBlocks.EXPEDITION_EXIT.get())) return false;
        if (!state.getValue(ExpeditionExitBlock.GROWN)) return true; // Existing compact exits remain valid.
        int first=state.getValue(ExpeditionExitBlock.FULL_TREE)?RecallTreeLayout.LEGACY_COUNT:0;
        int end=first==0?RecallTreeLayout.LEGACY_COUNT:OFFSETS.size();
        for (int i = first; i < end; i++) {
            var part = level.getBlockState(root.offset(OFFSETS.get(i)));
            if (!part.is(ModBlocks.RECALL_TREE_PART.get()) || part.getValue(RecallTreePartBlock.PART) != i) return false;
        }
        return true;
    }
}
