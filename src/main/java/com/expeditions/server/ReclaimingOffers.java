package com.expeditions.server;

import java.util.List;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

final class ReclaimingOffers {
    record Offer(int sap, Item item, int count, Item feed, int feedCount, Item extra, int extraCount) {
        ItemStack stack() { return new ItemStack(item, count); }
        ItemStack bonus() { return new ItemStack(extra, extraCount); }
    }

    private static final List<Offer> OVERWORLD = List.of(
            new Offer(250, Items.COBBLESTONE, 32, Items.AIR, 0, Items.OAK_LOG, 8),
            new Offer(500, Items.IRON_INGOT, 8, Items.RAW_IRON, 6, Items.COAL, 4),
            new Offer(1000, Items.IRON_PICKAXE, 1, Items.IRON_INGOT, 3, Items.TORCH, 8));
    private static final List<Offer> NETHER = List.of(
            new Offer(250, Items.NETHERRACK, 32, Items.AIR, 0, Items.BASALT, 12),
            new Offer(500, Items.GLOWSTONE, 8, Items.GLOWSTONE_DUST, 24, Items.QUARTZ, 4),
            new Offer(1000, Items.FIRE_CHARGE, 6, Items.BLAZE_POWDER, 2, Items.OBSIDIAN, 2));
    private static final List<Offer> END = List.of(
            new Offer(250, Items.END_STONE, 32, Items.AIR, 0, Items.CHORUS_FRUIT, 6),
            new Offer(500, Items.PURPUR_BLOCK, 16, Items.POPPED_CHORUS_FRUIT, 12, Items.ENDER_PEARL, 2),
            new Offer(1000, Items.END_ROD, 8, Items.BLAZE_ROD, 2, Items.PURPUR_BLOCK, 8));
    private static final List<Offer> TRAIL = List.of(
            new Offer(250, Items.OAK_LOG, 16, Items.AIR, 0, Items.BREAD, 4),
            new Offer(500, Items.GLASS, 16, Items.SAND, 12, Items.TORCH, 8),
            new Offer(1000, Items.SHIELD, 1, Items.IRON_INGOT, 2, Items.BREAD, 4));

    static List<Offer> forDimension(String dimension) {
        return switch (dimension) {
            case "minecraft:overworld" -> OVERWORLD;
            case "minecraft:the_nether" -> NETHER;
            case "minecraft:the_end" -> END;
            default -> TRAIL;
        };
    }

    private ReclaimingOffers() { }
}
