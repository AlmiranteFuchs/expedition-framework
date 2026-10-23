package com.expeditions.server.data;

import com.expeditions.core.ExpeditionDefinition;
import com.expeditions.core.ExpeditionRoute;
import com.expeditions.core.RouteAnchor;
import com.expeditions.core.RouteGeneration;
import com.expeditions.core.RouteSpec;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

/**
 * Datapack-provided expedition definitions, loaded from
 * {@code data/<namespace>/expeditions/<name>.json} by
 * {@link ExpeditionDefinitionLoader}. This store holds the latest reloaded
 * snapshot as an immutable map — datapack resources are server-level, not
 * world-persistent state, so a process-wide snapshot is the standard NeoForge
 * approach (same pattern as recipe/tag lookups).
 *
 * <p>Datapack definitions take precedence over config-defined expeditions for
 * the same dimension. Dimension adapters still gate support: a datapack entry
 * for a dimension without an adapter simply cannot start.
 */
public final class ExpeditionDefinitions {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int TICKS_PER_SECOND = 20;

    private static volatile Map<String, ExpeditionDefinition> byId = Map.of();

    private ExpeditionDefinitions() {
    }

    /**
     * Replaces the snapshot with freshly loaded datapack entries. Malformed
     * files are skipped and logged, matching the persistence layer's
     * fault-isolation policy. Public for game tests simulating a datapack
     * reload; production callers go through {@link ExpeditionDefinitionLoader}.
     */
    public static void reload(Map<ResourceLocation, JsonElement> entries) {
        Map<String, ExpeditionDefinition> loaded = new TreeMap<>();
        for (Map.Entry<ResourceLocation, JsonElement> entry : entries.entrySet()) {
            try {
                ExpeditionDefinition definition = parse(entry.getKey(), entry.getValue());
                loaded.put(definition.id(), definition);
            } catch (RuntimeException e) {
                LOGGER.warn("Skipping malformed expedition definition {}: {}",
                        entry.getKey(), e.getMessage());
            }
        }
        byId = Map.copyOf(loaded);
        LOGGER.info("Loaded {} datapack expedition definitions", loaded.size());
    }

    /** Clears the snapshot (server stop / test teardown). */
    public static void clear() {
        byId = Map.of();
    }

    /**
     * The datapack expedition for {@code dimensionId}, if one is defined. When
     * several definitions share a dimension, the lowest id wins — documented
     * and deterministic rather than load-order dependent.
     */
    public static Optional<ExpeditionDefinition> forDimension(String dimensionId) {
        return byId.values().stream()
                .filter(d -> d.dimension().equals(dimensionId))
                .findFirst();
    }

    /** All loaded definitions (unmodifiable snapshot). */
    public static Map<String, ExpeditionDefinition> all() {
        return byId;
    }

    /**
     * Parses one datapack entry into an {@link ExpeditionDefinition}.
     *
     * @throws RuntimeException if required fields are missing or malformed
     */
    static ExpeditionDefinition parse(ResourceLocation id, JsonElement element) {
        if (!element.isJsonObject()) {
            throw new IllegalArgumentException("expedition must be a JSON object");
        }
        JsonObject json = element.getAsJsonObject();

        String dimension = requiredString(json, "dimension");
        if (ResourceLocation.tryParse(dimension) == null) {
            throw new IllegalArgumentException("invalid dimension id \"" + dimension + "\"");
        }

        RouteSpec route;
        if (json.has("generate")) {
            if (json.has("checkpoints") || json.has("exit")) {
                throw new IllegalArgumentException(
                        "\"generate\" cannot be combined with \"checkpoints\"/\"exit\"");
            }
            route = parseGeneration(json.getAsJsonObject("generate"));
        } else {
            List<RouteAnchor> checkpoints = parseAnchors(json.getAsJsonArray("checkpoints"));
            if (checkpoints.isEmpty()) {
                throw new IllegalArgumentException(
                        "checkpoints must be a non-empty array of [x,z]");
            }
            route = new ExpeditionRoute(checkpoints, parseAnchor(json.get("exit")));
        }

        return new ExpeditionDefinition(
                id.toString(),
                optionalString(json, "display_name", id.getPath()),
                optionalInt(json, "difficulty", 0),
                dimension,
                route,
                optionalInt(json, "tokens", 0),
                optionalInt(json, "bonus_tokens", 0),
                optionalInt(json, "bonus_time_seconds", 0) * TICKS_PER_SECOND);
    }

    private static RouteGeneration parseGeneration(JsonObject json) {
        if (json == null) {
            throw new IllegalArgumentException("\"generate\" must be an object");
        }
        int minSpacing = optionalInt(json, "min_spacing", 32);
        int exitMin = optionalInt(json, "exit_min", 48);
        int entryMin = optionalInt(json, "entry_min", 128);
        return new RouteGeneration(
                optionalInt(json, "checkpoints", 3),
                minSpacing,
                optionalInt(json, "max_spacing", Math.max(minSpacing, 64)),
                exitMin,
                optionalInt(json, "exit_max", Math.max(exitMin, 96)),
                optionalInt(json, "max_attempts", 4),
                entryMin,
                optionalInt(json, "entry_max", Math.max(entryMin, 256)));
    }

    private static String requiredString(JsonObject json, String key) {
        if (!json.has(key) || !json.get(key).isJsonPrimitive()) {
            throw new IllegalArgumentException("missing required \"" + key + "\"");
        }
        return json.get(key).getAsString();
    }

    private static String optionalString(JsonObject json, String key, String fallback) {
        return json.has(key) && json.get(key).isJsonPrimitive()
                ? json.get(key).getAsString() : fallback;
    }

    private static int optionalInt(JsonObject json, String key, int fallback) {
        return json.has(key) && json.get(key).isJsonPrimitive()
                ? json.get(key).getAsInt() : fallback;
    }

    private static List<RouteAnchor> parseAnchors(JsonArray array) {
        if (array == null) {
            return List.of();
        }
        List<RouteAnchor> anchors = new ArrayList<>();
        for (JsonElement e : array) {
            anchors.add(parseAnchor(e));
        }
        return anchors;
    }

    private static RouteAnchor parseAnchor(JsonElement e) {
        if (e == null || !e.isJsonArray()) {
            throw new IllegalArgumentException("route positions must be [x,z] arrays");
        }
        JsonArray pair = e.getAsJsonArray();
        if (pair.size() != 2) {
            throw new IllegalArgumentException("route positions must be [x,z] pairs");
        }
        return new RouteAnchor(pair.get(0).getAsInt(), pair.get(1).getAsInt());
    }
}
