package com.expeditions.block;

import com.expeditions.server.ExpeditionManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Expedition checkpoint marker. Placed by the server at each configured route
 * position when a run starts. Right-clicking activates it only if it is the
 * next expected checkpoint of that player's run.
 */
public class ExpeditionCheckpointBlock extends RouteMarkerBlock {
    public ExpeditionCheckpointBlock(Properties properties) {
        super(properties, "expedition_checkpoint");
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hitResult) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            ExpeditionManager manager = ExpeditionManager.get(level.getServer());
            if (manager.revealFieldCache(serverPlayer, pos)) return InteractionResult.SUCCESS;
            try {
                manager.activateCheckpoint(serverPlayer, pos);
            } catch (IllegalStateException e) {
                String message = e.getMessage();
                player.displayClientMessage(Component.translatable(message != null && message.startsWith("message.expeditions.")
                        ? message : "message.expeditions.wrong_checkpoint"), true);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
