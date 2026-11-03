package com.expeditions.block;

import com.expeditions.core.ExpeditionObjective;
import com.expeditions.core.ExpeditionRun;
import com.expeditions.server.ExpeditionManager;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.*;

/**
 * Expedition exit marker. Placed by the server at the configured exit position.
 * Right-clicking completes the run only when every checkpoint was activated, and
 * grants the guaranteed token reward exactly once.
 */
public class ExpeditionExitBlock extends RouteMarkerBlock {
    public static final BooleanProperty GROWN = BooleanProperty.create("grown");
    public static final BooleanProperty FULL_TREE = BooleanProperty.create("full_tree");
    public static final net.minecraft.world.level.block.state.properties.IntegerProperty TREE_LAYOUT =
            net.minecraft.world.level.block.state.properties.IntegerProperty.create("tree_layout",0,1);
    public ExpeditionExitBlock(Properties properties) {
        super(properties, "expedition_exit");
        registerDefaultState(stateDefinition.any().setValue(GROWN, false).setValue(FULL_TREE,false).setValue(TREE_LAYOUT,0));
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(GROWN,FULL_TREE,TREE_LAYOUT); }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if(state.getValue(GROWN)&&state.getValue(FULL_TREE))return Shapes.empty();
        return state.getValue(GROWN) ? FieldMachineShapes.shape("recall_tree_root", net.minecraft.core.Direction.NORTH)
                : super.getShape(state, level, pos, context);
    }
    @Override protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(GROWN) ? Shapes.empty() : super.getCollisionShape(state, level, pos, context);
    }
    @Override protected void entityInside(BlockState state, Level level, BlockPos pos, net.minecraft.world.entity.Entity entity) {
        if (state.getValue(GROWN) && !state.getValue(FULL_TREE) && !level.isClientSide && entity instanceof ServerPlayer player && player.tickCount % 20 == 0)
            recall(player, pos, true);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hitResult) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            recall(serverPlayer, pos, false);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    public static void recall(ServerPlayer serverPlayer, BlockPos pos, boolean walking) {
            var player = serverPlayer;
            ExpeditionManager manager = ExpeditionManager.get(player.server);
            var bound = manager.activeRun(player.getUUID());
            if (bound.isEmpty() || !bound.get().exit().equals(ExpeditionManager.toWaypoint(pos))
                    || !bound.get().definition().dimension().equals(player.serverLevel().dimension().location().toString())) return;
            var rootState = player.serverLevel().getBlockState(pos);
            if (!rootState.is(com.expeditions.registry.ModBlocks.EXPEDITION_EXIT.get())
                    || !com.expeditions.server.RecallTrees.intact(player.serverLevel(), pos)) return;
            // Merely approaching an unfinished hollow should not fill chat with refusals.
            if (walking && (!bound.get().allCheckpointsDone()
                    || (bound.get().definition().bigBad() && !bound.get().guardianDefeated()))) return;
            try {
                ExpeditionRun run = manager.completeRun(serverPlayer);
                player.sendSystemMessage(Component.translatable(
                        "message.expeditions.expedition_completed",
                        run.tokensAwarded(),
                        manager.tokenBalance(serverPlayer.getUUID())));
            } catch (IllegalStateException e) {
                if (walking) return; // Keep approaches quiet; timed/cargo gates are evaluated by completeRun above.
                // A refused completion is either unfinished checkpoints or
                // unfinished required objectives — tell the player which.
                Optional<ExpeditionRun> active = manager.activeRun(serverPlayer.getUUID());
                List<ExpeditionObjective> missing = active
                        .map(ExpeditionRun::missingRequiredObjectives)
                        .orElse(List.of());
                if (active.filter(run -> run.definition().bigBad() && run.allCheckpointsDone()
                        && !run.guardianDefeated()).isPresent()) {
                    player.sendSystemMessage(Component.translatable("message.expeditions.guardian_blocks_exit"));
                } else if (!missing.isEmpty()) {
                    player.sendSystemMessage(Component.translatable(
                            "message.expeditions.objectives_missing",
                            missing.stream().map(ExpeditionObjective::description)
                                    .collect(Collectors.joining(", "))));
                } else {
                    player.sendSystemMessage(Component.translatable(
                            "message.expeditions.not_finished"));
                }
            }
    }
}
