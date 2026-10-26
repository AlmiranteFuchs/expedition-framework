package com.expeditions.server;

import com.expeditions.core.Difficulty;
import com.expeditions.core.ExpeditionOptions;
import com.expeditions.core.ExpeditionRun;
import com.expeditions.core.Waypoint;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

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
     * On login, apply the recovery policy: a valid active run resumes (the
     * player is told where to go next), an unrecoverable one is failed
     * closed. Disconnects never complete or reward a run.
     */
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ExpeditionManager manager = ExpeditionManager.get(player.server);
            // Runs that ended while the member was offline can leave a compass
            // behind in their inventory.
            manager.sweepCompasses(player);
            boolean hadRun = manager.activeRun(player.getUUID()).isPresent();
            manager.resumeOrFail(player).ifPresentOrElse(
                    run -> player.sendSystemMessage(Component.translatable(
                            "message.expeditions.expedition_resumed")),
                    () -> {
                        if (hadRun) {
                            player.sendSystemMessage(Component.translatable(
                                    "message.expeditions.expedition_not_restored"));
                        }
                    });
        }
    }

    /**
     * After the server finishes starting, remove marker blocks that no active
     * run references (e.g. left behind by a crash between chunk save and the
     * expedition data save).
     */
    public static void onServerStarted(net.neoforged.neoforge.event.server.ServerStartedEvent event) {
        ExpeditionManager.pruneOrphanMarkers(event.getServer());
    }

    /**
     * Once a second, retry discarding challenge entities whose cleanup ran
     * while their chunk was unloaded — a tracked horde can never outlive its
     * expedition unnoticed.
     */
    public static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % 20 == 0) {
            ExpeditionManager manager = ExpeditionManager.get(event.getServer());
            manager.tickDefenses();
            manager.sweepChallengeEntities();
        }
    }

    /**
     * Feeds item pickups into collect_items objectives. The event fires after
     * the stack entered the inventory; the picked amount is the item entity's
     * stack minus its remainder. Only server-side players in an active run
     * progress objectives.
     */
    public static void onItemPickup(ItemEntityPickupEvent.Post event) {
        if (event.getPlayer() instanceof ServerPlayer player) {
            int count = event.getOriginalStack().getCount()
                    - event.getCurrentStack().getCount();
            if (count > 0) {
                ItemStack picked = event.getOriginalStack();
                picked.setCount(count);
                ExpeditionManager.get(player.server).onItemPickup(player, picked);
            }
        }
    }

    /** Registers the datapack expedition-definition loader. */
    public static void onAddReloadListener(
            net.neoforged.neoforge.event.AddReloadListenerEvent event) {
        event.addListener(new com.expeditions.server.data.ExpeditionDefinitionLoader());
    }

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("expeditions")
                .then(Commands.literal("balance").executes(ExpeditionEventHandler::balance))
                .then(Commands.literal("status").executes(ExpeditionEventHandler::status))
                .then(Commands.literal("abandon").executes(ExpeditionEventHandler::abandon))
                .then(Commands.literal("redeem").executes(ExpeditionEventHandler::redeem))
                .then(Commands.literal("start")
                        .then(Commands.argument("expedition", ResourceLocationArgument.id())
                                .suggests(EXPEDITION_SUGGESTIONS)
                                .executes(ExpeditionEventHandler::start)
                                .then(Commands.argument("difficulty", StringArgumentType.word())
                                        .suggests(DIFFICULTY_SUGGESTIONS)
                                        .executes(ExpeditionEventHandler::start)
                                        .then(Commands.argument("checkpoints",
                                                        IntegerArgumentType.integer(0, 16))
                                                .executes(ExpeditionEventHandler::start)
                                                .then(Commands.argument("time",
                                                                IntegerArgumentType.integer(0, 400))
                                                        .executes(ExpeditionEventHandler::start)))))));
    }

    private static final SuggestionProvider<CommandSourceStack> EXPEDITION_SUGGESTIONS =
            (ctx, builder) -> SharedSuggestionProvider.suggest(
                    ExpeditionManager.get(ctx.getSource().getServer()).listStartable()
                            .stream().map(d -> d.id()).toList(),
                    builder);

    private static final SuggestionProvider<CommandSourceStack> DIFFICULTY_SUGGESTIONS =
            (ctx, builder) -> SharedSuggestionProvider.suggest(
                    java.util.Arrays.stream(Difficulty.values())
                            .map(d -> d.name().toLowerCase(java.util.Locale.ROOT)).toList(),
                    builder);

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

    /**
     * `/expeditions redeem` — the token sink: spends the configured cost on a
     * supply draw from the current dimension's reward item pool.
     */
    private static int redeem(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = player(ctx);
        if (player == null) {
            return 0;
        }
        return ExpeditionManager.get(player.server).redeemTokens(player) ? 1 : 0;
    }

    /**
     * `/expeditions start <expedition> [difficulty] [checkpoints] [time]` —
     * the no-GUI twin of the terminal menu: starts the named expedition with
     * optional per-run modifiers, with the portal above the player (there is
     * no terminal block in command context — the player's feet anchor it).
     */
    private static int start(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = player(ctx);
        if (player == null) {
            return 0;
        }
        String expeditionId = ResourceLocationArgument.getId(ctx, "expedition").toString();
        Difficulty difficulty = Difficulty.byName(
                argOr(ctx, "difficulty", String.class, "standard"));
        if (difficulty == null) {
            player.sendSystemMessage(Component.translatable(
                    "message.expeditions.unknown_difficulty"));
            return 0;
        }
        ExpeditionOptions options = new ExpeditionOptions(expeditionId, difficulty,
                argOr(ctx, "checkpoints", Integer.class, 0),
                argOr(ctx, "time", Integer.class, 0));
        try {
            ExpeditionRun run = ExpeditionManager.get(player.server)
                    .startRun(player, player.blockPosition(), options);
            player.sendSystemMessage(Component.translatable(
                    "message.expeditions.expedition_started",
                    run.definition().displayName()));
            return 1;
        } catch (IllegalStateException e) {
            player.sendSystemMessage(Component.literal(e.getMessage()));
            return 0;
        }
    }

    /** Reads an optional command argument, falling back when it was not given. */
    private static <T> T argOr(CommandContext<CommandSourceStack> ctx, String name,
            Class<T> type, T fallback) {
        try {
            return ctx.getArgument(name, type);
        } catch (IllegalArgumentException e) {
            return fallback;
        }
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
