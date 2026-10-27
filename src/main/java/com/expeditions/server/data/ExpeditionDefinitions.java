package com.expeditions.server.data;

import com.expeditions.core.EffectSpec;
import com.expeditions.core.ExpeditionDefinition;
import com.expeditions.core.ExpeditionModifiers;
import com.expeditions.core.ExpeditionObjective;
import com.expeditions.core.ExpeditionRoute;
import com.expeditions.core.RouteAnchor;
import com.expeditions.core.RouteGeneration;
import com.expeditions.core.RouteSpec;
import com.expeditions.core.WeatherModifier;
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
import net.minecraft.core.registries.BuiltInRegistries;
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
                optionalInt(json, "bonus_time_seconds", 0) * TICKS_PER_SECOND,
                parseObjectives(json.get("objectives")),
                parseModifiers(json.get("modifiers")),
                parseKeys(json.get("requires")),
                parseKeys(json.get("grants")),
                optionalInt(json, "cost_tokens", 0),
                parseCampaignState(json.get("min_campaign_state")),
                optionalBool(json, "big_bad", false));
    }

    /**
     * Parses the optional {@code "min_campaign_state"} campaign gate —
     * {@code frontier} (default), {@code established} or {@code cleared};
     * unknown ids fail the definition loudly like other enum fields.
     */
    private static com.expeditions.core.CampaignState parseCampaignState(
            JsonElement element) {
        if (element == null) {
            return com.expeditions.core.CampaignState.FRONTIER;
        }
        if (!element.isJsonPrimitive()) {
            throw new IllegalArgumentException(
                    "\"min_campaign_state\" must be a string");
        }
        com.expeditions.core.CampaignState state =
                com.expeditions.core.CampaignState.byId(element.getAsString());
        if (state == null) {
            throw new IllegalArgumentException(
                    "unknown min_campaign_state \"" + element.getAsString() + "\"");
        }
        return state;
    }

    /**
     * Parses the optional {@code "requires"}/{@code "grants"} string arrays:
     * unlock keys gating the expedition and keys it awards on completion.
     * Blank entries fail the definition loudly.
     */
    private static java.util.Set<String> parseKeys(JsonElement element) {
        if (element == null) {
            return java.util.Set.of();
        }
        if (!element.isJsonArray()) {
            throw new IllegalArgumentException(
                    "\"requires\"/\"grants\" must be arrays of key strings");
        }
        java.util.Set<String> keys = new java.util.LinkedHashSet<>();
        for (JsonElement e : element.getAsJsonArray()) {
            if (!e.isJsonPrimitive() || e.getAsString().isBlank()) {
                throw new IllegalArgumentException("unlock keys must be non-blank strings");
            }
            keys.add(e.getAsString());
        }
        return keys;
    }

    /**
     * Parses the optional {@code "modifiers"} object:
     * {@code "weather"} ({@code clear}|{@code rain}|{@code thunder}),
     * {@code "player_effects"} and {@code "horde_effects"} (arrays of
     * {@code {"id": effect-id, "amplifier": n}}). Effect ids are validated
     * against the mob-effect registry so modded effects work and typos fail
     * the definition loudly.
     */
    private static ExpeditionModifiers parseModifiers(JsonElement element) {
        if (element == null) {
            return ExpeditionModifiers.NONE;
        }
        if (!element.isJsonObject()) {
            throw new IllegalArgumentException("\"modifiers\" must be an object");
        }
        JsonObject json = element.getAsJsonObject();
        WeatherModifier weather = WeatherModifier.NONE;
        String weatherName = optionalString(json, "weather", null);
        if (weatherName != null) {
            weather = WeatherModifier.byName(weatherName);
            if (weather == null) {
                throw new IllegalArgumentException(
                        "unknown modifier weather \"" + weatherName + "\"");
            }
        }
        return new ExpeditionModifiers(weather,
                parseEffects(json.get("player_effects"), "player_effects"),
                parseEffects(json.get("horde_effects"), "horde_effects"));
    }

    private static List<EffectSpec> parseEffects(JsonElement element, String key) {
        if (element == null) {
            return List.of();
        }
        if (!element.isJsonArray()) {
            throw new IllegalArgumentException("\"" + key + "\" must be an array");
        }
        List<EffectSpec> effects = new ArrayList<>();
        for (JsonElement e : element.getAsJsonArray()) {
            if (!e.isJsonObject()) {
                throw new IllegalArgumentException(key + " entries must be JSON objects");
            }
            JsonObject json = e.getAsJsonObject();
            String effectId = requiredString(json, "id");
            ResourceLocation parsed = ResourceLocation.tryParse(effectId);
            if (parsed == null
                    || BuiltInRegistries.MOB_EFFECT.getOptional(parsed).isEmpty()) {
                throw new IllegalArgumentException(
                        key + " references unknown effect \"" + effectId + "\"");
            }
            effects.add(new EffectSpec(effectId, optionalInt(json, "amplifier", 0)));
        }
        return effects;
    }

    /**
     * Parses the optional {@code "objectives"} array. Each entry names a
     * stable {@code id} and a {@code type}; shared optional keys are
     * {@code description}, {@code required} and {@code bonus_tokens}.
     * Per type: {@code visit_checkpoint} takes {@code checkpoint} (1-based
     * index), {@code collect_items} takes exactly one of {@code item} or
     * {@code tag} plus {@code count}, {@code time_limit} takes
     * {@code seconds}, {@code recover_artifact} takes {@code checkpoint}
     * (the checkpoint that issues the run's artifact), and
     * {@code defend_device} takes {@code checkpoint} (the checkpoint that
     * deploys the device) plus {@code seconds} (the siege duration).
     */
    private static List<ExpeditionObjective> parseObjectives(JsonElement element) {
        if (element == null) {
            return List.of();
        }
        if (!element.isJsonArray()) {
            throw new IllegalArgumentException("\"objectives\" must be an array");
        }
        List<ExpeditionObjective> objectives = new ArrayList<>();
        for (JsonElement e : element.getAsJsonArray()) {
            if (!e.isJsonObject()) {
                throw new IllegalArgumentException("objectives must be JSON objects");
            }
            JsonObject json = e.getAsJsonObject();
            String id = requiredString(json, "id");
            String description = optionalString(json, "description", id);
            boolean required = optionalBool(json, "required", false);
            int bonus = optionalInt(json, "bonus_tokens", 0);
            switch (requiredString(json, "type")) {
                case "visit_checkpoint" -> objectives.add(ExpeditionObjective.visitCheckpoint(
                        id, description, required, requiredInt(json, "checkpoint"), bonus));
                case "collect_items" -> {
                    String item = optionalString(json, "item", null);
                    String tag = optionalString(json, "tag", null);
                    if ((item != null) == (tag != null)) {
                        throw new IllegalArgumentException(
                                "collect_items objective \"" + id
                                        + "\" needs exactly one of \"item\" or \"tag\"");
                    }
                    ResourceLocation itemId = ResourceLocation.tryParse(
                            item != null ? item : tag);
                    if (itemId == null) {
                        throw new IllegalArgumentException(
                                "collect_items objective \"" + id + "\" has invalid id \""
                                        + (item != null ? item : tag) + "\"");
                    }
                    if (item != null && BuiltInRegistries.ITEM.getOptional(itemId).isEmpty()) {
                        throw new IllegalArgumentException(
                                "collect_items objective \"" + id
                                        + "\" references unknown item \"" + item + "\"");
                    }
                    objectives.add(ExpeditionObjective.collectItems(id, description, required,
                            item, tag, requiredInt(json, "count"), bonus));
                }
                case "time_limit" -> objectives.add(ExpeditionObjective.timeLimit(
                        id, description, required,
                        (long) requiredInt(json, "seconds") * TICKS_PER_SECOND, bonus));
                case "recover_artifact" -> objectives.add(ExpeditionObjective.recoverArtifact(
                        id, description, required, requiredInt(json, "checkpoint"), bonus));
                case "defend_device" -> objectives.add(ExpeditionObjective.defendDevice(
                        id, description, required, requiredInt(json, "checkpoint"),
                        (long) requiredInt(json, "seconds") * TICKS_PER_SECOND, bonus));
                default -> throw new IllegalArgumentException(
                        "unknown objective type \"" + json.get("type").getAsString() + "\"");
            }
        }
        return objectives;
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

    private static int requiredInt(JsonObject json, String key) {
        if (!json.has(key) || !json.get(key).isJsonPrimitive()) {
            throw new IllegalArgumentException("missing required \"" + key + "\"");
        }
        return json.get(key).getAsInt();
    }

    private static boolean optionalBool(JsonObject json, String key, boolean fallback) {
        return json.has(key) && json.get(key).isJsonPrimitive()
                ? json.get(key).getAsBoolean() : fallback;
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
