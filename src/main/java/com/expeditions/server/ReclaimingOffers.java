package com.expeditions.server;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.Items;
import net.minecraft.core.component.DataComponents;
import net.neoforged.neoforge.common.Tags;

public final class ReclaimingOffers {
    public record Find(ItemStack stack, int weight) { }

    public record Offer(int sap, List<Find> finds) {
        ItemStack sample() {
            return finds.stream().min(java.util.Comparator.comparingInt(Find::weight))
                    .map(find -> find.stack().copy()).orElse(ItemStack.EMPTY);
        }

        ItemStack draw(RandomSource random) {
            return drawExcept(random, List.of());
        }

        ItemStack drawExcept(RandomSource random, List<ItemStack> excluded) {
            var eligible = finds.stream().filter(find -> excluded.stream()
                    .noneMatch(stack -> stack.is(find.stack().getItem()))).toList();
            int weight = eligible.stream().mapToInt(Find::weight).sum();
            if (weight == 0) return ItemStack.EMPTY;
            int roll = random.nextInt(weight);
            for (Find find : eligible) {
                roll -= find.weight();
                if (roll < 0) return find.stack().copy();
            }
            throw new IllegalStateException("Invalid trace weights");
        }
    }

    private static final List<List<Item>> OVERWORLD = List.of(
            List.of(Items.IRON_INGOT, Items.COPPER_INGOT, Items.COAL, Items.REDSTONE, Items.LAPIS_LAZULI),
            List.of(Items.NAME_TAG, Items.SADDLE, Items.AMETHYST_SHARD, Items.GOLDEN_APPLE),
            List.of(Items.IRON_SWORD, Items.SHIELD, Items.CROSSBOW, Items.IRON_CHESTPLATE));

    private static final Set<String> RESTRICTED = Set.of("key", "keys", "quest", "trophy", "boss",
            "creative", "debug", "admin", "spawn", "spawner", "command", "structure",
            "barrier", "knowledge", "operator", "jigsaw", "portal", "frame", "core", "sigil",
            "dragon", "star", "totem", "pattern", "template", "knot", "bucket");

    public static List<Offer> forDimension(String dimension, Level level) {
        var id = ResourceLocation.tryParse(dimension);
        List<List<Find>> categories = List.of(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        var tiers = id != null
                ? List.of(salvageTag(id, "materials"), salvageTag(id, "curiosities"), salvageTag(id, "gear"))
                : List.<TagKey<Item>>of();
        var authored = tiers.stream().map(tag -> BuiltInRegistries.ITEM.getTag(tag)
                .filter(entries -> entries.size() > 0).isPresent()).toList();
        if (id != null) for (var item : BuiltInRegistries.ITEM) {
            var key = BuiltInRegistries.ITEM.getKey(item);
            if (item instanceof BlockItem
                    || !item.isEnabled(level.enabledFeatures())) continue;
            if (item == Items.ENCHANTED_BOOK || item == Items.FILLED_MAP || item == Items.POTION
                    || item == Items.SPLASH_POTION || item == Items.LINGERING_POTION || item == Items.TIPPED_ARROW) continue;
            var words = key.getPath().split("[/_.-]+");
            if (java.util.Arrays.stream(words).anyMatch(RESTRICTED::contains)) continue;
            var stack = new ItemStack(item);
            if (stack.isEmpty() || stack.has(DataComponents.CONTAINER)
                    || stack.has(DataComponents.BUNDLE_CONTENTS) || stack.has(DataComponents.BLOCK_ENTITY_DATA)) continue;
            boolean gear = stack.has(DataComponents.MAX_DAMAGE)
                    || !stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS,
                            net.minecraft.world.item.component.ItemAttributeModifiers.EMPTY).modifiers().isEmpty();
            boolean material = stack.is(Tags.Items.GEMS) || stack.is(Tags.Items.INGOTS)
                    || stack.is(Tags.Items.RAW_MATERIALS) || stack.is(Tags.Items.DUSTS) || stack.is(Tags.Items.NUGGETS);
            int category = -1;
            for (int tier = 0; tier < tiers.size(); tier++) {
                if (authored.get(tier) && stack.is(tiers.get(tier))) {
                    category = tier;
                    break;
                }
            }
            if (category < 0 && id.equals(Level.OVERWORLD.location())) {
                for (int tier = 0; tier < OVERWORLD.size(); tier++) {
                    if (!authored.get(tier) && OVERWORLD.get(tier).contains(item)) category = tier;
                }
            } else if (category < 0 && !id.getNamespace().equals("minecraft")
                    && key.getNamespace().equals(id.getNamespace())) {
                int discovered = material ? 0 : gear && stack.isDamageableItem() ? 2
                        : stack.getRarity() == Rarity.UNCOMMON && stack.isStackable() ? 1 : -1;
                if (discovered >= 0 && !authored.get(discovered)) category = discovered;
            }
            if (category < 0) continue;
            int score = ItemValuator.score(stack);
            if (category == 0) stack.setCount(Math.min(stack.getMaxStackSize(), score <= 5 ? 4 : 2));
            categories.get(category).add(new Find(stack, Math.clamp(160 / Math.max(1, score), 1, 40)));
        }
        var materials = categories.getFirst();
        var curiosities = categories.get(1);
        var gear = categories.get(2);
        backfill(materials, 2, List.of(new ItemStack(com.expeditions.registry.ModItems.DORMANT_FLAKE.get(), 4),
                new ItemStack(Items.COPPER_INGOT, 4)));
        if (id != null && !id.getNamespace().equals("minecraft")) {
            backfill(curiosities, 2, List.of(new ItemStack(Items.NAME_TAG), new ItemStack(Items.AMETHYST_SHARD, 2)));
            backfill(gear, 1, List.of(new ItemStack(Items.IRON_PICKAXE)));
        }
        return List.of(offer(250, materials), offer(500, curiosities), offer(1000, gear));
    }

    private static void backfill(List<Find> finds, int minimum, List<ItemStack> supplies) {
        for (var supply : supplies) {
            if (finds.size() >= minimum) return;
            if (finds.stream().noneMatch(find -> find.stack().is(supply.getItem()))) {
                finds.add(new Find(supply, 20));
            }
        }
    }

    private static Offer offer(int sap, List<Find> finds) {
        return new Offer(sap, finds.stream().limit(12).toList());
    }

    private static TagKey<Item> salvageTag(ResourceLocation dimension, String tier) {
        return TagKey.create(Registries.ITEM,
                ResourceLocation.fromNamespaceAndPath("expeditions", "salvage/" + dimension.getPath() + "/" + tier));
    }

    static ItemStack cacheReward(String dimension, ServerLevel level) {
        var curiosities = forDimension(dimension, level).get(1);
        if (!curiosities.finds().isEmpty()) {
            return curiosities.draw(level.random);
        }
        return new ItemStack(Items.NAME_TAG);
    }

    private ReclaimingOffers() { }
}
