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
        return OFFSETS.subList(RecallTreeLayout.AUTHORED_START,OFFSETS.size()).stream().map(root::offset).toList();
    }
    public static List<BlockPos> allParts(BlockPos root) {return OFFSETS.stream().map(root::offset).distinct().toList();}

    public static boolean contains(BlockPos root, BlockPos pos) {
        return root.equals(pos) || RecallTreeLayout.COVERAGE.contains(pos.subtract(root));
    }
    public static boolean contains(BlockPos root,BlockPos pos,java.util.Optional<java.util.Set<Integer>> manifest,ServerLevel level) {
        if(root.equals(pos))return true;
        if(manifest.isEmpty()) {
            if(level==null)return false;
            var state=level.getBlockState(pos);
            if(!state.is(ModBlocks.RECALL_TREE_PART.get()))return false;
            int index=state.getValue(RecallTreePartBlock.PART);
            return index<RecallTreeLayout.AUTHORED_START&&root.offset(OFFSETS.get(index)).equals(pos);
        }
        BlockPos offset=pos.subtract(root);
        return RecallTreeLayout.PARTS_AT.getOrDefault(offset,java.util.Set.of()).stream().anyMatch(manifest.get()::contains);
    }
    public static boolean coreContains(BlockPos root,BlockPos pos) {
        if(root.equals(pos)||RecallTreeLayout.CLEARANCE.contains(pos.subtract(root)))return true;
        return RecallTreeLayout.PARTS_AT.getOrDefault(pos.subtract(root),java.util.Set.of()).stream()
                .anyMatch(i->i>=RecallTreeLayout.AUTHORED_START&&RecallTreeLayout.portal(i));
    }

    private static boolean clear(ServerLevel level,BlockPos pos) {
        if(level.isOutsideBuildHeight(pos)||!level.getWorldBorder().isWithinBounds(pos))return false;
        level.getChunk(pos.getX()>>4,pos.getZ()>>4);
        return level.getBlockState(pos).isAir()&&level.getFluidState(pos).isEmpty();
    }

    public static boolean fits(ServerLevel level, BlockPos root) {
        var cells=new java.util.ArrayList<BlockPos>();cells.add(root);
        RecallTreeLayout.CLEARANCE.stream().map(root::offset).forEach(cells::add);
        for(int i=RecallTreeLayout.AUTHORED_START;i<OFFSETS.size();i++)if(RecallTreeLayout.portal(i))cells.add(root.offset(OFFSETS.get(i)));
        for (BlockPos pos : cells) {
            if(!clear(level,pos))return false;
        }
        for (BlockPos offset : RecallTreeLayout.FLOORS) {
            BlockPos floor = root.offset(offset);
            if (!DestinationChecks.isSturdyFloor(level, floor) || DestinationChecks.isHazard(level, floor)) return false;
        }
        return true;
    }

    public static void place(ServerLevel level, BlockPos root, ExpeditionSavedData data) {
        if(!fits(level,root))throw new IllegalStateException("The recall opening needs safe clearance");
        String dimension = level.dimension().location().toString();
        level.setBlock(root, ModBlocks.EXPEDITION_EXIT.get().defaultBlockState().setValue(ExpeditionExitBlock.GROWN, true)
                .setValue(ExpeditionExitBlock.FULL_TREE,true).setValue(ExpeditionExitBlock.TREE_LAYOUT,1), 3);
        data.trackMarker(dimension, root);
        var placed=new java.util.HashSet<Integer>();
        for (int i = RecallTreeLayout.AUTHORED_START; i < OFFSETS.size(); i++) {
            BlockPos pos = root.offset(OFFSETS.get(i));
            if(!clear(level,pos))continue; // The tree yields; existing terrain is never removed or dropped.
            level.setBlock(pos, ModBlocks.RECALL_TREE_PART.get().defaultBlockState().setValue(RecallTreePartBlock.PART, i), 3);
            data.trackMarker(dimension, pos);
            placed.add(i);
        }
        data.recordRecallTreeParts(dimension,root,placed);
    }

    public static boolean intact(ServerLevel level, BlockPos root) {
        var data=level.getServer().overworld().getDataStorage().computeIfAbsent(ExpeditionSavedData.FACTORY,ExpeditionSavedData.DATA_NAME);
        return intact(level,root,data.recallTreeParts(level.dimension().location().toString(),root));
    }
    public static boolean intact(ServerLevel level,BlockPos root,java.util.Optional<java.util.Set<Integer>> manifest) {
        var state = level.getBlockState(root);
        if (!state.is(ModBlocks.EXPEDITION_EXIT.get())) return false;
        if (!state.getValue(ExpeditionExitBlock.GROWN)) return manifest.isEmpty(); // Existing compact exits remain valid.
        if(state.getValue(ExpeditionExitBlock.FULL_TREE)&&state.getValue(ExpeditionExitBlock.TREE_LAYOUT)==1&&manifest.isEmpty())return false;
        int first=!state.getValue(ExpeditionExitBlock.FULL_TREE)?0:
                state.getValue(ExpeditionExitBlock.TREE_LAYOUT)==0?RecallTreeLayout.LEGACY_COUNT:RecallTreeLayout.AUTHORED_START;
        int end=first==0?RecallTreeLayout.LEGACY_COUNT:first==RecallTreeLayout.LEGACY_COUNT?RecallTreeLayout.AUTHORED_START:OFFSETS.size();
        java.util.Set<Integer> expected=manifest.orElseGet(()->java.util.stream.IntStream.range(first,end).boxed().collect(java.util.stream.Collectors.toSet()));
        if(expected.isEmpty()||expected.stream().anyMatch(i->i<first||i>=end))return false;
        for(int i=first;i<end;i++)if(RecallTreeLayout.portal(i)&&!expected.contains(i))return false;
        for (int i : expected) {
            var part = level.getBlockState(root.offset(OFFSETS.get(i)));
            if (!part.is(ModBlocks.RECALL_TREE_PART.get()) || part.getValue(RecallTreePartBlock.PART) != i) return false;
        }
        return true;
    }
}
