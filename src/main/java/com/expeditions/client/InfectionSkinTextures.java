package com.expeditions.client;

import com.mojang.blaze3d.platform.NativeImage;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

/** Masks lesions to the host skin's alpha so empty hats, wings and cutouts stay empty. */
public final class InfectionSkinTextures {
    public record Pair(ResourceLocation scars, ResourceLocation embers) { }
    private static final Map<ResourceLocation, Optional<Pair>> CACHE = new HashMap<>();
    private InfectionSkinTextures() { }
    public static void clear() {
        var textures = Minecraft.getInstance().getTextureManager();
        CACHE.values().forEach(pair -> pair.ifPresent(value -> {
            textures.release(value.scars()); textures.release(value.embers());
        }));
        CACHE.clear();
    }
    public static Optional<Pair> forSkin(ResourceLocation skin) {
        var known = CACHE.get(skin);
        if (known != null) return known;
        if (CACHE.size() >= 256) return Optional.empty();
        var mc = Minecraft.getInstance();
        Optional<Pair> result;
        try (var stream = mc.getResourceManager().open(skin); var host = NativeImage.read(stream)) {
            int id = CACHE.size();
            var scars = create(host, "infection_scars", "scars_" + id);
            var embers = create(host, "infection_embers", "embers_" + id);
            result = Optional.of(new Pair(scars, embers));
        } catch (java.io.IOException e) {
            // Downloaded/dynamic skins have no resource image. Preserve their
            // transparency rather than paint opaque pixels over unknown UVs.
            result = Optional.empty();
        }
        CACHE.put(skin, result);
        return result;
    }
    private static ResourceLocation create(NativeImage host, String asset, String name) throws java.io.IOException {
        var mc = Minecraft.getInstance();
        try (var stream = mc.getResourceManager().open(ResourceLocation.parse("expeditions:textures/entity/" + asset + ".png"));
                var stencil = NativeImage.read(stream)) {
            int w = Math.min(256, host.getWidth()), h = Math.min(256, host.getHeight());
            var image = new NativeImage(w,h,false);
            for (int y=0;y<h;y++) for (int x=0;x<w;x++) {
                int color = stencil.getPixelRGBA(x*stencil.getWidth()/w,y*stencil.getHeight()/h);
                int alpha = (color>>>24)*(host.getPixelRGBA(x*host.getWidth()/w,y*host.getHeight()/h)>>>24)/255;
                image.setPixelRGBA(x,y,alpha<<24 | color&0xffffff);
            }
            var id = ResourceLocation.parse("expeditions:infection/" + name);
            mc.getTextureManager().register(id,new DynamicTexture(image));
            return id;
        }
    }
}
