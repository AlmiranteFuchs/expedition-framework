package com.expeditions.client;

import com.expeditions.ExpeditionsMod;
import com.mojang.blaze3d.platform.NativeImage;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * The assimilation's living signature, generated once per session: a
 * capillary network of amber veins over the covered face. Strands enter
 * at the membrane's rim and meander toward interior sinks with random
 * walk, thinning as they go — the frontier reads as the glowing part.
 * Alpha is additionally rim-weighted so the veins are boldest at the
 * edge and ghost toward the center. The renderer pulses the overlay on
 * the colony's heartbeat wave and rotates it per cell, so neighboring
 * membranes never tile identically.
 */
public final class TraceTexture {
    private static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath(ExpeditionsMod.MOD_ID, "trace/amber");
    private static final int SIZE = 32;
    private static final int STRANDS = 7;
    private static final long SEED = 0xA551CA71L;
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
        float[][] alpha = new float[SIZE][SIZE];
        Random rng = new Random(SEED);
        for (int s = 0; s < STRANDS; s++) {
            growStrand(alpha, rng);
        }
        NativeImage img = new NativeImage(SIZE, SIZE, false);
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                if (alpha[y][x] <= 0) {
                    continue; // fully transparent off the veins
                }
                int dist = Math.min(Math.min(x, SIZE - 1 - x),
                        Math.min(y, SIZE - 1 - y));
                double edge = 1.0 - dist / (SIZE / 2.0 - 1);
                int a = (int) (255 * Math.min(1.0,
                        alpha[y][x] * (0.30 + 0.70 * edge * edge)));
                img.setPixelRGBA(x, y, a << 24 | AMBER);
            }
        }
        Minecraft.getInstance().getTextureManager().register(ID, new DynamicTexture(img));
    }

    /**
     * One capillary: enters at a random rim point, drifts toward a random
     * interior sink with jitter, stamping a fading trail. The sink hops
     * occasionally so strands wander instead of beelining.
     */
    private static void growStrand(float[][] alpha, Random rng) {
        int[] start = rim(rng);
        double x = start[0], y = start[1];
        double tx = 4 + rng.nextInt(SIZE - 8);
        double ty = 4 + rng.nextInt(SIZE - 8);
        double strength = 0.55 + rng.nextDouble() * 0.45;
        int steps = 22 + rng.nextInt(26);
        for (int i = 0; i < steps; i++) {
            stamp(alpha, (int) Math.round(x), (int) Math.round(y),
                    strength * (1.0 - (double) i / steps * 0.8), rng);
            double dx = tx - x, dy = ty - y;
            double len = Math.max(1e-3, Math.hypot(dx, dy));
            x += dx / len * 0.9 + (rng.nextDouble() - 0.5) * 0.9;
            y += dy / len * 0.9 + (rng.nextDouble() - 0.5) * 0.9;
            if (rng.nextDouble() < 0.06) {
                tx = rng.nextInt(SIZE);
                ty = rng.nextInt(SIZE);
            }
        }
    }

    private static int[] rim(Random rng) {
        return switch (rng.nextInt(4)) {
            case 0 -> new int[] {0, rng.nextInt(SIZE)};
            case 1 -> new int[] {SIZE - 1, rng.nextInt(SIZE)};
            case 2 -> new int[] {rng.nextInt(SIZE), 0};
            default -> new int[] {rng.nextInt(SIZE), SIZE - 1};
        };
    }

    /** Veins are one pixel wide, thickening sporadically into knots. */
    private static void stamp(float[][] alpha, int x, int y, double v,
            Random rng) {
        if (x >= 0 && x < SIZE && y >= 0 && y < SIZE) {
            alpha[y][x] = (float) Math.max(alpha[y][x], v);
        }
        if (rng.nextDouble() < 0.18) {
            int nx = x + rng.nextInt(3) - 1;
            int ny = y + rng.nextInt(3) - 1;
            if (nx >= 0 && nx < SIZE && ny >= 0 && ny < SIZE) {
                alpha[ny][nx] = (float) Math.max(alpha[ny][nx], v * 0.7);
            }
        }
    }
}
