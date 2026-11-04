package com.expeditions.client.mixin;

import com.expeditions.registry.ModAttachments;
import com.expeditions.registry.ModEffects;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Reuse vanilla's frozen-body shiver without setting frozen ticks or causing freezing damage. */
@Mixin(LivingEntityRenderer.class)
abstract class LivingEntityRendererMixin {
    @Inject(method="isShaking",at=@At("RETURN"),cancellable=true)
    private void expeditions$shiver(LivingEntity entity,CallbackInfoReturnable<Boolean> result) {
        if(entity instanceof Mob && entity.getData(ModAttachments.ASSIMILATED)>0
                && !entity.hasEffect(ModEffects.ASSIMILATION_DISRUPTION)) result.setReturnValue(true);
    }
}
