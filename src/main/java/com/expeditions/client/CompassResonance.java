package com.expeditions.client;

import com.expeditions.core.CompassElevation;
import com.expeditions.core.PulseWave;
import com.expeditions.item.ExpeditionCompassItem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;

/** A held compass's ember: item-space rendering, never a HUD arrow or world marker. */
public final class CompassResonance {
    private static final ResourceLocation EMBER = ResourceLocation.fromNamespaceAndPath(
            "expeditions", "textures/item/compass_ember.png");
    private static ClientLevel lastLevel;
    private static boolean wasUsing;
    private static long attunedAt = Long.MIN_VALUE;
    private static GlobalPos attunedTarget;
    private static InteractionHand attunedHand;

    private CompassResonance() { }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            wasUsing = false;
            attunedAt = Long.MIN_VALUE;
            attunedTarget = null;
        }
        if (mc.player == null || mc.level == null || mc.isPaused()) {
            return;
        }
        GlobalPos target = mc.player.isUsingItem()
                ? ExpeditionCompassItem.localTarget(mc.player.getUseItem(), mc.level) : null;
        boolean using = target != null;
        if (using && !wasUsing) {
            attunedAt = mc.level.getGameTime();
            attunedTarget = target;
            attunedHand = mc.player.getUsedItemHand();
        }
        wasUsing = using;
        if (attunedTarget == null) {
            return;
        }
        if (!attunedTarget.equals(ExpeditionCompassItem.localTarget(
                mc.player.getItemInHand(attunedHand), mc.level))) {
            attunedTarget = null;
            return;
        }
        long age = mc.level.getGameTime() - attunedAt;
        if (age == 0 || age == 6 || age == 12) {
            int direction = CompassElevation.direction(attunedTarget.pos().getY() - mc.player.getY());
            float note = age / 6.0f;
            float pitch = direction > 0 ? 0.8f + note * 0.16f
                    : direction < 0 ? 1.12f - note * 0.16f : 0.96f;
            mc.player.playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 0.35f, pitch);
        }
    }

    public static void onRenderHand(RenderHandEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui
                || mc.player.isSpectator() || mc.player.isInvisible()) {
            return;
        }
        GlobalPos target = ExpeditionCompassItem.localTarget(event.getItemStack(), mc.level);
        if (target == null) {
            return;
        }
        double time = mc.level.getGameTime() + event.getPartialTick();
        double gap = target.pos().getY() - mc.player.getY();
        double pull = CompassElevation.pull(gap);
        int direction = CompassElevation.direction(gap);
        boolean right = (event.getHand() == InteractionHand.MAIN_HAND
                ? mc.player.getMainArm() : mc.player.getMainArm().getOpposite()) == HumanoidArm.RIGHT;
        float side = right ? 1 : -1;
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        // Follow the ordinary held-item equip and swing translations; the
        // ember lives above the compass housing and mirrors with the hand.
        float swingRoot = (float) Math.sqrt(event.getSwingProgress());
        pose.translate(side * (-0.4f * Math.sin(swingRoot * Math.PI)),
                0.2f * Math.sin(swingRoot * Math.PI * 2),
                -0.2f * Math.sin(event.getSwingProgress() * Math.PI));
        pose.translate(side * 0.56, -0.22 - event.getEquipProgress() * 0.6, -0.72);
        float beat = (float) PulseWave.amplitudeAt(time, 0, target.pos().asLong());
        float y = (float) (pull * 0.12 + Math.sin(time * 0.16) * 0.006);
        spark(event, pose, 0, y, 0.019f + beat * 0.005f, 0.8f);

        double age = target.equals(attunedTarget) && event.getHand() == attunedHand
                ? time - attunedAt : -1;
        if (age >= 0 && age < ExpeditionCompassItem.ATTUNEMENT_TICKS) {
            // Each spark travels along the vertical pull. At the same height
            // they settle into a small level ring instead of implying up/down.
            for (int i = 0; i < 7; i++) {
                double progress = (age - i * 1.6) / 13;
                if (progress < 0 || progress > 1) continue;
                float x = (float) (Math.sin(i * 2.4 + progress * 2) * 0.018);
                float trailY = direction == 0
                        ? (float) (Math.cos(i * 2.4) * 0.025)
                        : (float) (direction * progress * (0.17 + Math.abs(pull) * 0.12));
                spark(event, pose, x, trailY, 0.013f, (float) (1 - progress));
            }
        }
        pose.popPose();
    }

    private static void spark(RenderHandEvent event, PoseStack stack,
            float x, float y, float radius, float alpha) {
        var buffer = event.getMultiBufferSource().getBuffer(RenderType.eyes(EMBER));
        var pose = stack.last();
        float[][] corners = {{-1, -1, 0, 1}, {1, -1, 1, 1}, {1, 1, 1, 0}, {-1, 1, 0, 0}};
        for (float[] corner : corners) {
            buffer.addVertex(pose, x + corner[0] * radius, y + corner[1] * radius, 0)
                    .setColor(1.0f, 1.0f, 1.0f, alpha)
                    .setUv(corner[2], corner[3])
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(LightTexture.FULL_BRIGHT)
                    .setNormal(pose, 0, 0, 1);
        }
    }
}
