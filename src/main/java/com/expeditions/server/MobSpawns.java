package com.expeditions.server;

import com.expeditions.core.SafeColumnSearch;
import com.expeditions.server.dimension.DestinationChecks;
import java.util.Optional;
import java.util.OptionalInt;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;

/**
 * Shared "put a hostile mob on real ground near an event" logic for
 * challenge hordes and defense waves. Spawn positions come from a bounded
 * column scan around the event's own elevation — the heightmap is
 * deliberately not consulted: in enclosed dimensions it names the ceiling
 * (a horde on the nether roof is a horde that doesn't exist), and in open
 * ones the window keeps spawns near the players' level instead of a far
 * ridge top.
 */
public final class MobSpawns {
    /** How far above an event's elevation a spawn column is scanned. */
    private static final int SCAN_UP = 16;
    /** How far below — deep enough to reach floors in enclosed dimensions. */
    private static final int SCAN_DOWN = 40;
    /** Random columns a single mob gets, like vanilla's per-mob retries. */
    private static final int ATTEMPTS = 8;
    /** Fixed columns in the deterministic ring sweep fallback. */
    private static final int SWEEP_ANGLES = 8;
    private static final int SWEEP_RADII = 3;

    private MobSpawns() {
    }

    /**
     * A positioned, collision-free {@link Mob} of {@code type} in the ring
     * around {@code center}, or null when no column can host it. Random
     * columns first (cheap, varied); when every roll misses, a bounded
     * deterministic sweep of staggered rings decides whether the ring is
     * viable at all — a challenge that CAN spawn must not silently pass on
     * an unlucky roll. Not yet finalized or added to the level — callers
     * run {@code finalizeSpawn} and {@code addFreshEntity} themselves.
     */
    public static Mob trySpawn(EntityType<?> type, ServerLevel level,
            BlockPos center, int radius) {
        for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
            Mob mob = tryColumn(type, level, center,
                    level.getRandom().nextDouble() * Math.PI * 2,
                    2 + level.getRandom().nextInt(radius - 1));
            if (mob != null) {
                return mob;
            }
        }
        for (int ri = 0; ri < SWEEP_RADII; ri++) {
            int distance = Math.max(2, radius * (ri + 1) / (SWEEP_RADII + 1));
            for (int ai = 0; ai < SWEEP_ANGLES; ai++) {
                Mob mob = tryColumn(type, level, center,
                        ai * Math.PI / 4 + ri * 0.41, distance);
                if (mob != null) {
                    return mob;
                }
            }
        }
        return null;
    }

    /**
     * One column attempt: standable surface in the elevation window plus a
     * bounding-box check — the standing contract guarantees two free
     * blocks, a mob taller or wider would suffocate in the wall.
     */
    private static Mob tryColumn(EntityType<?> type, ServerLevel level,
            BlockPos center, double angle, int distance) {
        BlockPos column = center.offset(
                (int) Math.round(Math.cos(angle) * distance), 0,
                (int) Math.round(Math.sin(angle) * distance));
        // Force-load the spawn column (bounded by the configured radius)
        // before reading its blocks — an ungenerated column reports
        // stale state — and so the mob is actually live.
        level.getChunk(column);
        Optional<BlockPos> surface = findStandingSpot(level, column,
                center.getY());
        if (surface.isEmpty() || !(type.create(level) instanceof Mob mob)) {
            return null;
        }
        mob.moveTo(surface.get().getX() + 0.5, surface.get().getY(),
                surface.get().getZ() + 0.5,
                level.getRandom().nextFloat() * 360f, 0f);
        return level.noCollision(mob) ? mob : null;
    }

    /**
     * A standable spot in {@code column} near {@code nearY}: the same
     * "two free blocks above a sturdy, non-hazardous floor" contract route
     * resolution uses, scanned in a bounded window around the event's own
     * elevation.
     */
    public static Optional<BlockPos> findStandingSpot(ServerLevel level,
            BlockPos column, int nearY) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(
                column.getX(), 0, column.getZ());
        int top = Math.min(level.getMaxBuildHeight() - 1, nearY + SCAN_UP);
        int bottom = Math.max(level.getMinBuildHeight() + 1, nearY - SCAN_DOWN);
        OptionalInt y = SafeColumnSearch.findStandingY(
                yy -> DestinationChecks.isFree(level, pos.setY(yy)),
                yy -> DestinationChecks.isSturdyFloor(level, pos.setY(yy)),
                yy -> DestinationChecks.isHazard(level, pos.setY(yy)),
                top, bottom);
        return y.isPresent()
                ? Optional.of(pos.setY(y.getAsInt()).immutable())
                : Optional.empty();
    }
}
