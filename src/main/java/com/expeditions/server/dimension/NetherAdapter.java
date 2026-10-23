package com.expeditions.server.dimension;

import com.expeditions.core.RouteAnchor;
import com.expeditions.core.SafeColumnSearch;
import java.util.Optional;
import java.util.OptionalInt;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/**
 * Adapted resolution for the Nether: no surface heightmap exists below the
 * bedrock ceiling, so columns are scanned downward for a standable spot —
 * two free blocks above a sturdy, non-hazardous floor, with no hazard inside
 * the three-block space. Columns that offer nothing safe are refused.
 */
public final class NetherAdapter implements ExpeditionDimensionAdapter {
    /** Scan window: below the ceiling, above the lava-sea bed. */
    private static final int SCAN_TOP = 121;
    private static final int SCAN_BOTTOM = 33;

    @Override
    public ResourceKey<Level> dimension() {
        return Level.NETHER;
    }

    @Override
    public Optional<BlockPos> resolveAnchor(ServerLevel level, RouteAnchor anchor) {
        level.getChunk(anchor.x() >> 4, anchor.z() >> 4);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(anchor.x(), 0, anchor.z());
        OptionalInt y = SafeColumnSearch.findStandingY(
                yy -> DestinationChecks.isFree(level, pos.setY(yy)),
                yy -> DestinationChecks.isSturdyFloor(level, pos.setY(yy)),
                yy -> DestinationChecks.isHazard(level, pos.setY(yy)),
                SCAN_TOP, SCAN_BOTTOM);
        return y.isPresent()
                ? Optional.of(new BlockPos(anchor.x(), y.getAsInt(), anchor.z()))
                : Optional.empty();
    }
}
