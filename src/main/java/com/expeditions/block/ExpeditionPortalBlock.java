package com.expeditions.block;

import com.expeditions.server.ExpeditionManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Expedition portal. Spawned above the terminal when a run starts; players
 * who step into it are teleported to the run's remote landing spot and join
 * its party. It stays open only until the first checkpoint is activated,
 * then the server removes it — the party seals.
 *
 * <p>All behavior is delegated to the manager: the block itself is a
 * non-solid marker with no logic beyond forwarding entity collisions.
 */
public class ExpeditionPortalBlock extends Block {
    public static final net.minecraft.world.level.block.state.properties.EnumProperty<net.minecraft.core.Direction.Axis> AXIS =
            net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_AXIS;
    public static final net.minecraft.world.level.block.state.properties.BooleanProperty FRAMED =
            net.minecraft.world.level.block.state.properties.BooleanProperty.create("framed");
    private static final net.minecraft.world.phys.shapes.VoxelShape OUTLINE_X = apertureShape(false);
    private static final net.minecraft.world.phys.shapes.VoxelShape OUTLINE_Z = apertureShape(true);
    private static final VoxelShape FRAMED_X = net.minecraft.world.phys.shapes.Shapes.or(
            box(0, 0, 7.75, 4, 32, 8.25), box(12, 0, 7.75, 16, 32, 8.25),
            box(4, 28, 7.75, 12, 32, 8.25));
    private static final VoxelShape FRAMED_Z = net.minecraft.world.phys.shapes.Shapes.or(
            box(7.75, 0, 0, 8.25, 32, 4), box(7.75, 0, 12, 8.25, 32, 16),
            box(7.75, 28, 4, 8.25, 32, 12));

    private static net.minecraft.world.phys.shapes.VoxelShape apertureShape(boolean alongZ) {
        // One root frame, matching the single plane in the model.
        // Leave the aperture empty so a ray through it reaches the block behind.
        var shape = net.minecraft.world.phys.shapes.Shapes.empty();
        // Alpha mask of the connected root frame, top row first. Detached
        // ember motes do not intercept a click through the aperture.
        int[] rows = {0x7e0, 0xff0, 0x1ff8, 0x383c, 0x781e, 0x700e, 0x701e, 0x780e,
                0xf00f, 0xe007, 0xf00f, 0x781e, 0x3c3c, 0x3ffc, 0x1ff8, 0xff0};
        for (int row = 0; row < 16; row++) {
            int y = 15 - row;
            for (int x = 0; x < 16; x++) {
                if ((rows[row] & (1 << x)) != 0) {
                    shape = net.minecraft.world.phys.shapes.Shapes.or(shape, alongZ
                            ? box(7.75, y, x, 8.25, y + 1, x + 1)
                            : box(x, y, 7.75, x + 1, y + 1, 8.25));
                }
            }
        }
        return shape.optimize();
    }

    @Override
    protected net.minecraft.world.phys.shapes.VoxelShape getShape(BlockState state,
            net.minecraft.world.level.BlockGetter level, BlockPos pos,
            net.minecraft.world.phys.shapes.CollisionContext context) {
        if (state.getValue(FRAMED)) {
            return state.getValue(AXIS) == net.minecraft.core.Direction.Axis.X ? FRAMED_X : FRAMED_Z;
        }
        return state.getValue(AXIS) == net.minecraft.core.Direction.Axis.X ? OUTLINE_X : OUTLINE_Z;
    }

    public ExpeditionPortalBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(AXIS, net.minecraft.core.Direction.Axis.X).setValue(FRAMED, false));
    }

    @Override
    protected void createBlockStateDefinition(net.minecraft.world.level.block.state.StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS, FRAMED);
    }

    @Override
    protected BlockState rotate(BlockState state, net.minecraft.world.level.block.Rotation rotation) {
        return switch (rotation) {
            case CLOCKWISE_90, COUNTERCLOCKWISE_90 -> state.setValue(AXIS,
                    state.getValue(AXIS) == net.minecraft.core.Direction.Axis.X
                            ? net.minecraft.core.Direction.Axis.Z : net.minecraft.core.Direction.Axis.X);
            default -> state;
        };
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, net.minecraft.util.RandomSource random) {
        double angle = random.nextDouble() * Math.PI * 2;
        double x = pos.getX() + 0.5 + Math.cos(angle) * 0.35;
        double z = pos.getZ() + 0.5 + Math.sin(angle) * 0.35;
        double y = pos.getY() + 0.2 + random.nextDouble() * (state.getValue(FRAMED) ? 1.65 : 0.7);
        level.addParticle(net.minecraft.core.particles.ParticleTypes.ASH, x, y, z, 0, 0.012, 0);
        if (random.nextInt(4) == 0) {
            level.addParticle(net.minecraft.core.particles.ParticleTypes.SMALL_FLAME,
                    x, y, z, (pos.getX() + 0.5 - x) * 0.02, 0.005, (pos.getZ() + 0.5 - z) * 0.02);
        }
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moved) {
        super.onPlace(state, level, pos, oldState, moved);
        if (!level.isClientSide && state.getValue(FRAMED)) level.scheduleTick(pos, this, 20);
    }

    @Override
    protected void tick(BlockState state, net.minecraft.server.level.ServerLevel level, BlockPos pos,
            net.minecraft.util.RandomSource random) {
        if (state.getValue(FRAMED) && ExpeditionManager.get(level.getServer()).checkFramedPortal(level, pos)) {
            level.scheduleTick(pos, this, 20);
        }
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (!level.isClientSide && entity instanceof ServerPlayer player) {
            ExpeditionManager.get(level.getServer()).enterPortal(player, pos);
        }
    }
}
