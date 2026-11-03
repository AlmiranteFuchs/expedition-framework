package com.expeditions.block;

import com.expeditions.server.RecallTrees;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;

/** Every visible segment resolves to its root; no client-supplied address or independent reward path. */
public final class RecallTreePartBlock extends RouteMarkerBlock {
    public static final IntegerProperty PART = IntegerProperty.create("part", 0, 7);

    public RecallTreePartBlock(Properties properties) {
        super(properties, "recall_tree_0");
        registerDefaultState(stateDefinition.any().setValue(PART, 0));
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(PART); }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return FieldMachineShapes.shape("recall_tree_" + state.getValue(PART), Direction.NORTH);
    }
    @Override protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(PART) == 7 ? Shapes.empty() : getShape(state, level, pos, context);
    }
    private static BlockPos root(BlockState state, BlockPos pos) { return pos.subtract(RecallTrees.OFFSETS.get(state.getValue(PART))); }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) ExpeditionExitBlock.recall(serverPlayer, root(state, pos), false);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (state.getValue(PART) == 7 && !level.isClientSide && entity instanceof ServerPlayer player && player.tickCount % 20 == 0)
            ExpeditionExitBlock.recall(player, root(state, pos), true);
    }
    @Override public void animateTick(BlockState state, Level level, BlockPos pos, net.minecraft.util.RandomSource random) {
        if (state.getValue(PART) == 7) super.animateTick(state, level, pos, random);
    }
}
