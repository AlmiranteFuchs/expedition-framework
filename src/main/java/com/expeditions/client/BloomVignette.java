package com.expeditions.client;

import com.expeditions.core.PulseWave;
import com.expeditions.registry.ModAttachments;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/** A restrained edge pulse. Infection never changes fog distance or camera projection. */
public final class BloomVignette {
    private BloomVignette() { }
    public static void render(GuiGraphics gui, net.minecraft.client.DeltaTracker delta) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui || mc.player.isSpectator()) return;
        int stage = mc.player.getData(ModAttachments.ASSIMILATED);
        if (stage <= 0) return;
        double beat = PulseWave.amplitudeAt(mc.level.getGameTime() + delta.getGameTimeDeltaPartialTick(false),
                0, mc.player.getUUID().getMostSignificantBits());
        int width = gui.guiWidth(), height = gui.guiHeight(), edge = Math.min(26, Math.min(width, height) / 8);
        for (int i = 0; i < edge; i++) {
            int alpha = (int)(255 * Math.min(.10, .022 * stage + .025 * beat) * (1.0 - (double)i / edge));
            int color = alpha << 24 | 0x172019;
            gui.fill(i, i, width - i, i + 1, color);
            gui.fill(i, height - i - 1, width - i, height - i, color);
            gui.fill(i, i + 1, i + 1, height - i - 1, color);
            gui.fill(width - i - 1, i + 1, width - i, height - i - 1, color);
        }
    }
}
