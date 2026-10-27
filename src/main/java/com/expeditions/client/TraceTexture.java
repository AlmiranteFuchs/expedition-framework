package com.expeditions.client;

import com.expeditions.ExpeditionsMod;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * The assimilation's living signature, generated once per session: a
 * diamond-lattice of amber traces over the covered face. Alpha is baked
 * rim-weighted — traces are boldest at the membrane's edge and ghost
 * toward the center, so the assimilation's frontier reads as the glowing
 * part — while the renderer pulses the whole overlay on a slow sine so
 * it breathes instead of sitting static.
 */
public final class TraceTexture {
    private static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath(ExpeditionsMod.MOD_ID, "trace/amber");
    private static final int SIZE = 16;
    /** Honey amber, packed ABGR little-endian like NativeImage uses. */
    private static final int AMBER = 36 << 16 | 178 << 8 | 255;

    private static volatile boolean created;

    private TraceTexture() {
    }

    public static ResourceLocation get() {
        if (!created) {
            created = true;
            create();
        }
        return ID;
    }

    private static void create() {
        NativeImage img = new NativeImage(SIZE, SIZE, false);
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                boolean trace = (x + y) % 8 == 0 || (x - y + SIZE) % 8 == 0;
                if (!trace) {
                    continue; // fully transparent off the lattice
                }
                int dist = Math.min(Math.min(x, SIZE - 1 - x),
                        Math.min(y, SIZE - 1 - y));
                double edge = 1.0 - dist / 7.0;
                boolean node = (x + y) % 8 == 0 && (x - y + SIZE) % 8 == 0;
                int alpha = (int) (255 * Math.min(1.0,
                        0.15 + 0.85 * edge * edge + (node ? 0.25 : 0)));
                img.setPixelRGBA(x, y, alpha << 24 | AMBER);
            }
        }
        Minecraft.getInstance().getTextureManager().register(ID, new DynamicTexture(img));
    }
}
