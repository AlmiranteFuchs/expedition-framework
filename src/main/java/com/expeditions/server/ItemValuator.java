package com.expeditions.server;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.neoforged.neoforge.common.Tags;

/**
 * Stat-driven item valuation for reward draws. Reads the item's own data
 * components and the NeoForge {@code c:} convention tags (gems, ingots, raw
 * materials...) so modded items rank on the same scale as vanilla ones with
 * no per-item table — a mod's gem lands in {@code c:gems} by convention and
 * scores like a diamond. The score is a coarse tier, not a price list:
 * sticks land near 1, food and materials in the single digits, equipment
 * highest.
 */
public final class ItemValuator {
    private ItemValuator() {
    }

    /** Value score of {@code stack}; 0 for empty, >= 1 otherwise. */
    public static int score(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }
        int score = 1;
        score += switch (stack.getRarity()) {
            case COMMON -> 0;
            case UNCOMMON -> 2;
            case RARE -> 4;
            case EPIC -> 8;
        };
        // Unstackables are almost always equipment — a tier on their own.
        if (stack.getMaxStackSize() == 1) {
            score += 3;
        }
        FoodProperties food = stack.get(DataComponents.FOOD);
        if (food != null) {
            // saturation() is already the effective saturation points
            // (nutrition-scaled), so it contributes at half weight.
            score += food.nutrition() / 2 + (int) (food.saturation() / 2);
        }
        // Precious materials by the c: tag convention (mods opt in).
        if (stack.is(Tags.Items.GEMS)) {
            score += 6;
        } else if (stack.is(Tags.Items.INGOTS) || stack.is(Tags.Items.RAW_MATERIALS)) {
            score += 4;
        } else if (stack.is(Tags.Items.NUGGETS) || stack.is(Tags.Items.DUSTS)
                || stack.is(Tags.Items.ORES)) {
            score += 2;
        }
        Integer maxDamage = stack.get(DataComponents.MAX_DAMAGE);
        if (maxDamage != null) {
            score += maxDamage / 128;
        }
        ItemAttributeModifiers attributes = stack.get(DataComponents.ATTRIBUTE_MODIFIERS);
        if (attributes != null) {
            double power = 0;
            for (ItemAttributeModifiers.Entry entry : attributes.modifiers()) {
                if (entry.modifier().amount() > 0) {
                    power += entry.modifier().amount();
                }
            }
            score += (int) Math.round(power);
        }
        return score;
    }
}
