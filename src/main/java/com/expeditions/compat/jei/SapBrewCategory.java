package com.expeditions.compat.jei;

import com.expeditions.registry.ModBlocks;
import com.expeditions.registry.ModFluids;
import com.expeditions.registry.ModItems;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Water into the jar, a knot steeps in it, essence settles the steep into
 * sap — the brew is hardcoded in {@code SapVesselBlock.useItemOn}, so the
 * recipe card is a fixed singleton laid out in click order.
 */
final class SapBrewCategory extends AbstractRecipeCategory<SapBrewCategory.Brew> {
    /** Marker recipe — inputs/output are constants of the interaction. */
    record Brew() { }

    SapBrewCategory(IGuiHelper gui) {
        super(ExpeditionsJeiPlugin.SAP_BREW,
                Component.translatable("jei.expeditions.category.sap_brewing"),
                gui.createDrawableItemStack(new ItemStack(ModBlocks.SAP_JAR.get())),
                122, 48);
    }

    @Override
    public void draw(Brew recipe, IRecipeSlotsView slots, GuiGraphics gui, double mouseX, double mouseY) {
        var font = Minecraft.getInstance().font;
        int y = 26;
        for (var line : font.split(Component.translatable("jei.expeditions.sap_brewing.steps"), 118)) {
            gui.drawString(font, line, 2, y, 0xff4d3626, false);
            y += 10;
        }
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, Brew recipe, IFocusGroup focuses) {
        builder.addInputSlot(4, 4).addItemStack(new ItemStack(Items.WATER_BUCKET))
                .setStandardSlotBackground();
        builder.addInputSlot(26, 4).addItemStack(new ItemStack(ModItems.RETURN_KNOT.get()))
                .setStandardSlotBackground();
        builder.addInputSlot(48, 4).addItemStack(new ItemStack(ModItems.ASSIMILATION_ESSENCE.get()))
                .setStandardSlotBackground();
        builder.addOutputSlot(99, 4).addFluidStack(ModFluids.SAP.get(), 1000)
                .setStandardSlotBackground();
    }
}
