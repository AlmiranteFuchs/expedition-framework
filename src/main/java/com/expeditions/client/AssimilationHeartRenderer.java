package com.expeditions.client;

import com.expeditions.block.entity.AssimilationHeartBlockEntity;
import com.expeditions.core.PulseWave;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

/**
 * The heart's aura: a shell of bone-pale veins hovering a hair off each
 * face, throbbing on the colony's heartbeat — the wave's source, so it
 * beats first and brightest, its halo burning a deeper ember than the
 * membrane's. A membrane growing over the heart sits outside this shell
 * (the aura lives inside the covering cell's space), so the nucleus
 * glows up through its own assimilation.
 */
public class AssimilationHeartRenderer implements BlockEntityRenderer<AssimilationHeartBlockEntity> {
    /** Vein shell sits this far outside the block, the halo a hair further. */
    private static final float AURA = 0.05f;

    public AssimilationHeartRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(AssimilationHeartBlockEntity entity, float partialTick,
            PoseStack poseStack, MultiBufferSource buffers, int packedLight,
            int packedOverlay) {
        Level level = entity.getLevel();
        if (level == null) {
            return;
        }
        BlockPos pos = entity.getBlockPos();
        double time = level.getGameTime() + partialTick;
        // Distance zero — the heart is the wave's source.
        float amp = (float) PulseWave.amplitudeAt(time, 0, pos.asLong());
        float veins = 0.20f + 0.65f * amp;
        float halo = 0.55f * amp;
        int light = LevelRenderer.getLightColor(level, pos);
        int rot = (int) (pos.asLong() & 7);

        for (Direction d : Direction.values()) {
            float shade = AssimilationLayerRenderer.SHADE.get(d);
            VertexConsumer vein = buffers.getBuffer(
                    RenderType.entityTranslucent(TraceTexture.get()));
            AssimilationLayerRenderer.emitQuad(vein, poseStack, d, 1 + AURA,
                    shade, shade, shade, veins, light,
                    packedOverlay, rot, 1.0f);
            VertexConsumer glow = buffers.getBuffer(
                    RenderType.eyes(TraceTexture.get()));
            AssimilationLayerRenderer.emitQuad(glow, poseStack, d,
                    1 + AURA * 2,
                    AssimilationLayerRenderer.EMBER[0],
                    AssimilationLayerRenderer.EMBER[1] * 0.8f,
                    AssimilationLayerRenderer.EMBER[2] * 0.5f, halo,
                    AssimilationLayerRenderer.FULLBRIGHT, packedOverlay,
                    rot, 1.0f);
        }
    }
}
