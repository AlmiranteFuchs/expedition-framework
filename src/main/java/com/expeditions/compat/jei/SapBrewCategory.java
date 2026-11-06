package com.expeditions.compat.jei;

import com.expeditions.registry.ModBlocks;
import com.expeditions.registry.ModFluids;
import com.expeditions.registry.ModItems;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * "Essence + knot + water bucket into a sap jar" — the brew is hardcoded in
 * {@code SapVesselBlock.useItemOn}, so the recipe card is a fixed singleton.
 */
final class SapBrewCategory extends AbstractRecipeCategory<SapBrewCategory.Brew> {
    /** Marker recipe — inputs/output are constants of the interaction. */
    record Brew() { }

    SapBrewCategory(IGuiHelper gui) {
        super(ExpeditionsJeiPlugin.SAP_BREW,
                Component.translatable("jei.expeditions.category.sap_brewing"),
                gui.createDrawableItemStack(new ItemStack(ModBlocks.SAP_JAR.get())),
                96, 38);
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, Brew recipe, IFocusGroup focuses) {
        builder.addInputSlot(4, 4).addItemStack(new ItemStack(Items.WATER_BUCKET))
                .setStandardSlotBackground();
        builder.addInputSlot(26, 4).addItemStack(new ItemStack(ModItems.ASSIMILATION_ESSENCE.get()))
                .setStandardSlotBackground();
        builder.addInputSlot(48, 4).addItemStack(new ItemStack(ModItems.RETURN_KNOT.get()))
                .setStandardSlotBackground();
        builder.addOutputSlot(74, 4).addFluidStack(ModFluids.SAP.get(), 1000)
                .setStandardSlotBackground();
    }
}
