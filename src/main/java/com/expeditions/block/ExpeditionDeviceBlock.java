package com.expeditions.block;

import com.expeditions.server.ExpeditionManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Destructible defense device, deployed by a {@code defend_device} objective.
 * The block itself shares the markers' pickaxe protection — its explicit
 * health lives in the run record, and damage arrives only through
 * {@link ExpeditionManager#damageDeviceAt}: siege mobs chip it, and a player
 * punch counts as one point of sabotage. Right-click reports the siege
 * status to anyone nearby.
 */
public class ExpeditionDeviceBlock extends Block {
    public ExpeditionDeviceBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hitResult) {
        if (level instanceof ServerLevel serverLevel) {
            ExpeditionManager.get(serverLevel.getServer())
                    .defenseStatusAt(serverLevel, pos)
                    .ifPresent(status -> player.sendSystemMessage(
                            Component.translatable(
                                    "message.expeditions.device_status",
                                    status.hp(), status.maxHp(),
                                    status.remainingTicks() / 20)));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void attack(BlockState state, Level level, BlockPos pos, Player player) {
        if (level instanceof ServerLevel serverLevel) {
            ExpeditionManager.get(serverLevel.getServer())
                    .damageDeviceAt(serverLevel, pos, 1);
        }
        super.attack(state, level, pos, player);
    }
}
