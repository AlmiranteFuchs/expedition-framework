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
            int roll = random.nextInt(finds.stream().mapToInt(Find::weight).sum());
            for (Find find : finds) {
                roll -= find.weight();
                if (roll < 0) return find.stack().copy();
            }
            throw new IllegalStateException("Invalid trace weights");
        }
    }

    private static final Set<String> RESTRICTED = Set.of("key", "keys", "quest", "trophy", "boss",
            "creative", "debug", "admin", "spawn", "spawner", "command", "structure",
            "barrier", "knowledge", "operator", "jigsaw", "portal", "frame", "core", "sigil",
            "dragon", "star", "totem", "pattern", "template", "knot", "bucket");

    public static List<Offer> forDimension(String dimension, Level level) {
        var id = ResourceLocation.tryParse(dimension);
        List<List<Find>> categories = List.of(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        var tiers = id != null && (id.equals(Level.NETHER.location()) || id.equals(Level.END.location()))
                ? List.of(salvageTag(id, "materials"), salvageTag(id, "curiosities"), salvageTag(id, "gear"))
                : List.<TagKey<Item>>of();
        if (id != null) for (var item : BuiltInRegistries.ITEM) {
            var key = BuiltInRegistries.ITEM.getKey(item);
            if (!key.getNamespace().equals(id.getNamespace()) || item instanceof BlockItem
                    || !item.isEnabled(level.enabledFeatures())) continue;
            if (item == Items.ENCHANTED_BOOK || item == Items.FILLED_MAP || item == Items.POTION
                    || item == Items.SPLASH_POTION || item == Items.LINGERING_POTION || item == Items.TIPPED_ARROW) continue;
            var words = key.getPath().split("[/_.-]+");
            if (java.util.Arrays.stream(words).anyMatch(RESTRICTED::contains)) continue;
            var stack = new ItemStack(item);
            if (stack.isEmpty() || stack.has(DataComponents.CONTAINER)
                    || stack.has(DataComponents.BUNDLE_CONTENTS) || stack.has(DataComponents.BLOCK_ENTITY_DATA)) continue;
            int score = ItemValuator.score(stack);
            boolean gear = stack.has(DataComponents.MAX_DAMAGE)
                    || !stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS,
                            net.minecraft.world.item.component.ItemAttributeModifiers.EMPTY).modifiers().isEmpty();
            boolean material = stack.is(Tags.Items.GEMS) || stack.is(Tags.Items.INGOTS)
                    || stack.is(Tags.Items.RAW_MATERIALS) || stack.is(Tags.Items.DUSTS) || stack.is(Tags.Items.NUGGETS);
            int category = gear ? 2 : material || (stack.getRarity() == Rarity.COMMON && stack.getMaxStackSize() > 1) ? 0 : 1;
            if (!tiers.isEmpty()) {
                category = -1;
                for (int tier = 0; tier < tiers.size(); tier++) {
                    if (stack.is(tiers.get(tier))) {
                        category = tier;
                        break;
                    }
                }
                if (category < 0) continue;
            }
            if (category == 0) stack.setCount(Math.min(stack.getMaxStackSize(), score <= 4 ? 8 : score <= 8 ? 4 : 2));
            categories.get(category).add(new Find(stack, Math.clamp(160 / Math.max(1, score), 1, 40)));
        }
        return List.of(new Offer(250, List.copyOf(categories.get(0))), new Offer(500, List.copyOf(categories.get(1))),
                new Offer(1000, List.copyOf(categories.get(2))));
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
        var knot = new ItemStack(com.expeditions.registry.ModItems.RETURN_KNOT.get());
        com.expeditions.item.ReturnKnotItem.bind(knot, dimension);
        return knot;
    }

    private ReclaimingOffers() { }
}
