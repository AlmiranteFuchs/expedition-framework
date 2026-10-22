package com.expeditions.server.dimension;

import com.expeditions.core.RouteAnchor;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Automatic resolution for surface dimensions: the heightmap gives the ground
 * and a short upward nudge places markers above obstructions like grass or
 * snow layers.
 */
public final class OverworldAdapter implements ExpeditionDimensionAdapter {
    @Override
    public ResourceKey<Level> dimension() {
        return Level.OVERWORLD;
    }

    @Override
    public Optional<BlockPos> resolveAnchor(ServerLevel level, RouteAnchor anchor) {
        level.getChunk(anchor.x() >> 4, anchor.z() >> 4);
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, anchor.x(), anchor.z());
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(anchor.x(), y, anchor.z());
        for (int i = 0; i < 16 && pos.getY() < level.getMaxBuildHeight() - 1; i++) {
            BlockState state = level.getBlockState(pos);
            if (state.isAir() || state.canBeReplaced()) {
                return Optional.of(pos.immutable());
            }
            pos.move(0, 1, 0);
        }
        return Optional.empty();
    }
}
