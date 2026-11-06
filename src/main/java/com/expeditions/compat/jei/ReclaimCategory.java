package com.expeditions.compat.jei;

import com.expeditions.registry.ModBlocks;
import com.expeditions.registry.ModFluids;
import java.util.List;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * Reclaiming-basin tiers per dimension: sap in, a weighted draw out. The
 * output slot cycles the full pool so every find is reachable on hover.
 */
final class ReclaimCategory extends AbstractRecipeCategory<ReclaimCategory.Offer> {
    record Offer(String dimension, int sap, List<ItemStack> finds) { }

    ReclaimCategory(IGuiHelper gui) {
        super(ExpeditionsJeiPlugin.RECLAIMING,
                Component.translatable("jei.expeditions.category.reclaiming"),
                gui.createDrawableItemStack(new ItemStack(ModBlocks.RECLAIMING_BASIN.get())),
                150, 40);
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, Offer recipe, IFocusGroup focuses) {
        builder.addInputSlot(4, 12).addFluidStack(ModFluids.SAP.get(), recipe.sap())
                .setStandardSlotBackground();
        builder.addOutputSlot(44, 12).addItemStacks(recipe.finds())
                .setStandardSlotBackground();
    }

    @Override
    public void draw(Offer recipe, IRecipeSlotsView slotsView, GuiGraphics graphics,
            double mouseX, double mouseY) {
        var font = Minecraft.getInstance().font;
        graphics.drawString(font, Component.translatable("jei.expeditions.reclaiming.tier",
                recipe.sap()), 66, 16, 0xFF808080, false);
        graphics.drawString(font, Component.literal(recipe.dimension()), 4, 1, 0xFF606060, false);
    }
}
