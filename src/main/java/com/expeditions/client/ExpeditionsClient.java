package com.expeditions.client;

import com.expeditions.ExpeditionsMod;
import com.expeditions.registry.ModBlockEntities;
import com.expeditions.registry.ModBlocks;
import com.expeditions.registry.ModItems;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.item.CompassItemPropertyFunction;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.CompassItem;
import net.minecraft.world.item.component.LodestoneTracker;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * Client-only setup: registers the compass needle property for the expedition
 * compass so its {@code LODESTONE_TRACKER} component drives the needle like a
 * lodestone compass. Kept in a separate {@code @Mod} class so the dedicated
 * server never loads client code.
 */
@Mod(value = ExpeditionsMod.MOD_ID, dist = Dist.CLIENT)
public class ExpeditionsClient {
    public ExpeditionsClient(IEventBus modEventBus, ModContainer container) {
        modEventBus.addListener(ExpeditionsClient::onClientSetup);
        modEventBus.addListener(ExpeditionsClient::onRegisterRenderers);
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        // The layer's translucent membrane mesh needs the translucent
        // render layer; the desaturated skin is drawn by the BER.
        event.enqueueWork(() -> ItemBlockRenderTypes.setRenderLayer(
                ModBlocks.ASSIMILATION_LAYER.get(), RenderType.translucent()));
        event.enqueueWork(() -> ItemProperties.register(
                ModItems.EXPEDITION_COMPASS.get(),
                ResourceLocation.withDefaultNamespace("angle"),
                new CompassItemPropertyFunction((level, stack, entity) -> {
                    LodestoneTracker tracker = stack.get(DataComponents.LODESTONE_TRACKER);
                    return tracker != null
                            ? tracker.target().orElse(null)
                            : CompassItem.getSpawnPosition(level);
                })));
    }

    private static void onRegisterRenderers(
            EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(
                ModBlockEntities.ASSIMILATION_LAYER.get(),
                AssimilationLayerRenderer::new);
    }
}
