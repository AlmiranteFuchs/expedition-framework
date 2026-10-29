package com.expeditions.client;

import com.expeditions.ExpeditionsMod;
import com.mojang.blaze3d.platform.NativeImage;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;

/**
 * Runtime bleached copies of atlas sprites: the assimilation layer draws
 * the covered face's own texture paled toward bone — desaturated, lifted
 * toward near-white and tinted a hair cream — preserving its detail and
 * shading instead of faking it with a tint. Coverage reads "Pale Garden
 * bleached", not merely grayed. Textures are generated once per sprite,
 * registered with the texture manager, and reused for the session — no
 * persistent data, no atlas mutation.
 */
public final class DesaturationTextures {
    private static final Map<String, ResourceLocation> CACHE = new ConcurrentHashMap<>();
    private static int nextId;

    private DesaturationTextures() {
    }

    /** A desaturated copy of {@code sprite} as a standalone texture. */
    public static ResourceLocation forSprite(TextureAtlasSprite sprite) {
        String key = sprite.contents().name().toString();
        return CACHE.computeIfAbsent(key, k -> create(sprite));
    }

    private static ResourceLocation create(TextureAtlasSprite sprite) {
        NativeImage source = sprite.contents().getOriginalImage();
        NativeImage gray = new NativeImage(source.getWidth(), source.getHeight(), false);
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                // NativeImage packs ABGR little-endian.
                int c = source.getPixelRGBA(x, y);
                int a = c >>> 24;
                int r = c & 0xFF;
                int g = c >>> 8 & 0xFF;
                int b = c >>> 16 & 0xFF;
                int luma = (r * 299 + g * 587 + b * 114) / 1000;
                // Bleach toward bone: pull luma ~22% toward near-white,
                // then a faint cream bias so coverage reads paled.
                int lift = luma + (240 - luma) * 22 / 100;
                gray.setPixelRGBA(x, y, a << 24
                        | lift * 90 / 100 << 16 | lift * 97 / 100 << 8 | lift);
            }
        }
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(
                ExpeditionsMod.MOD_ID, "desat/" + nextId++);
        Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(gray));
        return id;
    }
}
