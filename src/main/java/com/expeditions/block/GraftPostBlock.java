package com.expeditions.block;

import com.expeditions.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;

/** The top of a graft post curls toward a neighboring collar. */
public final class GraftPostBlock extends RotatedPillarBlock {
    public static final DirectionProperty CROWN = DirectionProperty.create("crown",
            direction -> direction != Direction.DOWN);

    public GraftPostBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(CROWN, Direction.UP));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(CROWN);
    }

    private BlockState crown(BlockState state, LevelAccessor level, BlockPos pos) {
        if (state.getValue(AXIS) == Direction.Axis.Y) {
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                if (level.getBlockState(pos.relative(direction)).is(ModBlocks.GRAFT_COLLAR.get())) {
                    return state.setValue(CROWN, direction);
                }
            }
        }
        return state.setValue(CROWN, Direction.UP);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState previous, boolean moving) {
        super.onPlace(state, level, pos, previous, moving);
        if (!level.isClientSide && !previous.is(this)) {
            var updated = crown(state, level, pos);
            if (updated != state) level.setBlock(pos, updated, 2);
        }
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return crown(super.getStateForPlacement(context), context.getLevel(), context.getClickedPos());
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbor,
            LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        return crown(super.updateShape(state, direction, neighbor, level, pos, neighborPos), level, pos);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return super.rotate(state, rotation).setValue(CROWN, rotation.rotate(state.getValue(CROWN)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return super.mirror(state, mirror).setValue(CROWN, mirror.mirror(state.getValue(CROWN)));
    }
}
