package com.expeditions.client;

import com.expeditions.registry.ModAttachments;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Mob;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.joml.Vector3f;

/** Local drifting spores, including mobs whose renderer cannot accept our skin layer. */
final class InfectedMobEffects {
    private static final DustParticleOptions SPORE = new DustParticleOptions(new Vector3f(.76f, .72f, .64f), .65f);
    private InfectedMobEffects() { }

    static void onClientTick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.isPaused()) return;
        int budget = 4;
        for (var entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof Mob mob) || !mob.isAlive() || mob.isInvisible()
                    || mob.distanceToSqr(mc.player) > 24 * 24) continue;
            int stage = mob.getData(ModAttachments.ASSIMILATED);
            if (stage <= 0) continue;
            int interval = stage >= 3 ? 11 : stage == 2 ? 19 : 29;
            if ((mob.tickCount + mob.getId()) % interval != 0) continue;
            var random = mc.level.random;
            double x = mob.getX() + (random.nextDouble() - .5) * mob.getBbWidth();
            double y = mob.getY() + mob.getBbHeight() * (.45 + random.nextDouble() * .45);
            double z = mob.getZ() + (random.nextDouble() - .5) * mob.getBbWidth();
            mc.level.addParticle(SPORE, x, y, z, 0, .022, 0);
            if (--budget == 0) return;
            mc.level.addParticle(ParticleTypes.WHITE_ASH, x, y, z, 0, .035, 0);
            if (--budget == 0) return;
        }
    }
}
