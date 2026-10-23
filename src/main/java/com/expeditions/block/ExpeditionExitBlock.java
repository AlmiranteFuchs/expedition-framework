package com.expeditions.block;

import com.expeditions.core.ExpeditionObjective;
import com.expeditions.core.ExpeditionRun;
import com.expeditions.server.ExpeditionManager;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Expedition exit marker. Placed by the server at the configured exit position.
 * Right-clicking completes the run only when every checkpoint was activated, and
 * grants the guaranteed token reward exactly once.
 */
public class ExpeditionExitBlock extends Block {
    public ExpeditionExitBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hitResult) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            ExpeditionManager manager = ExpeditionManager.get(level.getServer());
            try {
                ExpeditionRun run = manager.completeRun(serverPlayer);
                player.sendSystemMessage(Component.translatable(
                        "message.expeditions.expedition_completed",
                        run.tokensAwarded(),
                        manager.tokenBalance(serverPlayer.getUUID())));
            } catch (IllegalStateException e) {
                // A refused completion is either unfinished checkpoints or
                // unfinished required objectives — tell the player which.
                Optional<ExpeditionRun> active = manager.activeRun(serverPlayer.getUUID());
                if (active.isPresent()
                        && !active.get().missingRequiredObjectives().isEmpty()) {
                    StringBuilder names = new StringBuilder();
                    for (ExpeditionObjective objective : active.get().missingRequiredObjectives()) {
                        if (names.length() > 0) {
                            names.append(", ");
                        }
                        names.append(objective.description());
                    }
                    player.sendSystemMessage(Component.translatable(
                            "message.expeditions.objectives_missing", names.toString()));
                } else {
                    player.sendSystemMessage(Component.translatable(
                            "message.expeditions.not_finished"));
                }
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
