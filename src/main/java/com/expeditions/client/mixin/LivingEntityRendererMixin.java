package com.expeditions.client.mixin;

import com.expeditions.registry.ModAttachments;
import com.expeditions.registry.ModEffects;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Render a broken rhythm of short body shudders without affecting entity physics. */
@Mixin(LivingEntityRenderer.class)
abstract class LivingEntityRendererMixin {
    @Inject(method="setupRotations",at=@At("HEAD"))
    private void expeditions$shiver(LivingEntity entity, PoseStack pose, float bob, float yaw,
            float partialTick, float scale, CallbackInfo info) {
        if (!(entity instanceof Mob) || !entity.isAlive()
                || entity.hasEffect(ModEffects.ASSIMILATION_DISRUPTION)) return;
        int stage = entity.getData(ModAttachments.ASSIMILATED);
        if (stage <= 0) return;
        float time = entity.tickCount + partialTick + Math.floorMod(entity.getId(), 37);
        double tremor = Math.sin(time * 2.8) * (0.55 + 0.45 * Math.sin(time * 0.39));
        float strength = 2.2f + 1.25f * Math.min(stage, 3);
        pose.mulPose(Axis.YP.rotationDegrees((float) tremor * strength));
        pose.mulPose(Axis.ZP.rotationDegrees((float) Math.sin(time * 3.6) * strength * 0.55f));
    }
}
