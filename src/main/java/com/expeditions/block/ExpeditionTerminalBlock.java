package com.expeditions.block;

import com.expeditions.server.ExpeditionManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Expedition terminal: the in-world entry point to the expedition framework.
 *
 * <p>For this milestone, interacting with it reports the server-authoritative
 * expedition status. Route generation and a real GUI arrive in later milestones.
 */
public class ExpeditionTerminalBlock extends Block {
    public ExpeditionTerminalBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hitResult) {
        if (!level.isClientSide) {
            // The server owns expedition state; clients only display the outcome.
            ExpeditionManager manager = ExpeditionManager.get(level.getServer());
            if (manager.activeRun().isPresent()) {
                player.sendSystemMessage(Component.translatable("message.expeditions.expedition_active"));
            } else {
                player.sendSystemMessage(Component.translatable("message.expeditions.no_active_expedition"));
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
