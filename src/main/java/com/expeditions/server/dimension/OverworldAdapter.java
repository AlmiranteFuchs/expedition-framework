package com.expeditions.server.dimension;

import com.expeditions.core.RouteAnchor;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Automatic resolution for surface dimensions. The heightmap bounds the
 * search, then the candidate column is validated against real block state:
 * the marker position and the space above it must be free and hazard-free,
 * over a sturdy non-hazardous floor, inside the world border. Unsafe columns
 * — water, lava, cactus fields, unsupportable ground — are refused rather
 * than nudged into.
 */
public final class OverworldAdapter implements ExpeditionDimensionAdapter {
    /** Upward nudge budget when the heightmap lands inside an obstruction. */
    private static final int NUDGE_LIMIT = 16;

    @Override
    public ResourceKey<Level> dimension() {
        return Level.OVERWORLD;
    }

    @Override
    public Optional<BlockPos> resolveAnchor(ServerLevel level, RouteAnchor anchor) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(anchor.x(), 0, anchor.z());
        if (!level.getWorldBorder().isWithinBounds(pos)) {
            return Optional.empty();
        }
        level.getChunk(anchor.x() >> 4, anchor.z() >> 4);
        int surface = level.getHeight(
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, anchor.x(), anchor.z());
        pos.setY(surface);
        for (int i = 0; i < NUDGE_LIMIT && pos.getY() < level.getMaxBuildHeight() - 1; i++) {
            if (isSafeStandingSpot(level, pos)) {
                return Optional.of(pos.immutable());
            }
            pos.move(0, 1, 0);
        }
        return Optional.empty();
    }

    /** A marker spot a player could stand at: free with headroom, safe floor. */
    private static boolean isSafeStandingSpot(ServerLevel level, BlockPos pos) {
        return DestinationChecks.isFree(level, pos)
                && DestinationChecks.isFree(level, pos.above())
                && !DestinationChecks.isHazard(level, pos)
                && DestinationChecks.isSturdyFloor(level, pos.below())
                && !DestinationChecks.isHazard(level, pos.below());
    }
}
