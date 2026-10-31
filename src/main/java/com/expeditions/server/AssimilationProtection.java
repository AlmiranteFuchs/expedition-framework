package com.expeditions.server;

import com.expeditions.registry.ModEffects;
import com.expeditions.registry.ModItems;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.capabilities.EntityCapability;
import net.neoforged.neoforge.items.IItemHandler;

/** Curios' documented item-handler capability keeps compatibility optional and dependency-free. */
public final class AssimilationProtection {
    private static final EntityCapability<IItemHandler,Void> CURIOS = EntityCapability.createVoid(
            ResourceLocation.parse("curios:item_handler"),IItemHandler.class);
    private AssimilationProtection() { }

    public static boolean protectedFromInfection(LivingEntity entity) {
        return entity.hasEffect(ModEffects.ASSIMILATION_DISRUPTION) || wearingCharm(entity);
    }

    public static boolean wearingCharm(LivingEntity entity) {
        if(entity.getOffhandItem().is(ModItems.DISRUPTION_CHARM.get())) return true;
        IItemHandler slots=entity.getCapability(CURIOS);
        if(slots!=null) for(int i=0;i<Math.min(256,slots.getSlots());i++)
            if(slots.getStackInSlot(i).is(ModItems.DISRUPTION_CHARM.get())) return true;
        return false;
    }

    public static void tick(LivingEntity entity) {
        if(entity.tickCount%10!=0 || !wearingCharm(entity)) return;
        var existing=entity.getEffect(ModEffects.ASSIMILATION_DISRUPTION);
        if(existing==null || existing.getDuration()<25)
            entity.addEffect(new MobEffectInstance(ModEffects.ASSIMILATION_DISRUPTION,35,0,true,false,true));
    }
}
