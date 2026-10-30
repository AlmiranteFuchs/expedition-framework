package com.expeditions.server;

import com.expeditions.ExpeditionsConfig;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.util.random.WeightedEntry;
import net.minecraft.util.random.WeightedRandom;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.SpawnPlacementType;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.biome.MobSpawnSettings;

/**
 * What a horde spawns, resolved smart-first: the pool is the biome's own
 * natural hostile spawn categories at the horde's position — dimension-, biome-
 * and mod-aware by construction (deserts roll husks, modded biomes roll the
 * mod's registered mobs) with no configuration needed. Explicit control is
 * still possible: a non-empty {@code expeditions:horde_mobs/<dimension path>}
 * entity tag replaces the natural pool entirely, {@code horde_mobs/default}
 * covers dimensions with no local combat mobs, and the config
 * {@code expeditions.challenges.hordeMob} id is the final fallback.
 */
public final class HordePools {
    private static final ResourceLocation DEFAULT_TAG =
            ResourceLocation.fromNamespaceAndPath("expeditions", "horde_mobs/default");

    private HordePools() {
    }

    /**
     * Rolls one horde type for a spawn near {@code pos}: the biome's natural
     * spawn weights decide (or a uniform pick from an overriding tag pool).
     */
    public static EntityType<?> pick(ServerLevel level, BlockPos pos,
            String dimensionId, RandomSource random) {
        List<EntityType<?>> override = overridePool(dimensionId);
        if (!override.isEmpty()) {
            return override.get(random.nextInt(override.size()));
        }
        List<WeightedEntry.Wrapper<EntityType<?>>> natural = naturalPool(level, pos);
        if (!natural.isEmpty()) {
            return WeightedRandom.getRandomItem(random, natural)
                    .map(WeightedEntry.Wrapper::data).orElseThrow();
        }

        List<EntityType<?>> fallback = tagTypes(DEFAULT_TAG);
        if (!fallback.isEmpty()) {
            return fallback.get(random.nextInt(fallback.size()));
        }
        return BuiltInRegistries.ENTITY_TYPE.get(
                ResourceLocation.parse(ExpeditionsConfig.challengeHordeMob()));
    }

    /** Every type {@link #pick} may return — for tests and diagnostics. */
    public static List<EntityType<?>> candidates(ServerLevel level, BlockPos pos,
            String dimensionId) {
        List<EntityType<?>> override = overridePool(dimensionId);
        if (!override.isEmpty()) {
            return override;
        }
        List<EntityType<?>> natural = naturalPool(level, pos).stream()
                .map(WeightedEntry.Wrapper::data).toList();
        if (!natural.isEmpty()) {
            return natural;
        }
        List<EntityType<?>> fallback = tagTypes(DEFAULT_TAG);
        return fallback.isEmpty()
                ? List.of(BuiltInRegistries.ENTITY_TYPE.get(
                        ResourceLocation.parse(ExpeditionsConfig.challengeHordeMob())))
                : fallback;
    }

    /** The explicit per-dimension tag override; empty when unconfigured. */
    private static List<EntityType<?>> overridePool(String dimensionId) {
        ResourceLocation parsed = ResourceLocation.tryParse(dimensionId);
        return parsed == null ? List.of()
                : tagTypes(ResourceLocation.fromNamespaceAndPath(
                        "expeditions", "horde_mobs/" + parsed.getPath()));
    }

    /**
     * All hostile categories, including NeoForge enum extensions used by
     * dimension mods. Biomes without hostiles can supply neutral combat mobs
     * (e.g. bees), but never passive livestock. Minus types that
     * can't fight on land (water/lava-spawn placements) — a drowned or
     * strider rolled onto dry ground is a bug, not variety. Types with no
     * registered placement (some modded mobs) are assumed land-capable.
     */
    private static List<WeightedEntry.Wrapper<EntityType<?>>> naturalPool(
            ServerLevel level, BlockPos pos) {
        List<WeightedEntry.Wrapper<EntityType<?>>> pool = new ArrayList<>();
        MobSpawnSettings settings = level.getBiome(pos).value().getMobSettings();
        for (MobCategory category : settings.getSpawnerTypes()) {
            if (category.isFriendly()) {
                continue;
            }
            for (MobSpawnSettings.SpawnerData spawn : settings.getMobs(category).unwrap()) {
                if (canFightOnLand(spawn.type)) {
                    pool.add(WeightedEntry.wrap(spawn.type, spawn.getWeight().asInt()));
                }
            }
        }
        if (pool.isEmpty()) {
            for (MobSpawnSettings.SpawnerData spawn : settings.getMobs(MobCategory.CREATURE).unwrap()) {
                if (canFightOnLand(spawn.type)
                        && spawn.type.create(level) instanceof Mob mob
                        && mob instanceof NeutralMob) {
                    pool.add(WeightedEntry.wrap(spawn.type, spawn.getWeight().asInt()));
                }
            }
        }
        return pool;
    }

    private static boolean canFightOnLand(EntityType<?> type) {
        SpawnPlacementType placement = SpawnPlacements.getPlacementType(type);
        return placement != SpawnPlacementTypes.IN_WATER
                && placement != SpawnPlacementTypes.IN_LAVA;
    }

    private static List<EntityType<?>> tagTypes(ResourceLocation tagId) {
        return BuiltInRegistries.ENTITY_TYPE
                .getTag(TagKey.create(Registries.ENTITY_TYPE, tagId))
                .map(set -> set.stream().map(Holder::value).toList())
                .orElse(List.of());
    }
}
