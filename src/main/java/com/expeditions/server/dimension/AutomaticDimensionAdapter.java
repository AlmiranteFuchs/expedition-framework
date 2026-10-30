package com.expeditions.server.dimension;

import com.expeditions.core.RouteAnchor;
import com.expeditions.core.SafeColumnSearch;
import java.util.Optional;
import java.util.OptionalInt;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;

/** Conservative fallback for loaded dimensions without a specialized adapter. */
public record AutomaticDimensionAdapter(ResourceKey<Level> dimension)
        implements ExpeditionDimensionAdapter {
    @Override
    public Optional<BlockPos> resolveAnchor(ServerLevel level, RouteAnchor anchor) {
        if (!level.dimension().equals(dimension)) {
            return Optional.empty();
        }
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(anchor.x(), 0, anchor.z());
        if (!level.getWorldBorder().isWithinBounds(pos)) {
            return Optional.empty();
        }
        level.getChunk(anchor.x() >> 4, anchor.z() >> 4);
        // Heightmaps can name the roof of enclosed dimensions. Start below
        // their logical ceiling instead; every candidate still needs real
        // headroom and a non-hazardous, sturdy floor. Void columns refuse.
        int top = level.dimensionType().hasCeiling()
                ? level.getMinBuildHeight() + level.dimensionType().logicalHeight() - 8
                : level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                        anchor.x(), anchor.z());
        top = Math.min(top, level.getMaxBuildHeight() - 2);
        OptionalInt y = SafeColumnSearch.findStandingY(
                yy -> DestinationChecks.isFree(level, pos.setY(yy)),
                yy -> DestinationChecks.isSturdyFloor(level, pos.setY(yy)),
                yy -> DestinationChecks.isHazard(level, pos.setY(yy)),
                top, level.getMinBuildHeight());
        return y.isPresent()
                ? Optional.of(new BlockPos(anchor.x(), y.getAsInt(), anchor.z()))
                : Optional.empty();
    }
}
