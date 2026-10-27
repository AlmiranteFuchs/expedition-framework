package com.expeditions.client;

import com.expeditions.block.AssimilationLayerBlock;
import com.expeditions.block.entity.AssimilationLayerBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;

/**
 * Draws the assimilation skin: the covered face's own sprite, desaturated
 * at runtime into a standalone texture, emitted as a quad a hair above the
 * face (inside the layer's cell). The underlying block is untouched — its
 * state, model and any block entity render exactly as before; we simply
 * hide its color under its own grayscale copy.
 *
 * <p>Blocks whose face exposes no baked sprite (custom renderers, BER-only
 * blocks) get the generic membrane texture instead — the layer still
 * assimilates them mechanically, just without the faithful skin.
 */
public class AssimilationLayerRenderer implements BlockEntityRenderer<AssimilationLayerBlockEntity> {
    /** Bundled fallback for faces with no resolvable sprite. */
    private static final ResourceLocation MEMBRANE =
            ResourceLocation.fromNamespaceAndPath("expeditions", "textures/block/assimilation_layer.png");

    /** Offset of the skin into the layer cell, off the covered face. */
    private static final double EPS = 1.0 / 512.0;

    /** Directional face shading, lifted from vanilla's factors (down 0.5,
     * sides 0.6/0.8) to offset the translucent membrane plate darkening
     * what shows through it — tuned so each face reads like the block's
     * own, desaturated. */
    private static final Map<Direction, Float> SHADE = Map.of(
            Direction.DOWN, 0.75f, Direction.UP, 1.0f,
            Direction.NORTH, 0.8f, Direction.SOUTH, 0.8f,
            Direction.WEST, 0.6f, Direction.EAST, 0.6f);

    private static final RandomSource RANDOM = RandomSource.create(42L);

    public AssimilationLayerRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(AssimilationLayerBlockEntity entity, float partialTick,
            PoseStack poseStack, MultiBufferSource buffers, int packedLight,
            int packedOverlay) {
        Level level = entity.getLevel();
        if (level == null) {
            return;
        }
        BlockState state = entity.getBlockState();
        Direction facing = state.getValue(AssimilationLayerBlock.FACING);
        BlockPos pos = entity.getBlockPos();
        BlockState support = level.getBlockState(pos.relative(facing.getOpposite()));

        VertexConsumer consumer = buffers.getBuffer(
                RenderType.entityTranslucent(textureFor(level, pos, support, facing)));
        int light = LevelRenderer.getLightColor(level, pos);
        float shade = SHADE.get(facing);
        emitQuad(consumer, poseStack, facing, shade, light, packedOverlay);
    }

    /**
     * The covered face's sprite, desaturated. Preference: a quad baked for
     * that face, then an unculled quad pointing the same way, then the
     * particle texture, then the membrane fallback.
     */
    private static ResourceLocation textureFor(Level level, BlockPos pos,
            BlockState support, Direction facing) {
        BlockRenderDispatcher dispatcher = Minecraft.getInstance().getBlockRenderer();
        BakedModel model = dispatcher.getBlockModel(support);
        List<BakedQuad> quads = model.getQuads(support, facing, RANDOM,
                ModelData.EMPTY, null);
        if (quads.isEmpty()) {
            quads = model.getQuads(support, null, RANDOM, ModelData.EMPTY, null)
                    .stream().filter(q -> q.getDirection() == facing).toList();
        }
        TextureAtlasSprite sprite = !quads.isEmpty() ? quads.get(0).getSprite()
                : model.getParticleIcon(ModelData.EMPTY);
        return sprite == null ? MEMBRANE : DesaturationTextures.forSprite(sprite);
    }

    /**
     * Emits one quad on the inner face of the layer cell that touches the
     * support. Corners follow the vanilla canonical quad order for each
     * face and UVs the matching per-face mapping, so the skin lines up
     * exactly with the texture it is desaturating.
     */
    private static void emitQuad(VertexConsumer consumer, PoseStack poseStack,
            Direction facing, float shade, int light, int overlay) {
        PoseStack.Pose pose = poseStack.last();
        float e = (float) EPS;
        float[][] corners = switch (facing) {
            case UP -> new float[][] {
                    {0, e, 0}, {0, e, 1}, {1, e, 1}, {1, e, 0}};
            case DOWN -> new float[][] {
                    {0, 1 - e, 0}, {1, 1 - e, 0}, {1, 1 - e, 1}, {0, 1 - e, 1}};
            case NORTH -> new float[][] {
                    {1, 1, 1 - e}, {0, 1, 1 - e}, {0, 0, 1 - e}, {1, 0, 1 - e}};
            case SOUTH -> new float[][] {
                    {0, 1, e}, {1, 1, e}, {1, 0, e}, {0, 0, e}};
            case WEST -> new float[][] {
                    {1 - e, 1, 1}, {1 - e, 1, 0}, {1 - e, 0, 0}, {1 - e, 0, 1}};
            case EAST -> new float[][] {
                    {e, 1, 0}, {e, 1, 1}, {e, 0, 1}, {e, 0, 0}};
        };
        // Canonical quad UVs: corners above run u-major then v, like vanilla.
        float[][] uv = switch (facing) {
            case UP -> new float[][] {{0, 0}, {0, 1}, {1, 1}, {1, 0}};
            case DOWN, NORTH, SOUTH, WEST, EAST -> new float[][] {
                    {0, 0}, {1, 0}, {1, 1}, {0, 1}};
        };
        float nx = facing.getStepX();
        float ny = facing.getStepY();
        float nz = facing.getStepZ();
        for (int i = 0; i < 4; i++) {
            consumer.addVertex(pose, corners[i][0], corners[i][1], corners[i][2])
                    .setColor(shade, shade, shade, 1.0f)
                    .setUv(uv[i][0], uv[i][1])
                    .setOverlay(overlay)
                    .setLight(light)
                    .setNormal(pose, nx, ny, nz);
        }
    }
}
