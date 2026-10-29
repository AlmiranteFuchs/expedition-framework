package com.expeditions.client;

import com.expeditions.core.PulseWave;
import com.expeditions.registry.ModAttachments;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.world.entity.LivingEntity;

/**
 * The assimilated skin: registered on every {@link LivingEntityRenderer}
 * (vanilla, ours, or a mod's — the layer draws the entity's own model, so
 * any shape gets it) and on the player renderers, so an infected player
 * wears their stage visibly. Gated per-entity on the synced
 * {@code expeditions:assimilated} attachment — a clean mob renders nothing.
 *
 * <p>Three passes over the already-animated parent model, the membrane's
 * palette transplanted to flesh: a bone-pale film that deepens with the
 * stage, pale capillary veins riding a per-entity lub-dub phase, and an
 * ember pass that ignites on the thump. Entities whose renderer can't
 * carry layers (custom render pipelines) still get the ember particle
 * fallback server-side.
 */
public class AssimilationOverlayLayer<T extends LivingEntity, M extends EntityModel<T>>
        extends RenderLayer<T, M> {
    private static final ResourceLocation FILM =
            ResourceLocation.fromNamespaceAndPath("expeditions", "assimilation/film");
    private static volatile boolean filmCreated;

    public AssimilationOverlayLayer(RenderLayerParent<T, M> renderer) {
        super(renderer);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffers,
            int packedLight, T entity, float limbSwing, float limbSwingAmount,
            float partialTick, float ageInTicks, float netHeadYaw,
            float headPitch) {
        if (entity.isInvisible()) {
            return;
        }
        int stage = entity.getData(ModAttachments.ASSIMILATED);
        if (stage <= 0) {
            return;
        }
        float taken = Math.min(1.0f, stage / 3.0f);
        // Each body beats on its own phase, seeded from its UUID — a horde
        // ripples rather than strobing.
        double time = entity.level().getGameTime() + partialTick;
        float amp = (float) PulseWave.amplitudeAt(
                time, 0, entity.getUUID().getMostSignificantBits());
        M model = getParentModel(); // animated by the base pass this frame
        int overlay = LivingEntityRenderer.getOverlayCoords(entity, 0.0f);

        VertexConsumer film = buffers.getBuffer(
                RenderType.entityTranslucent(filmTexture()));
        model.renderToBuffer(poseStack, film, packedLight, overlay,
                FastColor.ARGB32.color(
                        (int) (255 * (0.10f + 0.24f * taken)), 228, 222, 208));
        VertexConsumer veins = buffers.getBuffer(
                RenderType.entityTranslucent(TraceTexture.get()));
        model.renderToBuffer(poseStack, veins, packedLight, overlay,
                FastColor.ARGB32.color(
                        (int) (255 * Math.min(1.0f,
                                0.30f + 0.55f * amp * (0.5f + 0.5f * taken))),
                        255, 255, 255));
        VertexConsumer ember = buffers.getBuffer(
                RenderType.eyes(TraceTexture.get()));
        model.renderToBuffer(poseStack, ember, packedLight, overlay,
                FastColor.ARGB32.color(
                        (int) (255 * 0.55f * amp * (0.4f + 0.6f * taken)),
                        255, 140, 38));
    }

    /** A flat bone-colored film texture for the bleach pass. */
    private static ResourceLocation filmTexture() {
        if (!filmCreated) {
            filmCreated = true;
            NativeImage img = new NativeImage(2, 2, false);
            for (int y = 0; y < 2; y++) {
                for (int x = 0; x < 2; x++) {
                    img.setPixelRGBA(x, y, 0xFFFFFFFF);
                }
            }
            Minecraft.getInstance().getTextureManager()
                    .register(FILM, new DynamicTexture(img));
        }
        return FILM;
    }
}
