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
                        run.definition().tokenReward(),
                        manager.tokenBalance(serverPlayer.getUUID())));
            } catch (IllegalStateException e) {
                player.sendSystemMessage(Component.translatable(
                        "message.expeditions.not_finished"));
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
