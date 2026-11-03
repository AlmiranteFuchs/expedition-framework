package com.expeditions.client;

import com.expeditions.core.CompassBearing;
import com.expeditions.core.CompassElevation;
import com.expeditions.core.ExpeditionOptions;
import com.expeditions.item.ExpeditionCompassItem;
import com.expeditions.registry.ModDataComponents;
import com.mojang.math.Axis;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;

/** The practice compass projects a small amber bearing while it is held. */
final class GuidedBearing {
    private GuidedBearing() { }

    static void render(GuiGraphics gui, DeltaTracker tick) {
        var mc = Minecraft.getInstance();
        var player = mc.player;
        if (player == null || mc.options.hideGui || mc.screen != null || player.isSpectator()) return;
        for (var hand : InteractionHand.values()) {
            var stack = player.getItemInHand(hand);
            var step = stack.get(ModDataComponents.GUIDED_STEP.get());
            if (step == null || step < 1 || step > ExpeditionOptions.GUIDED_CHECKPOINTS + 1) continue;
            var target = ExpeditionCompassItem.localTarget(stack, player.level());
            if (target == null) continue;

            double east = target.pos().getX() + 0.5 - player.getX();
            double south = target.pos().getZ() + 0.5 - player.getZ();
            double height = target.pos().getY() - player.getY();
            boolean close = Math.hypot(east, south) < 3 && CompassElevation.direction(height) == 0;
            int center = gui.guiWidth() / 2;
            int y = Math.max(25, gui.guiHeight() / 7);
            var pose = gui.pose();
            pose.pushPose();
            pose.translate(center, y, 0);
            if (!close) {
                pose.mulPose(Axis.ZP.rotationDegrees((float) CompassBearing.turn(east, south,
                        player.getViewYRot(tick.getGameTimeDeltaPartialTick(false)))));
                // A stepped pale thorn with a warm resin seam and dark rim.
                gui.fill(-2, -9, 2, 7, 0xd52a2419);
                gui.fill(-6, -3, 6, 2, 0xd52a2419);
                gui.fill(-1, -8, 1, 6, 0xffe8d8aa);
                gui.fill(-3, -5, 3, 0, 0xffe8d8aa);
                gui.fill(-5, -2, 5, 0, 0xffe8d8aa);
                gui.fill(-1, -3, 1, 3, 0xffde952d);
            } else {
                gui.fill(-4, -4, 4, 4, 0xd52a2419);
                gui.fill(-2, -2, 2, 2, 0xffffc16b);
            }
            pose.popPose();
            var label = step > ExpeditionOptions.GUIDED_CHECKPOINTS
                    ? Component.translatable("gui.expeditions.guide_exit")
                    : Component.translatable("gui.expeditions.guide_mark", step, ExpeditionOptions.GUIDED_CHECKPOINTS);
            gui.drawCenteredString(mc.font, label, center, y + 11, 0xffead9ae);
            int vertical = CompassElevation.direction(height);
            String hint = close ? (step > ExpeditionOptions.GUIDED_CHECKPOINTS ? "cross" : "use")
                    : vertical > 0 ? "above" : vertical < 0 ? "below" : "follow";
            gui.drawCenteredString(mc.font, Component.translatable("gui.expeditions.guide_" + hint),
                    center, y + 22, 0xffe5ad65);
            return;
        }
    }
}
