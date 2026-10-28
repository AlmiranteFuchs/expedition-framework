package com.expeditions.server.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Optional;

/**
 * Per-dimension tuning overrides loaded from a single datapack file
 * ({@code data/<namespace>/expedition_dimensions/<path>.json}, where the
 * resource id IS the dimension id it tunes — e.g.
 * {@code data/expeditions/expedition_dimensions/minecraft/the_nether.json}
 * overrides {@code minecraft:the_nether}).
 *
 * <p>The file is a sparse object of sections holding snake_case knobs:
 * <pre>{@code
 * {
 *   "microbiome": { "enabled": true, "spawn_chance": 0.5, ... },
 *   "pressure":   { "per_run_start": 8, "buff_threshold": 40, ... }
 * }
 * }</pre>
 *
 * <p>Every field is optional — absent keys mean "use the global
 * {@code expeditions-server.toml} value", so an override file only names the
 * knobs it actually changes. Values are looked up by dot-path
 * ({@code "microbiome.enabled"}); malformed values resolve to empty and fall
 * back to the global, matching the loader's fault-isolation policy.
 */
public final class DimensionTuning {
    private final JsonObject json;

    public DimensionTuning(JsonObject json) {
        this.json = json;
    }

    public Optional<Boolean> bool(String path) {
        JsonElement e = at(path);
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isBoolean()
                ? Optional.of(e.getAsBoolean()) : Optional.empty();
    }

    public Optional<Integer> integer(String path) {
        JsonElement e = at(path);
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber()
                ? Optional.of(e.getAsInt()) : Optional.empty();
    }

    public Optional<Double> real(String path) {
        JsonElement e = at(path);
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber()
                ? Optional.of(e.getAsDouble()) : Optional.empty();
    }

    /** Walks {@code "section.key"} through nested objects; null if absent. */
    private JsonElement at(String path) {
        JsonElement cursor = json;
        for (String segment : path.split("\\.")) {
            if (cursor == null || !cursor.isJsonObject()) {
                return null;
            }
            cursor = cursor.getAsJsonObject().get(segment);
        }
        return cursor;
    }
}
