package com.expeditions.server.data;

import com.google.gson.JsonElement;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;

/**
 * Datapack-provided per-dimension tuning overrides, loaded by
 * {@link ExpeditionTuningLoader} from
 * {@code data/<namespace>/expedition_dimensions/<path>.json} — the resource
 * id is the dimension id it tunes ({@code minecraft/the_nether.json} →
 * {@code minecraft:the_nether}; modded dims and sub-paths like
 * {@code mymod:planets/mars} work the same way).
 *
 * <p>Overrides are sparse: a key present here wins, absent keys fall back to
 * the global {@code expeditions-server.toml} value. Consumers call the typed
 * resolution helpers with the dimension they are acting in plus the global
 * fallback — the whole override reads as one expression at each call site.
 * Same process-wide immutable snapshot pattern as
 * {@link ExpeditionDefinitions} (server-level datapack state, re-applied on
 * every {@code /reload}).
 */
public final class ExpeditionTuning {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static volatile Map<String, DimensionTuning> byDimension = Map.of();

    private ExpeditionTuning() {
    }

    /**
     * Replaces the snapshot with freshly loaded entries. Non-object files are
     * skipped and logged; public for game tests simulating a datapack reload
     * (always reset with {@code reload(Map.of())} in a finally).
     */
    public static void reload(Map<ResourceLocation, JsonElement> entries) {
        Map<String, DimensionTuning> loaded = new TreeMap<>();
        for (Map.Entry<ResourceLocation, JsonElement> entry : entries.entrySet()) {
            if (!entry.getValue().isJsonObject()) {
                LOGGER.warn("Skipping malformed dimension tuning {}: not a JSON object",
                        entry.getKey());
                continue;
            }
            loaded.put(entry.getKey().toString(),
                    new DimensionTuning(entry.getValue().getAsJsonObject()));
        }
        byDimension = Map.copyOf(loaded);
        LOGGER.info("Loaded {} dimension tuning overrides", loaded.size());
    }

    /** Clears the snapshot (server stop / test teardown). */
    public static void clear() {
        byDimension = Map.of();
    }

    /** The overrides for {@code dimensionId}, if a datapack file defined any. */
    public static Optional<DimensionTuning> forDimension(String dimensionId) {
        return Optional.ofNullable(byDimension.get(dimensionId));
    }

    /** {@code path}'s boolean override for {@code dimension}, else {@code fallback}. */
    public static boolean bool(ResourceKey<Level> dimension, String path, boolean fallback) {
        return bool(dimension.location().toString(), path, fallback);
    }

    public static boolean bool(String dimensionId, String path, boolean fallback) {
        DimensionTuning tuning = byDimension.get(dimensionId);
        return tuning != null ? tuning.bool(path).orElse(fallback) : fallback;
    }

    /** {@code path}'s int override for {@code dimension}, else {@code fallback}. */
    public static int integer(ResourceKey<Level> dimension, String path, int fallback) {
        return integer(dimension.location().toString(), path, fallback);
    }

    public static int integer(String dimensionId, String path, int fallback) {
        DimensionTuning tuning = byDimension.get(dimensionId);
        return tuning != null ? tuning.integer(path).orElse(fallback) : fallback;
    }

    /** {@code path}'s double override for {@code dimension}, else {@code fallback}. */
    public static double real(ResourceKey<Level> dimension, String path, double fallback) {
        return real(dimension.location().toString(), path, fallback);
    }

    public static double real(String dimensionId, String path, double fallback) {
        DimensionTuning tuning = byDimension.get(dimensionId);
        return tuning != null ? tuning.real(path).orElse(fallback) : fallback;
    }
}
