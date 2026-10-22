package com.expeditions.block;

import com.expeditions.core.ExpeditionRun;
import com.expeditions.server.ExpeditionManager;
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
 * Expedition checkpoint marker. Placed by the server at each configured route
 * position when a run starts. Right-clicking activates it only if it is the
 * next expected checkpoint of that player's run.
 */
public class ExpeditionCheckpointBlock extends Block {
    public ExpeditionCheckpointBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hitResult) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            ExpeditionManager manager = ExpeditionManager.get(level.getServer());
            try {
                ExpeditionRun run = manager.activateCheckpoint(serverPlayer, pos);
                if (run.allCheckpointsDone()) {
                    player.sendSystemMessage(Component.translatable(
                            "message.expeditions.all_checkpoints",
                            ExpeditionTerminalBlock.format(run.exit())));
                } else {
                    player.sendSystemMessage(Component.translatable(
                            "message.expeditions.checkpoint_reached",
                            run.completedCheckpoints(), run.totalCheckpoints()));
                }
            } catch (IllegalStateException e) {
                player.sendSystemMessage(Component.translatable(
                        "message.expeditions.wrong_checkpoint"));
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
