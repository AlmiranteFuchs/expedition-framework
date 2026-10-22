package com.expeditions.server;

import com.expeditions.core.Waypoint;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Game-bus hooks for the expedition system: player death fails the active run,
 * and {@code /expeditions} exposes status, balance and abandon subcommands.
 */
public final class ExpeditionEventHandler {
    private ExpeditionEventHandler() {
    }

    /**
     * Dying while an expedition is active fails the run (and forfeits the
     * completion reward). Vanilla inventory/keepInventory behavior is untouched.
     */
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ExpeditionManager manager = ExpeditionManager.get(player.server);
            if (manager.activeRun(player.getUUID()).isPresent()) {
                manager.failRun(player);
                player.sendSystemMessage(Component.translatable("message.expeditions.expedition_failed"));
            }
        }
    }

    /**
     * On login, remind returning players of an expedition left in progress.
     * Disconnects never complete or reward a run; an active run simply resumes.
     */
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ExpeditionManager manager = ExpeditionManager.get(player.server);
            manager.activeRun(player.getUUID()).ifPresent(run ->
                    player.sendSystemMessage(Component.translatable(
                            "message.expeditions.expedition_resumed")));
        }
    }

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("expeditions")
                .then(Commands.literal("balance").executes(ExpeditionEventHandler::balance))
                .then(Commands.literal("status").executes(ExpeditionEventHandler::status))
                .then(Commands.literal("abandon").executes(ExpeditionEventHandler::abandon)));
    }

    private static int balance(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = player(ctx);
        if (player == null) {
            return 0;
        }
        int balance = ExpeditionManager.get(player.server).tokenBalance(player.getUUID());
        player.sendSystemMessage(Component.translatable("message.expeditions.balance", balance));
        return 1;
    }

    private static int status(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = player(ctx);
        if (player == null) {
            return 0;
        }
        ExpeditionManager manager = ExpeditionManager.get(player.server);
        manager.activeRun(player.getUUID()).ifPresentOrElse(
                run -> run.nextCheckpoint().ifPresentOrElse(
                        next -> player.sendSystemMessage(Component.translatable(
                                "message.expeditions.next_checkpoint",
                                run.completedCheckpoints() + 1, run.totalCheckpoints(),
                                format(next))),
                        () -> player.sendSystemMessage(Component.translatable(
                                "message.expeditions.all_checkpoints", format(run.exit())))),
                () -> player.sendSystemMessage(Component.translatable(
                        "message.expeditions.status_none")));
        return 1;
    }

    private static int abandon(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = player(ctx);
        if (player == null) {
            return 0;
        }
        try {
            ExpeditionManager.get(player.server).failRun(player);
            player.sendSystemMessage(Component.translatable("message.expeditions.expedition_abandoned"));
            return 1;
        } catch (IllegalStateException e) {
            player.sendSystemMessage(Component.translatable("message.expeditions.status_none"));
            return 0;
        }
    }

    private static ServerPlayer player(CommandContext<CommandSourceStack> ctx) {
        try {
            return ctx.getSource().getPlayerOrException();
        } catch (Exception e) {
            ctx.getSource().sendSystemMessage(Component.literal("Player-only command"));
            return null;
        }
    }

    private static String format(Waypoint w) {
        return w.x() + ", " + w.y() + ", " + w.z();
    }
}
