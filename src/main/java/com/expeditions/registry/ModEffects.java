package com.expeditions.registry;

import com.expeditions.ExpeditionsMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEffects {
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT,ExpeditionsMod.MOD_ID);
    public static final DeferredHolder<MobEffect,MobEffect> ASSIMILATION_DISRUPTION = EFFECTS.register(
            "assimilation_disruption",()->new MobEffect(MobEffectCategory.BENEFICIAL,0xc6b98f) { });
    private ModEffects() { }
}
