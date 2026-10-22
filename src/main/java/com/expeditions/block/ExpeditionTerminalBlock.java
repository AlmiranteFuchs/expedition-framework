package com.expeditions.block;

import com.expeditions.core.ExpeditionRun;
import com.expeditions.core.Waypoint;
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
 * Expedition terminal: the in-world entry point to the expedition framework.
 *
 * <p>Right-clicking starts the configured Overworld expedition when the player
 * has no active run, or reports the current run's progress otherwise. All state
 * changes happen on the server.
 */
public class ExpeditionTerminalBlock extends Block {
    public ExpeditionTerminalBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hitResult) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            ExpeditionManager manager = ExpeditionManager.get(level.getServer());
            // Apply the recovery policy on interaction too: a run whose world
            // state vanished is failed closed instead of trapping the player.
            manager.resumeOrFail(serverPlayer).ifPresentOrElse(
                    run -> reportProgress(serverPlayer, run),
                    () -> startExpedition(serverPlayer, manager));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private static void startExpedition(ServerPlayer player, ExpeditionManager manager) {
        try {
            ExpeditionRun run = manager.startRun(player);
            Waypoint first = run.nextCheckpoint().orElseThrow();
            player.sendSystemMessage(Component.translatable("message.expeditions.expedition_started",
                    run.definition().displayName()));
            player.sendSystemMessage(Component.translatable("message.expeditions.next_checkpoint",
                    run.completedCheckpoints() + 1, run.totalCheckpoints(), format(first)));
        } catch (IllegalStateException e) {
            player.sendSystemMessage(Component.literal(e.getMessage()));
        }
    }

    private static void reportProgress(ServerPlayer player, ExpeditionRun run) {
        run.nextCheckpoint().ifPresentOrElse(
                next -> player.sendSystemMessage(Component.translatable(
                        "message.expeditions.next_checkpoint",
                        run.completedCheckpoints() + 1, run.totalCheckpoints(), format(next))),
                () -> player.sendSystemMessage(Component.translatable(
                        "message.expeditions.all_checkpoints", format(run.exit()))));
    }

    static String format(Waypoint w) {
        return w.x() + ", " + w.y() + ", " + w.z();
    }
}
