package com.expeditions.block;

import com.expeditions.block.entity.AssimilationLayerBlockEntity;
import com.expeditions.registry.ModItems;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * The Big Bad's assimilation layer: a thin membrane that attaches to one
 * face of a solid supporting block and renders that face grayscale. The
 * layer never replaces, mutates or stores the support's state — it only
 * lives in the cell adjacent to the covered face and dies with it.
 *
 * <p>{@link #FACING} is the outward normal of the covered face: the support
 * sits at {@code pos.relative(FACING.getOpposite())} and the membrane hugs
 * the opposite inner face of its own cell. A sturdy full face is required —
 * air, fluids, and non-solid or partial faces refuse placement, and losing
 * support removes the layer with no drops (the block is only harvested for
 * essence by direct breaking).
 */
public class AssimilationLayerBlock extends Block implements EntityBlock {
    /** Which side of the support the layer covers (the covered face's normal). */
    public static final DirectionProperty FACING = BlockStateProperties.FACING;

    /** Outline is 2/16 of a block so the membrane is easy to aim at. */
    private static final double OUTLINE_THICK = 2.0 / 16.0;

    private static final Map<Direction, VoxelShape> OUTLINE_SHAPES = shapes(OUTLINE_THICK);

    private static Map<Direction, VoxelShape> shapes(double thick) {
        Map<Direction, VoxelShape> map = new EnumMap<>(Direction.class);
        map.put(Direction.UP, Block.box(0, 0, 0, 16, thick * 16, 16));
        map.put(Direction.DOWN, Block.box(0, 16 - thick * 16, 0, 16, 16, 16));
        map.put(Direction.NORTH, Block.box(0, 0, 16 - thick * 16, 16, 16, 16));
        map.put(Direction.SOUTH, Block.box(0, 0, 0, 16, 16, thick * 16));
        map.put(Direction.WEST, Block.box(16 - thick * 16, 0, 0, 16, 16, 16));
        map.put(Direction.EAST, Block.box(0, 0, 0, thick * 16, 16, 16));
        return map;
    }

    public AssimilationLayerBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.UP));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    /** Whether {@code face} of the block at {@code supportPos} can hold a layer. */
    public static boolean canAttachTo(BlockGetter level, BlockPos supportPos, Direction face) {
        return level.getBlockState(supportPos).isFaceSturdy(level, supportPos, face);
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // getClickedPos is the placement cell; the support sits one back.
        Direction face = context.getClickedFace();
        BlockPos supportPos = context.getClickedPos().relative(face.getOpposite());
        return canAttachTo(context.getLevel(), supportPos, face)
                ? defaultBlockState().setValue(FACING, face)
                : null;
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction facing = state.getValue(FACING);
        return canAttachTo(level, pos.relative(facing.getOpposite()), facing);
    }

    /**
     * The canonical survival check: when the supporting block changes, the
     * layer evaluates itself and returns air. This path goes through
     * {@code updateOrDestroy} which rolls drops, so the block must have no
     * loot table — essence is granted by {@link #playerDestroy} instead,
     * which only a real survival harvest reaches. Support loss, piston
     * crush and world restoration all drop nothing.
     */
    @Override
    protected BlockState updateShape(BlockState state, Direction direction,
            BlockState neighborState, LevelAccessor level, BlockPos pos,
            BlockPos neighborPos) {
        return direction == state.getValue(FACING).getOpposite()
                        && !state.canSurvive(level, pos)
                ? Blocks.AIR.defaultBlockState()
                : state;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
            CollisionContext context) {
        return OUTLINE_SHAPES.get(state.getValue(FACING));
    }

    /**
     * A membrane is a film, not a shelf: entities pass through it like sculk
     * veins and lichen — they collide with the support, never the 1/16
     * skin. Without this, wall membranes snag anything hugging the wall.
     */
    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level,
            BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    /**
     * Exactly one essence per deliberate harvest. {@code playerDestroy} is
     * only invoked by the game mode after a successful survival break —
     * never in creative, never on support loss or replacement — so the
     * empty loot table cannot leak a second drop and cleanup stays silent.
     */
    @Override
    public void playerDestroy(Level level, Player player, BlockPos pos,
            BlockState state, @Nullable BlockEntity blockEntity, ItemStack tool) {
        super.playerDestroy(level, player, pos, state, blockEntity, tool);
        if (!level.isClientSide) {
            popResource(level, pos,
                    new ItemStack(ModItems.ASSIMILATION_ESSENCE.get()));
        }
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AssimilationLayerBlockEntity(pos, state);
    }
}
