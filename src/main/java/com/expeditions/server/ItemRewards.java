package com.expeditions.server;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Dimension-aware item pools for bonus drops and token redemption. A
 * dimension's pool is the {@code expeditions:reward_items/<dimension path>}
 * item tag — plain datapack surface, so modpacks extend the vanilla pools
 * and adapters' modded dimensions get their own tag; dimensions without one
 * fall back to {@code reward_items/default} (and to no drops if even that
 * is empty).
 *
 * <p>Draws are score-weighted by {@link ItemValuator}: bigger budgets reach
 * higher tiers more often, and leftover budget is spent on bulk stacks of
 * cheap items, so the draw always fully consumes the budget.
 */
public final class ItemRewards {
    private static final ResourceLocation DEFAULT_TAG =
            ResourceLocation.fromNamespaceAndPath("expeditions", "reward_items/default");

    private ItemRewards() {
    }

    /** Items in the reward pool for {@code dimensionId} (empty when none configured). */
    public static List<Item> pool(String dimensionId) {
        ResourceLocation parsed = ResourceLocation.tryParse(dimensionId);
        if (parsed != null) {
            List<Item> pool = itemsOf(ResourceLocation.fromNamespaceAndPath(
                    "expeditions", "reward_items/" + parsed.getPath()));
            if (!pool.isEmpty()) {
                return pool;
            }
        }
        return itemsOf(DEFAULT_TAG);
    }

    private static List<Item> itemsOf(ResourceLocation tagId) {
        return BuiltInRegistries.ITEM.getTag(TagKey.create(Registries.ITEM, tagId))
                .map(set -> set.stream().map(Holder::value).toList())
                .orElse(List.of());
    }

    /**
     * Converts an item-value budget into drops: repeatedly draws a
     * score-weighted affordable item and grants a stack sized to what the
     * leftover budget buys, until nothing affordable remains (the cheapest
     * pool item is granted once as a floor) or {@code maxDrops} stacks were
     * drawn.
     */
    public static List<ItemStack> draw(List<Item> pool, int budget,
            RandomSource random, int maxDrops) {
        List<ItemStack> drops = new ArrayList<>();
        if (pool.isEmpty() || maxDrops <= 0) {
            return drops;
        }
        Map<Item, Integer> scores = new IdentityHashMap<>();
        for (Item item : pool) {
            scores.put(item, ItemValuator.score(new ItemStack(item)));
        }
        int remaining = Math.max(0, budget);
        while (remaining > 0 && drops.size() < maxDrops) {
            List<Item> affordable = new ArrayList<>();
            int totalWeight = 0;
            for (Item item : pool) {
                if (scores.get(item) <= remaining) {
                    affordable.add(item);
                    totalWeight += Math.max(1, scores.get(item));
                }
            }
            if (affordable.isEmpty()) {
                drops.add(new ItemStack(pool.stream()
                        .min(Comparator.comparingInt(scores::get)).orElseThrow()));
                break;
            }
            int roll = random.nextInt(totalWeight);
            Item picked = affordable.get(0);
            for (Item item : affordable) {
                roll -= Math.max(1, scores.get(item));
                if (roll < 0) {
                    picked = item;
                    break;
                }
            }
            int score = scores.get(picked);
            int qty = Math.max(1, Math.min(new ItemStack(picked).getMaxStackSize(),
                    remaining / score));
            drops.add(new ItemStack(picked, qty));
            remaining -= score;
        }
        return drops;
    }
}
