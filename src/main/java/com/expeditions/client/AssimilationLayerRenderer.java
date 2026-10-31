package com.expeditions.client;

import com.expeditions.block.entity.AssimilationLayerBlockEntity;
import com.expeditions.core.PulseWave;
import com.expeditions.registry.ModBlocks;
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
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;

/**
 * Draws the assimilation skin: each covered face's own sprite, desaturated
 * at runtime into a standalone texture, emitted as a quad a hair above the
 * face (inside the layer's cell). The underlying block is untouched — its
 * state, model and any block entity render exactly as before; we simply
 * hide its color under its own grayscale copy.
 *
 * <p>Over the skin, sparse pale rootlets preserve the host's material identity.
 * Only the small ember knots pulse; the roots take ordinary world lighting.
 * The wave originates at the cell's
 * recorded heart and arrives
 * later the farther the cell sits from it — the colony visibly beats
 * outward from its nucleus. A cell whose heart died retains an ashen scar
 * without a pulse; hand-placed growth beats a faint solo rhythm.
 *
 * <p>Blocks whose face exposes no baked sprite (custom renderers, BER-only
 * blocks) get the generic membrane texture instead — the layer still
 * assimilates them mechanically, just without the faithful skin.
 */
public class AssimilationLayerRenderer implements BlockEntityRenderer<AssimilationLayerBlockEntity> {
    /** Bundled fallback for faces with no resolvable sprite. */
    static final ResourceLocation MEMBRANE =
            ResourceLocation.fromNamespaceAndPath("expeditions", "textures/block/assimilation_layer.png");
    private static final ResourceLocation ROOT_EMBERS =
            ResourceLocation.fromNamespaceAndPath("expeditions", "textures/block/assimilation_connections_embers.png");
    private static final ResourceLocation CONNECTED_ROOTS =
            ResourceLocation.fromNamespaceAndPath("expeditions", "textures/block/assimilation_connections.png");

    /** Offset of the skin into the layer cell, off the covered face. */
    static final double EPS = 1.0 / 512.0;

    /** Overlay light: always bright, regardless of the cell's lighting. */
    static final int FULLBRIGHT = 0xF000F0;

    /** Shared ignition tint used by the heart's aura. */
    static final float[] EMBER = {1.0f, 0.55f, 0.15f};

    /** Directional face shading, lifted from vanilla's factors (down 0.5,
     * sides 0.6/0.8) to offset the translucent membrane plate darkening
     * what shows through it — tuned so each face reads like the block's
     * own, desaturated. */
    static final Map<Direction, Float> SHADE = Map.of(
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
        BlockPos pos = entity.getBlockPos();
        int light = LevelRenderer.getLightColor(level, pos);
        double time = level.getGameTime() + partialTick;
        var camera=Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        var player=Minecraft.getInstance().player;
        double viewerX=(player==null?camera.x:player.getX())-pos.getX()-.5;
        double viewerY=(player==null?camera.y:player.getY()+player.getBbHeight()*.5)-pos.getY()-.5;
        double viewerZ=(player==null?camera.z:player.getZ())-pos.getZ()-.5;
        float distance=(float)Math.sqrt(camera.distanceToSqr(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5));

        // The wave radiates from the colony's heart: same lub-dub
        // everywhere, delayed per block of distance. A recorded-but-dead
        // heart leaves cold roots — the scar stays, the life is gone;
        // hand-placed growth beats a faint solo rhythm.
        BlockPos heart = entity.heart();
        float amp;
        boolean living = heart == null || level.getBlockState(heart).is(ModBlocks.ASSIMILATION_HEART.get());
        if (heart != null && level.getBlockState(heart)
                .is(ModBlocks.ASSIMILATION_HEART.get())) {
            amp = (float) PulseWave.amplitudeAt(
                    time, pos.distManhattan(heart), heart.asLong());
        } else if (heart == null) {
            amp = (float) PulseWave.amplitudeAt(time, 0, pos.asLong()) * 0.5f;
        } else {
            amp = 0;
        }
        float halo = living ? 0.03f + 0.72f * amp : 0;

        // Fresh tissue unfurls: a just-grown face scales in over ~10
        // ticks (ease-out) instead of popping — the colony visibly crawls.
        long born = entity.born();
        // Infection drains the host over a minute. Fresh growth still shows
        // the original material; the older colony becomes an ashen scar.
        float maturity = born == AssimilationLayerBlockEntity.BORN_UNKNOWN ? 1.0f
                : (float) Math.clamp((time - born) / 1200.0, 0, 1);
        float bleaching = 0.08f + 0.86f * maturity;
        float grow = 1.0f;
        if (born != AssimilationLayerBlockEntity.BORN_UNKNOWN) {
            float t = (float) Math.clamp((time - born) / 10.0, 0, 1);
            grow = t * (2.0f - t);
        }

        // One skin + trace + halo per covered face — the multiface block
        // can coat several faces of its cell at once. A face property d
        // means the support sits at pos+d; the covered face's normal is -d.
        for (Direction d : MultifaceBlock.availableFaces(state)) {
            Direction facing = d.getOpposite();
            BlockState support = level.getBlockState(pos.relative(d));
            // Variant interiors share fixed ports; reciprocal edge choices
            // preserve seams without forcing every face into a square lattice.
            int mask = com.expeditions.block.AssimilationConnections.veinMask(level, pos, facing);
            int tile = mask * 4 + (int)(net.minecraft.util.Mth.getSeed(pos) & 3);
            float u = (tile % 8 * 16 + .01f) / 128, v = (tile / 8 * 16 + .01f) / 128;
            float span = 15.98f / 128;
            VertexConsumer skin = buffers.getBuffer(
                    RenderType.entityTranslucent(
                            textureFor(level, pos, support, facing)));
            float shade = SHADE.get(facing);
            emitQuad(skin, poseStack, facing, (float) EPS,
                    shade, shade, shade, bleaching, light, packedOverlay, 0, 1);
            VertexConsumer vein = buffers.getBuffer(
                    RenderType.entityTranslucent(CONNECTED_ROOTS));
            emitQuad(vein, poseStack, facing, (float) EPS * 2, shade, shade,
                    shade, 1.0f, light, packedOverlay, 0, grow, u, v, span);
            VertexConsumer glow = buffers.getBuffer(
                    RenderType.eyes(ROOT_EMBERS));
            emitQuad(glow, poseStack, facing, (float) EPS * 3,
                    halo, halo, halo,
                    halo, FULLBRIGHT, packedOverlay, 0, grow, u, v, span);
            BloomRelief.render(poseStack,buffers,facing,mask,tile%4,light,packedOverlay,grow,shade,
                    living ? amp : 0,distance,living,viewerX,viewerY,viewerZ);
        }
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
     * Emits one quad on the plane facing the given direction at offset
     * {@code plane} along the face axis (0..1 inside the cell, &gt;1 sits
     * outside it — used by the heart's aura). {@code r},{@code g},{@code b}
     * tint the quad — grayscale for the shaded skin, white for the pale
     * veins, ember for the halo. {@code rot} spins the UVs —
     * bits 0-1 are quarter turns, bit 2 a horizontal mirror — so identical
     * textures never read as one continuous lattice across cells.
     */
    static void emitQuad(VertexConsumer consumer, PoseStack poseStack,
            Direction facing, float plane, float r, float g, float b,
            float alpha, int light, int overlay, int rot, float grow) {
        emitQuad(consumer, poseStack, facing, plane, r, g, b, alpha, light, overlay, rot, grow, 0, 0, 1);
    }

    private static void emitQuad(VertexConsumer consumer, PoseStack poseStack,
            Direction facing, float plane, float r, float g, float b,
            float alpha, int light, int overlay, int rot, float grow, float u, float v, float span) {
        PoseStack.Pose pose = poseStack.last();
        float[][] corners = switch (facing) {
            case UP -> new float[][] {
                    {0, plane, 0}, {0, plane, 1}, {1, plane, 1}, {1, plane, 0}};
            case DOWN -> new float[][] {
                    {0, 1 - plane, 0}, {1, 1 - plane, 0},
                    {1, 1 - plane, 1}, {0, 1 - plane, 1}};
            case NORTH -> new float[][] {
                    {1, 1, 1 - plane}, {0, 1, 1 - plane},
                    {0, 0, 1 - plane}, {1, 0, 1 - plane}};
            case SOUTH -> new float[][] {
                    {0, 1, plane}, {1, 1, plane}, {1, 0, plane}, {0, 0, plane}};
            case WEST -> new float[][] {
                    {1 - plane, 1, 1}, {1 - plane, 1, 0},
                    {1 - plane, 0, 0}, {1 - plane, 0, 1}};
            case EAST -> new float[][] {
                    {plane, 1, 0}, {plane, 1, 1}, {plane, 0, 1}, {plane, 0, 0}};
        };
        float[][] uv = rotatedUvs(facing, rot);
        float nx = facing.getStepX();
        float ny = facing.getStepY();
        float nz = facing.getStepZ();
        int normalAxis = facing.getAxis().ordinal();
        alpha *= grow;
        for (int i = 0; i < 4; i++) {
            float[] c = corners[i];
            // Grow-in pulls the tangent axes toward the quad's center —
            // the normal axis keeps its plane offset off the face.
            float x = normalAxis == 0 ? c[0] : 0.5f + (c[0] - 0.5f) * grow;
            float y = normalAxis == 1 ? c[1] : 0.5f + (c[1] - 0.5f) * grow;
            float z = normalAxis == 2 ? c[2] : 0.5f + (c[2] - 0.5f) * grow;
            consumer.addVertex(pose, x, y, z)
                    .setColor(r, g, b, alpha)
                    .setUv(u + uv[i][0] * span, v + uv[i][1] * span)
                    .setOverlay(overlay)
                    .setLight(light)
                    .setNormal(pose, nx, ny, nz);
        }
    }

    /** Canonical quad UVs for the face, rotated/mirrored by {@code rot}. */
    private static float[][] rotatedUvs(Direction facing, int rot) {
        float[][] uv = switch (facing) {
            case UP -> new float[][] {{0, 0}, {0, 1}, {1, 1}, {1, 0}};
            default -> new float[][] {{0, 0}, {1, 0}, {1, 1}, {0, 1}};
        };
        if ((rot & 4) != 0) {
            for (float[] p : uv) {
                p[0] = 1 - p[0];
            }
        }
        for (int i = 0; i < (rot & 3); i++) {
            for (float[] p : uv) {
                float u = p[0];
                p[0] = 1 - p[1];
                p[1] = u;
            }
        }
        return uv;
    }
}
