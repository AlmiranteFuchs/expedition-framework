package com.expeditions.server;

import com.expeditions.ExpeditionsConfig;
import com.expeditions.block.entity.AssimilationHeartBlockEntity;
import com.expeditions.core.SafeColumnSearch;
import com.expeditions.registry.ModBlocks;
import com.expeditions.server.data.ExpeditionTuning;
import com.expeditions.server.dimension.DestinationChecks;
import java.util.Optional;
import java.util.OptionalInt;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;

/**
 * The microbiome's world presence: whether it may grow here at all, and
 * the traversal-spawning that seeds hearts while players move through a
 * dimension.
 *
 * <p>The kill-switch is two-staged by design: {@code microbiome.enabled}
 * is the runtime config toggle, and a dimension marked cleared in the
 * saved data silences both new hearts and existing ones — the "won the
 * dimension" transition lands with the campaign milestone but the seam
 * is already enforced.
 */
public final class Microbiome {
    /**
     * Test seams: the game-test suite suppresses ambient seeding. Mock
     * players tick once at the world spawn before teleporting, so the
     * traversal roll would seed hearts around the origin — right where the
     * authored-route anchors every datapack test shares live — and colonies
     * would carpet those columns with non-sturdy membranes until route
     * resolution fails. {@code traversalSuppressed} is set once for the
     * whole suite and never lifted; {@code seedingSuppressed} gates marker
     * colonies, which only the dedicated colony test lifts inside its own
     * batch. Tests exercise {@code trySpawnHeart} directly.
     */
    private static volatile boolean seedingSuppressed;
    private static volatile boolean traversalSuppressed;

    public static void suppressSeedingForTests(boolean suppressed) {
        seedingSuppressed = suppressed;
    }

    public static void suppressTraversalForTests(boolean suppressed) {
        traversalSuppressed = suppressed;
    }

    private Microbiome() {
    }

    /** Whether the microbiome may grow or spawn in {@code level}. */
    public static boolean enabled(ServerLevel level) {
        return ExpeditionTuning.bool(level.dimension(), "microbiome.enabled",
                ExpeditionsConfig.microbiomeEnabled())
                && !savedData(level).isDimensionCleared(
                        level.dimension().location().toString());
    }

    /**
     * Tries to seed a heart on a standable column in a ring around
     * {@code center} (the player): far enough out to be discovered rather
     * than appear underfoot, within the same bounded elevation window the
     * mob spawner uses so a heart never lands on an enclosed dimension's
     * roof.
     */
    public static Optional<BlockPos> trySpawnHeart(ServerLevel level,
            BlockPos center, RandomSource random) {
        int min = ExpeditionTuning.integer(level.dimension(), "microbiome.spawn_radius_min",
                ExpeditionsConfig.microbiomeSpawnRadiusMin());
        int max = ExpeditionTuning.integer(level.dimension(), "microbiome.spawn_radius_max",
                ExpeditionsConfig.microbiomeSpawnRadiusMax());
        double angle = random.nextDouble() * Math.PI * 2;
        int distance = min + random.nextInt(Math.max(1, max - min));
        BlockPos column = center.offset(
                (int) Math.round(Math.cos(angle) * distance), 0,
                (int) Math.round(Math.sin(angle) * distance));
        level.getChunk(column);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(
                column.getX(), 0, column.getZ());
        int top = Math.min(level.getMaxBuildHeight() - 1, center.getY() + 16);
        int bottom = Math.max(level.getMinBuildHeight() + 1, center.getY() - 40);
        OptionalInt y = SafeColumnSearch.findStandingY(
                yy -> DestinationChecks.isFree(level, pos.setY(yy)),
                yy -> DestinationChecks.isSturdyFloor(level, pos.setY(yy)),
                yy -> DestinationChecks.isHazard(level, pos.setY(yy)),
                top, bottom);
        if (y.isEmpty()) {
            return Optional.empty();
        }
        BlockPos heartPos = pos.setY(y.getAsInt()).immutable();
        return level.setBlock(heartPos,
                ModBlocks.ASSIMILATION_HEART.get().defaultBlockState(), 3)
                ? Optional.of(heartPos)
                : Optional.empty();
    }

    /** Per-player traversal roll: called on a throttled player tick. */
    public static void onPlayerTick(ServerLevel level, BlockPos playerPos) {
        if (!traversalSuppressed
                && level.random.nextDouble() < ExpeditionTuning.real(level.dimension(),
                        "microbiome.heart_spawn_chance", ExpeditionsConfig.microbiomeSpawnChance())) {
            trySpawnHeart(level, playerPos, level.random);
        }
    }

    /**
     * Seeds a colony near a run marker — the microbiome colonizes
     * expedition infrastructure, and the glowing blob doubles as the
     * route's landmark. Best-effort like mob spawning: random columns in
     * the ring first, then a deterministic staggered sweep, so a viable
     * neighborhood always hosts one. The starter pulses give the patch
     * immediate presence; full growth still comes from the heart's own
     * ticker.
     */
    public static Optional<BlockPos> seedColonyNear(ServerLevel level,
            BlockPos marker, RandomSource random) {
        if (seedingSuppressed || !enabled(level)
                || !ExpeditionTuning.bool(level.dimension(), "microbiome.marker_colonies",
                        ExpeditionsConfig.microbiomeMarkerColonies())) {
            return Optional.empty();
        }
        int radius = ExpeditionTuning.integer(level.dimension(),
                "microbiome.marker_colony_radius",
                ExpeditionsConfig.microbiomeMarkerColonyRadius());
        for (int attempt = 0; attempt < 8; attempt++) {
            Optional<BlockPos> spot = columnSpot(level, marker,
                    random.nextDouble() * Math.PI * 2,
                    2 + random.nextInt(Math.max(1, radius - 1)));
            if (spot.isPresent()) {
                return plantColony(level, spot.get());
            }
        }
        for (int ri = 0; ri < 3; ri++) {
            int distance = Math.max(2, radius * (ri + 1) / 4);
            for (int ai = 0; ai < 8; ai++) {
                Optional<BlockPos> spot = columnSpot(level, marker,
                        ai * Math.PI / 4 + ri * 0.41, distance);
                if (spot.isPresent()) {
                    return plantColony(level, spot.get());
                }
            }
        }
        return Optional.empty();
    }

    /**
     * A standable cell in {@code column} near the marker's elevation —
     * the same two-free-above-a-sturdy-floor contract as mob spawning.
     */
    private static Optional<BlockPos> columnSpot(ServerLevel level,
            BlockPos marker, double angle, int distance) {
        BlockPos column = marker.offset(
                (int) Math.round(Math.cos(angle) * distance), 0,
                (int) Math.round(Math.sin(angle) * distance));
        level.getChunk(column);
        return MobSpawns.findStandingSpot(level, column, marker.getY());
    }

    /** Places a heart at {@code heartPos} and gives it a starter patch. */
    private static Optional<BlockPos> plantColony(ServerLevel level,
            BlockPos heartPos) {
        if (!level.setBlock(heartPos,
                ModBlocks.ASSIMILATION_HEART.get().defaultBlockState(), 3)) {
            return Optional.empty();
        }
        if (level.getBlockEntity(heartPos)
                instanceof AssimilationHeartBlockEntity heart) {
            int starter = ExpeditionTuning.integer(level.dimension(),
                    "microbiome.marker_colony_starter_pulses",
                    ExpeditionsConfig.microbiomeMarkerColonyStarterPulses());
            for (int i = 0; i < starter; i++) {
                heart.pulse(level);
            }
        }
        return Optional.of(heartPos);
    }

    private static ExpeditionSavedData savedData(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
                ExpeditionSavedData.FACTORY, ExpeditionSavedData.DATA_NAME);
    }
}
