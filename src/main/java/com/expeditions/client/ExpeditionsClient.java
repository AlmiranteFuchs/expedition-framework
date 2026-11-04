package com.expeditions.client;

import com.expeditions.ExpeditionsMod;
import com.expeditions.registry.ModBlockEntities;
import com.expeditions.registry.ModBlocks;
import com.expeditions.registry.ModItems;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.item.CompassItemPropertyFunction;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.CompassItem;
import net.minecraft.world.item.component.LodestoneTracker;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.common.NeoForge;

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
        modEventBus.addListener((net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent event) ->
                event.registerFluidType(new net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions() {
                    @Override public ResourceLocation getStillTexture() {
                        return ResourceLocation.withDefaultNamespace("block/water_still");
                    }
                    @Override public ResourceLocation getFlowingTexture() {
                        return ResourceLocation.withDefaultNamespace("block/water_flow");
                    }
                    @Override public int getTintColor() { return 0xffbe7734; }
                }, com.expeditions.registry.ModFluids.SAP_TYPE.get()));
        modEventBus.addListener((net.neoforged.neoforge.client.event.RegisterMenuScreensEvent event) -> {
                event.register(com.expeditions.registry.ModMenus.EXPEDITION.get(), ExpeditionScreen::new);
                event.register(com.expeditions.registry.ModMenus.RECLAIMING.get(), ReclaimingScreen::new);
                event.register(com.expeditions.registry.ModMenus.RESONANCE.get(),ResonanceScreen::new);
        });
        modEventBus.addListener((EntityRenderersEvent.RegisterLayerDefinitions event) ->
                event.registerLayerDefinition(GuardianModel.LAYER, GuardianModel::createBodyLayer));
        modEventBus.addListener(ExpeditionsClient::onAddLayers);
        modEventBus.addListener((net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent event) ->
                event.registerReloadListener((net.minecraft.server.packs.resources.ResourceManagerReloadListener)
                        manager -> InfectionSkinTextures.clear()));
        modEventBus.addListener((net.neoforged.neoforge.client.event.RegisterGuiLayersEvent event) ->
                event.registerAboveAll(ResourceLocation.fromNamespaceAndPath("expeditions", "bloom_influence"), BloomVignette::render));
        modEventBus.addListener((net.neoforged.neoforge.client.event.RegisterGuiLayersEvent event) ->
                event.registerAboveAll(ResourceLocation.fromNamespaceAndPath("expeditions", "guided_bearing"), GuidedBearing::render));
        NeoForge.EVENT_BUS.addListener(CompassResonance::onClientTick);
        NeoForge.EVENT_BUS.addListener(InfectedMobEffects::onClientTick);
        NeoForge.EVENT_BUS.addListener(CompassResonance::onRenderHand);
        NeoForge.EVENT_BUS.addListener(ExpeditionsClient::onCompassTooltip);
    }

    private static void onCompassTooltip(net.neoforged.neoforge.event.entity.player.ItemTooltipEvent event) {
        var player = event.getEntity();
        if (player == null) return;
        var target = com.expeditions.item.ExpeditionCompassItem.localTarget(event.getItemStack(), player.level());
        if (target == null) return;
        var range = com.expeditions.core.CompassRange.estimate(
                player.position().distanceTo(net.minecraft.world.phys.Vec3.atCenterOf(target.pos())));
        event.getToolTip().add(net.minecraft.network.chat.Component.translatable(
                "tooltip.expeditions.compass_distance", range.minimum(), range.maximum()).withStyle(net.minecraft.ChatFormatting.GOLD));
        int direction = com.expeditions.core.CompassElevation.direction(target.pos().getY() - player.getY());
        event.getToolTip().add(net.minecraft.network.chat.Component.translatable("tooltip.expeditions.compass_height_"
                + (direction > 0 ? "above" : direction < 0 ? "below" : "level"))
                .withStyle(net.minecraft.ChatFormatting.GRAY));
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> com.expeditions.item.FieldAssayKitItem.setClientOpener(hand ->
                net.minecraft.client.Minecraft.getInstance().setScreen(new AssayScreen(hand))));
        event.enqueueWork(() -> com.expeditions.item.ExpeditionFieldJournalItem
                .setClientOpener(hand -> net.minecraft.client.Minecraft.getInstance()
                        .execute(() -> JournalBook.open(hand))));
        // The layer's translucent membrane mesh needs the translucent
        // render layer; the desaturated skin is drawn by the BER.
        event.enqueueWork(() -> ItemBlockRenderTypes.setRenderLayer(
                ModBlocks.ASSIMILATION_LAYER.get(), RenderType.translucent()));
        event.enqueueWork(() -> ItemBlockRenderTypes.setRenderLayer(
                ModBlocks.EXPEDITION_PORTAL.get(), RenderType.cutout()));
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
        event.registerEntityRenderer(com.expeditions.registry.ModEntities.ROOTBOUND_GUARDIAN.get(), GuardianRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.RESONANCE_MACHINE.get(),ResonanceRenderer::new);
        event.registerBlockEntityRenderer(
                ModBlockEntities.ASSIMILATION_LAYER.get(),
                AssimilationLayerRenderer::new);
        event.registerBlockEntityRenderer(
                ModBlockEntities.ASSIMILATION_HEART.get(),
                AssimilationHeartRenderer::new);
    }

    /**
     * Adds the assimilation overlay to every living-entity renderer —
     * vanilla or modded, any model — plus both player skin models, so
     * infection shows on players too. Renderers using custom pipelines
     * (geo renderers) can't take layers; flagged entities get the ember
     * particle fallback instead.
     */
    private static void onAddLayers(EntityRenderersEvent.AddLayers event) {
        for (EntityType<?> type : event.getEntityTypes()) {
            if (event.getRenderer(type)
                    instanceof LivingEntityRenderer<?, ?> renderer) {
                addOverlay(renderer);
            }
        }
        for (PlayerSkin.Model skin : event.getSkins()) {
            if (event.getSkin(skin)
                    instanceof LivingEntityRenderer<?, ?> renderer) {
                addOverlay(renderer);
            }
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <T extends LivingEntity, M extends EntityModel<T>> void addOverlay(
            LivingEntityRenderer<?, ?> renderer) {
        ((LivingEntityRenderer<T, M>) renderer).addLayer(
                new AssimilationOverlayLayer<>(
                        (LivingEntityRenderer<T, M>) renderer));
    }
}
