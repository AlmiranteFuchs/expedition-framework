package com.expeditions.compat.jei;

import com.expeditions.ExpeditionsMod;
import com.expeditions.registry.ModBlocks;
import com.expeditions.registry.ModItems;
import com.expeditions.server.ReclaimingOffers;
import java.util.ArrayList;
import java.util.List;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** JEI integration — optional compile-time dep, loaded only when JEI runs. */
@JeiPlugin
public final class ExpeditionsJeiPlugin implements IModPlugin {
    static final RecipeType<SapBrewCategory.Brew> SAP_BREW =
            RecipeType.create(ExpeditionsMod.MOD_ID, "sap_brewing", SapBrewCategory.Brew.class);
    static final RecipeType<ReclaimCategory.Offer> RECLAIMING =
            RecipeType.create(ExpeditionsMod.MOD_ID, "reclaiming", ReclaimCategory.Offer.class);

    @Override
    public ResourceLocation getPluginUid() {
        return ResourceLocation.fromNamespaceAndPath(ExpeditionsMod.MOD_ID, "jei_plugin");
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        var gui = registration.getJeiHelpers().getGuiHelper();
        registration.addRecipeCategories(
                new SapBrewCategory(gui), new ReclaimCategory(gui));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        registration.addRecipes(SAP_BREW, List.of(new SapBrewCategory.Brew()));
        var level = Minecraft.getInstance().level;
        if (level != null) {
            List<ReclaimCategory.Offer> offers = new ArrayList<>();
            for (var key : level.registryAccess()
                    .registryOrThrow(Registries.DIMENSION).registryKeySet()) {
                String dimension = key.location().toString();
                for (var offer : ReclaimingOffers.forDimension(dimension, level)) {
                    if (!offer.finds().isEmpty()) {
                        offers.add(new ReclaimCategory.Offer(dimension, offer.sap(),
                                offer.finds().stream().map(ReclaimingOffers.Find::stack).toList()));
                    }
                }
            }
            registration.addRecipes(RECLAIMING, offers);
        }
        registration.addItemStackInfo(new ItemStack(ModItems.ASSIMILATION_ESSENCE.get()),
                Component.translatable("jei.expeditions.info.essence"));
        registration.addItemStackInfo(new ItemStack(ModBlocks.ESSENCE_CONDENSER.get()),
                Component.translatable("jei.expeditions.info.condenser"));
        registration.addItemStackInfo(new ItemStack(ModItems.RETURN_KNOT.get()),
                Component.translatable("jei.expeditions.info.return_knot"));
        registration.addItemStackInfo(new ItemStack(ModBlocks.FIELD_WARD.get()),
                Component.translatable("jei.expeditions.info.field_ward"));
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(new ItemStack(ModBlocks.SAP_JAR.get()), SAP_BREW);
        registration.addRecipeCatalyst(new ItemStack(ModBlocks.RECLAIMING_BASIN.get()), RECLAIMING);
    }
}
