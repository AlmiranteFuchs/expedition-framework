package com.expeditions.server;

import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;

/**
 * Dimension-aware mob pools for checkpoint hordes. A dimension's pool is the
 * {@code expeditions:horde_mobs/<dimension path>} entity tag — plain datapack
 * surface, so modpacks extend the vanilla pools and adapter-supported modded
 * dimensions get their own tag; dimensions without one fall back to
 * {@code horde_mobs/default}, and a completely absent pool falls back to the
 * {@code expeditions.challenges.hordeMob} config id.
 */
public final class HordePools {
    private static final ResourceLocation DEFAULT_TAG =
            ResourceLocation.fromNamespaceAndPath("expeditions", "horde_mobs/default");

    private HordePools() {
    }

    /** Entity types in the horde pool for {@code dimensionId} (empty when none configured). */
    public static List<EntityType<?>> pool(String dimensionId) {
        ResourceLocation parsed = ResourceLocation.tryParse(dimensionId);
        if (parsed != null) {
            List<EntityType<?>> pool = typesOf(ResourceLocation.fromNamespaceAndPath(
                    "expeditions", "horde_mobs/" + parsed.getPath()));
            if (!pool.isEmpty()) {
                return pool;
            }
        }
        return typesOf(DEFAULT_TAG);
    }

    private static List<EntityType<?>> typesOf(ResourceLocation tagId) {
        return BuiltInRegistries.ENTITY_TYPE
                .getTag(TagKey.create(Registries.ENTITY_TYPE, tagId))
                .map(set -> set.stream().map(Holder::value).toList())
                .orElse(List.of());
    }
}
