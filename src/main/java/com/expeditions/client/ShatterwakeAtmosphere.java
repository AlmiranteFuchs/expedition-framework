package com.expeditions.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.FogType;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.joml.Vector3f;

final class ShatterwakeAtmosphere {
    private static final ResourceLocation DIMENSION = ResourceLocation.parse("expeditions:shatterwake");
    private static final DustParticleOptions EMBER = new DustParticleOptions(new Vector3f(.78f, .48f, .22f), .65f);

    private ShatterwakeAtmosphere() { }

    static void onFog(ViewportEvent.RenderFog event) {
        var camera = event.getCamera().getEntity();
        if (camera == null || !camera.level().dimension().location().equals(DIMENSION)
                || event.getMode() != FogRenderer.FogMode.FOG_TERRAIN || event.getType() != FogType.NONE
                || event.getFarPlaneDistance() < 40) return;
        float far = Math.min(82, event.getFarPlaneDistance());
        event.setFarPlaneDistance(far);
        event.setNearPlaneDistance(Math.min(16, far * .32f));
        event.setCanceled(true);
    }

    static void onFogColor(ViewportEvent.ComputeFogColor event) {
        var camera = event.getCamera();
        var entity = camera.getEntity();
        if (entity == null || !entity.level().dimension().location().equals(DIMENSION)
                || camera.getFluidInCamera() != FogType.NONE) return;
        // Keep silhouettes against a warm, dark haze at the fixed night hour.
        event.setRed(Math.max(.17f, event.getRed()));
        event.setGreen(Math.max(.155f, event.getGreen()));
        event.setBlue(Math.max(.14f, event.getBlue()));
    }

    static void onTick(ClientTickEvent.Post event) {
        var minecraft = Minecraft.getInstance();
        var level = minecraft.level;
        var camera = minecraft.getCameraEntity();
        if (level == null || camera == null || minecraft.isPaused()
                || !level.dimension().location().equals(DIMENSION) || camera.tickCount % 7 != 0) return;
        var random = level.random;
        var eye = camera.getEyePosition();
        double x = eye.x + (random.nextDouble() - .5) * 24;
        double y = eye.y + (random.nextDouble() - .5) * 10;
        double z = eye.z + (random.nextDouble() - .5) * 24;
        if (level.isEmptyBlock(net.minecraft.core.BlockPos.containing(x, y, z))) {
            level.addParticle(EMBER, x, y, z, .009, .012, -.004);
        }
    }
}
