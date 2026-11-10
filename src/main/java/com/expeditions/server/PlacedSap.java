package com.expeditions.server;

import com.expeditions.registry.ModAttachments;
import com.expeditions.registry.ModBlocks;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.level.ChunkEvent;

/** Persisted source identities travel with the chunk that owns the actual liquid block. */
public final class PlacedSap {
    private PlacedSap() { }

    public static boolean isSource(BlockState state) {
        return state.is(ModBlocks.RETURN_SAP.get()) && state.getFluidState().isSource();
    }

    public static void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level && event.getChunk() instanceof LevelChunk chunk) {
            migrate(chunk, level.dimension().location().toString());
        }
    }

    private static void migrate(ChunkAccess chunk, String local) {
        if (chunk.hasData(ModAttachments.PLACED_SAP)) return;
        var sources = new HashMap<String, String>();
        var sections = chunk.getSections();
        for (int i = 0; i < sections.length; i++) {
            var section = sections[i];
            if (!section.maybeHas(PlacedSap::isSource)) continue;
            int y0 = chunk.getSectionYFromSectionIndex(i) << 4;
            for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
                if (isSource(section.getBlockState(x, y, z))) {
                    var pos = new BlockPos(chunk.getPos().getMinBlockX() + x, y0 + y, chunk.getPos().getMinBlockZ() + z);
                    sources.put(key(pos), local);
                }
            }
        }
        save(chunk, sources);
    }

    public static String source(ServerLevel level, BlockPos pos) {
        var chunk = level.getChunkAt(pos);
        migrate(chunk, level.dimension().location().toString());
        return chunk.getData(ModAttachments.PLACED_SAP).getOrDefault(key(pos), "");
    }

    public static void remember(ServerLevel level, BlockPos pos, String dimension) {
        var chunk = level.getChunkAt(pos);
        var sources = new HashMap<>(chunk.getData(ModAttachments.PLACED_SAP));
        if (dimension.isBlank()) sources.remove(key(pos));
        else sources.put(key(pos), dimension);
        save(chunk, sources);
    }

    public static void forget(ServerLevel level, BlockPos pos) {
        var chunk = level.getChunkAt(pos);
        if (!chunk.hasData(ModAttachments.PLACED_SAP) || !chunk.getData(ModAttachments.PLACED_SAP).containsKey(key(pos))) return;
        remember(level, pos, "");
    }

    private static String key(BlockPos pos) { return Long.toString(pos.asLong()); }

    private static void save(ChunkAccess chunk, Map<String, String> sources) {
        chunk.setData(ModAttachments.PLACED_SAP, Map.copyOf(sources));
        chunk.setUnsaved(true);
    }
}
