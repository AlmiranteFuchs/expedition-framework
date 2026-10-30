package com.expeditions.server.dimension;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/**
 * Registry of dimension adapters. Vanilla dimensions ship with built-in
 * adapters; {@link #register} exists so adapters for modded dimensions can be
 * added without touching the lifecycle code. Other loaded dimensions use a
 * conservative automatic adapter; explicit registrations always take priority.
 */
public final class ExpeditionAdapters {
    private static final Map<ResourceKey<Level>, ExpeditionDimensionAdapter> ADAPTERS =
            new ConcurrentHashMap<>();

    static {
        register(new OverworldAdapter());
        register(new NetherAdapter());
    }

    private ExpeditionAdapters() {
    }

    public static void register(ExpeditionDimensionAdapter adapter) {
        ADAPTERS.put(adapter.dimension(), adapter);
    }

    /** Remove an explicit override, restoring automatic terrain resolution. */
    public static void unregister(ResourceKey<Level> dimension) {
        ADAPTERS.remove(dimension);
    }

    /** The adapter for {@code level}'s dimension, if expeditions are supported. */
    public static Optional<ExpeditionDimensionAdapter> forLevel(ServerLevel level) {
        return Optional.of(ADAPTERS.getOrDefault(level.dimension(),
                new AutomaticDimensionAdapter(level.dimension())));
    }
}
