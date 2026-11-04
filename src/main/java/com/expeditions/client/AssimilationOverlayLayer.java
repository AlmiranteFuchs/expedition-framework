package com.expeditions.client;

import com.expeditions.core.PulseWave;
import com.expeditions.registry.ModAttachments;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.world.entity.LivingEntity;

/** Sparse bark lesions over the native animated skin. No bright body-wide film. */
public class AssimilationOverlayLayer<T extends LivingEntity, M extends EntityModel<T>> extends RenderLayer<T, M> {
    public AssimilationOverlayLayer(RenderLayerParent<T, M> renderer) { super(renderer); }
    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, T entity,
            float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float yaw, float pitch) {
        if (entity.isInvisible()) return;
        int stage = entity.getData(ModAttachments.ASSIMILATED);
        if (stage <= 0) return;
        var textures = InfectionSkinTextures.forSkin(getTextureLocation(entity));
        if (textures.isEmpty()) return;
        float taken = Math.min(1, stage / 3.0f);
        float pulse = (float)PulseWave.amplitudeAt(entity.level().getGameTime() + partialTick,
                0, entity.getUUID().getMostSignificantBits());
        int overlay = LivingEntityRenderer.getOverlayCoords(entity, 0);
        getParentModel().renderToBuffer(pose, buffers.getBuffer(RenderType.entityTranslucent(textures.get().scars())), light,
                overlay, FastColor.ARGB32.color((int)(255 * (.60f + .30f * taken)), 235, 229, 216));
        // Small resin fissures are emissive. Cap RGB as well as alpha for additive blending.
        float heat = pulse * (.4f + .6f * taken);
        getParentModel().renderToBuffer(pose, buffers.getBuffer(RenderType.eyes(textures.get().embers())), light,
                overlay, FastColor.ARGB32.color((int)(44 * heat), (int)(140 * heat), (int)(78 * heat), (int)(28 * heat)));

        float left = (entity.getData(ModAttachments.ARRIVAL_GLOW_UNTIL)
                - entity.level().getGameTime() - partialTick) / ModAttachments.ARRIVAL_GLOW_TICKS;
        if (left > 0 && left <= 1) {
            float glow = Math.min(1, (1 - left) * 8) * left;
            getParentModel().renderToBuffer(pose, buffers.getBuffer(RenderType.entityTranslucent(textures.get().scars())),
                    LightTexture.FULL_BRIGHT, overlay, FastColor.ARGB32.color((int)(160 * glow), 255, 243, 219));
            getParentModel().renderToBuffer(pose, buffers.getBuffer(RenderType.eyes(textures.get().embers())),
                    LightTexture.FULL_BRIGHT, overlay, FastColor.ARGB32.color((int)(84 * glow),
                            (int)(210 * glow), (int)(123 * glow), (int)(46 * glow)));
        }
    }
}
