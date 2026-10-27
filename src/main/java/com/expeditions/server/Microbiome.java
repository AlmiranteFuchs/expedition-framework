package com.expeditions.server;

import com.expeditions.ExpeditionsConfig;
import com.expeditions.core.SafeColumnSearch;
import com.expeditions.registry.ModBlocks;
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
    private Microbiome() {
    }

    /** Whether the microbiome may grow or spawn in {@code level}. */
    public static boolean enabled(ServerLevel level) {
        return ExpeditionsConfig.microbiomeEnabled()
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
        int min = ExpeditionsConfig.microbiomeSpawnRadiusMin();
        int max = ExpeditionsConfig.microbiomeSpawnRadiusMax();
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
        if (level.random.nextDouble() < ExpeditionsConfig.microbiomeSpawnChance()) {
            trySpawnHeart(level, playerPos, level.random);
        }
    }

    private static ExpeditionSavedData savedData(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
                ExpeditionSavedData.FACTORY, ExpeditionSavedData.DATA_NAME);
    }
}
