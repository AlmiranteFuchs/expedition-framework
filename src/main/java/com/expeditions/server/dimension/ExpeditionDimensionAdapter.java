package com.expeditions.server.dimension;

import com.expeditions.core.RouteAnchor;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/**
 * Per-dimension strategy for turning configured route anchors into safe world
 * positions. Adapters encapsulate everything a dimension needs to answer "can
 * an expedition happen here, and where do the markers go" — terrain layout,
 * build limits and hazards differ per dimension and must not leak into the
 * lifecycle code.
 *
 * <p>Resolution is deliberately conservative: an adapter returns empty rather
 * than guessing an unsafe spot. Loaded dimensions without an explicitly
 * registered adapter use {@link AutomaticDimensionAdapter}; unresolved routes
 * still refuse before placing markers or charging tokens.
 */
public interface ExpeditionDimensionAdapter {
    /** The dimension this adapter serves. */
    ResourceKey<Level> dimension();

    /**
     * Resolves a configured X/Z anchor to a safe, marker-hosting position.
     * Loads the column's chunk if needed.
     *
     * @return the resolved position, or empty when no safe spot exists
     */
    Optional<BlockPos> resolveAnchor(ServerLevel level, RouteAnchor anchor);
}
