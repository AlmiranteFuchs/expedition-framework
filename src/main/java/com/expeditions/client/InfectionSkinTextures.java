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
    public record Layers(ResourceLocation bark, ResourceLocation scars, ResourceLocation embers) { }
    private static final Map<ResourceLocation, Optional<Layers>> CACHE = new HashMap<>();
    private InfectionSkinTextures() { }
    public static void clear() {
        var textures = Minecraft.getInstance().getTextureManager();
        CACHE.values().forEach(pair -> pair.ifPresent(value -> {
            textures.release(value.bark()); textures.release(value.scars()); textures.release(value.embers());
        }));
        CACHE.clear();
    }
    public static Optional<Layers> forSkin(ResourceLocation skin) {
        var known = CACHE.get(skin);
        if (known != null) return known;
        if (CACHE.size() >= 256) return Optional.empty();
        var mc = Minecraft.getInstance();
        Optional<Layers> result;
        try (var stream = mc.getResourceManager().open(skin); var host = NativeImage.read(stream)) {
            int id = CACHE.size();
            var scars = create(host, "infection_scars", "scars_" + id, false);
            var embers = create(host, "infection_embers", "embers_" + id, false);
            var bark = create(host, "infection_scars", "bark_" + id, true);
            result = Optional.of(new Layers(bark, scars, embers));
        } catch (java.io.IOException e) {
            // Downloaded/dynamic skins have no resource image. Preserve their
            // transparency rather than paint opaque pixels over unknown UVs.
            result = Optional.empty();
        }
        CACHE.put(skin, result);
        return result;
    }
    private static ResourceLocation create(NativeImage host, String asset, String name, boolean bark) throws java.io.IOException {
        var mc = Minecraft.getInstance();
        try (var stream = mc.getResourceManager().open(ResourceLocation.parse("expeditions:textures/entity/" + asset + ".png"));
                var stencil = NativeImage.read(stream)) {
            int w = Math.min(256, host.getWidth()), h = Math.min(256, host.getHeight());
            var image = new NativeImage(w,h,false);
            for (int y=0;y<h;y++) for (int x=0;x<w;x++) {
                int sx = x * stencil.getWidth() / w;
                int sy = y * stencil.getHeight() / h;
                int color = bark ? barkPixel(stencil, sx, sy) : stencil.getPixelRGBA(sx, sy);
                int alpha = (color>>>24)*(host.getPixelRGBA(x*host.getWidth()/w,y*host.getHeight()/h)>>>24)/255;
                image.setPixelRGBA(x,y,alpha<<24 | color&0xffffff);
            }
            var id = ResourceLocation.parse("expeditions:infection/" + name);
            mc.getTextureManager().register(id,new DynamicTexture(image));
            return id;
        }
    }

    /** Extend the lesion stencil into staggered, matte bark plates. Native alpha is applied afterward. */
    private static int barkPixel(NativeImage stencil, int x, int y) {
        int distance = 5;
        for (int dy = -4; dy <= 4; dy++) for (int dx = -3; dx <= 3; dx++) {
            int sx = x + dx, sy = y + dy;
            if (sx >= 0 && sy >= 0 && sx < stencil.getWidth() && sy < stencil.getHeight()
                    && stencil.getPixelRGBA(sx, sy) >>> 24 >= 180) {
                distance = Math.min(distance, Math.abs(dx) + (Math.abs(dy) + 1) / 2);
            }
        }
        int grain = Math.floorMod(x + y / 5, 5);
        if (distance >= 5 || distance == 4 && grain >= 2) return 0;
        // NativeImage stores ABGR. Warm off-white faces, umber joints and subdued edges.
        int rgb = grain == 0 ? 0x777F92 : grain == 1 ? 0xB3C1CB : 0x9DA9B7;
        int alpha = distance < 3 ? 220 : 146;
        return alpha << 24 | rgb;
    }
}
