package com.expeditions.gametest;

import com.expeditions.core.Difficulty;
import com.expeditions.core.EffectSpec;
import com.expeditions.core.ExpeditionDefinition;
import com.expeditions.core.ExpeditionLedger;
import com.expeditions.core.ExpeditionOptions;
import com.expeditions.core.ExpeditionRoute;
import com.expeditions.core.ExpeditionRun;
import com.expeditions.core.ExpeditionStatus;
import com.expeditions.core.ObjectiveState;
import com.expeditions.core.RouteAnchor;
import com.expeditions.core.RouteGeneration;
import com.expeditions.ExpeditionsConfig;
import com.expeditions.core.WeatherModifier;
import com.expeditions.core.Waypoint;
import com.expeditions.registry.ModBlocks;
import com.expeditions.registry.ModItems;
import com.expeditions.server.ExpeditionManager;
import com.expeditions.server.ExpeditionMenu;
import com.expeditions.server.ExpeditionSavedData;
import com.expeditions.server.HordePools;
import com.expeditions.server.ItemRewards;
import com.expeditions.server.ItemValuator;
import com.expeditions.server.data.ExpeditionDefinitions;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.entity.EntitySection;
import net.minecraft.world.level.entity.EntitySectionStorage;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.component.LodestoneTracker;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * In-game integration tests for the full expedition loop. They run inside the
 * GameTest server (flat world) and drive the same block interaction code paths
 * real players use.
 */
@GameTestHolder("expeditions")
@PrefixGameTestTemplate(false)
public final class ExpeditionGameTests {
    private ExpeditionGameTests() {
    }

    /**
     * Full loop: start at the terminal, activate all three checkpoints in
     * order, exit, verify completion, reward and marker cleanup.
     */
    @GameTest(template = "arena", timeoutTicks = 400, batch = "expedition_a")
    public static void expeditionCompletesAndRewards(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        ExpeditionManager manager = ExpeditionManager.get(helper.getLevel().getServer());
        UUID playerId = player.getUUID();

        ExpeditionRun run = startExpedition(helper, manager, player);
        helper.assertTrue(run.completedCheckpoints() == 0, "run must start with zero checkpoints");

        for (Waypoint checkpoint : run.checkpoints()) {
            useBlockAt(helper, player, ExpeditionManager.toPos(checkpoint));
        }
        helper.assertTrue(run.allCheckpointsDone(), "all checkpoints must be activated");

        // Other tests/retries share the configured marker positions; end any
        // foreign active runs so marker removal is verifiable deterministically.
        for (ExpeditionRun other : manager.runs()) {
            if (other.isActive() && !other.playerId().equals(playerId)) {
                other.fail();
            }
        }

        BlockPos exitPos = ExpeditionManager.toPos(run.exit());
        useBlockAt(helper, player, exitPos);

        helper.assertTrue(run.status() == ExpeditionStatus.COMPLETED,
                "run must be COMPLETED, was " + run.status());
        helper.assertTrue(run.isRewarded(), "run must be rewarded");
        // The test completes well inside the bonus window, so the award is
        // the guaranteed reward plus the time bonus.
        int expectedTokens = run.definition().tokenReward() + run.definition().bonusTokens();
        helper.assertTrue(run.tokensAwarded() == expectedTokens,
                "awarded tokens must be " + expectedTokens + ", was " + run.tokensAwarded());
        helper.assertTrue(manager.tokenBalance(playerId) == expectedTokens,
                "token balance must be " + expectedTokens + ", was " + manager.tokenBalance(playerId));

        // Marker blocks are removed once the run ends.
        for (Waypoint checkpoint : run.checkpoints()) {
            helper.assertTrue(
                    helper.getLevel().getBlockState(ExpeditionManager.toPos(checkpoint)).isAir(),
                    "checkpoint marker must be removed");
        }
        helper.assertTrue(helper.getLevel().getBlockState(exitPos).isAir(),
                "exit marker must be removed");

        // A second exit-style completion is impossible: the run is terminal and
        // the token balance cannot grow.
        int balance = manager.tokenBalance(playerId);
        helper.assertTrue(manager.tokenBalance(playerId) == balance, "balance must be stable");

        helper.succeed();
    }

    /**
     * Dying while a run is active fails it: no completion, no reward.
     */
    @GameTest(template = "arena", timeoutTicks = 400, batch = "expedition_b")
    public static void deathFailsRun(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        ExpeditionManager manager = ExpeditionManager.get(helper.getLevel().getServer());
        UUID playerId = player.getUUID();

        ExpeditionRun run = startExpedition(helper, manager, player);
        player.hurt(player.damageSources().genericKill(), Float.MAX_VALUE);

        helper.assertTrue(run.status() == ExpeditionStatus.FAILED,
                "run must be FAILED after death, was " + run.status());
        helper.assertTrue(manager.tokenBalance(playerId) == 0,
                "no tokens may be granted for a failed run");
        helper.assertTrue(manager.activeRun(playerId).isEmpty(),
                "failed run must no longer be active");

        helper.succeed();
    }

    /**
     * Checkpoints reject out-of-order activation: clicking checkpoint 2 first
     * leaves the run at zero progress.
     */
    @GameTest(template = "arena", timeoutTicks = 400, batch = "expedition_c")
    public static void checkpointsRejectOutOfOrder(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        ExpeditionManager manager = ExpeditionManager.get(helper.getLevel().getServer());

        ExpeditionRun run = startExpedition(helper, manager, player);
        BlockPos second = ExpeditionManager.toPos(run.checkpoints().get(1));
        useBlockAt(helper, player, second);
        helper.assertTrue(run.completedCheckpoints() == 0,
                "out-of-order activation must not advance the run");

        // Activate in order, then try the same checkpoint again (duplicate).
        useBlockAt(helper, player, ExpeditionManager.toPos(run.checkpoints().get(0)));
        useBlockAt(helper, player, ExpeditionManager.toPos(run.checkpoints().get(0)));
        helper.assertTrue(run.completedCheckpoints() == 1,
                "duplicate activation must be rejected");

        manager.failRun(player); // leave no active run behind for later tests
        helper.succeed();
    }

    /**
     * The exit refuses to complete a run that still has checkpoints open.
     */
    @GameTest(template = "arena", timeoutTicks = 400, batch = "expedition_d")
    public static void exitRejectsUnfinishedRun(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        ExpeditionManager manager = ExpeditionManager.get(helper.getLevel().getServer());
        UUID playerId = player.getUUID();

        ExpeditionRun run = startExpedition(helper, manager, player);
        useBlockAt(helper, player, ExpeditionManager.toPos(run.exit()));

        helper.assertTrue(run.status() == ExpeditionStatus.ACTIVE,
                "unfinished run must stay ACTIVE, was " + run.status());
        helper.assertTrue(manager.tokenBalance(playerId) == 0, "no tokens may be granted");

        manager.failRun(player); // leave no active run behind for later tests
        helper.succeed();
    }

    /**
     * Starting in the Nether resolves the configured columns through the Nether
     * adapter: waypoints land inside its safe scan window and the marker blocks
     * are actually placed there.
     */
    @GameTest(template = "arena", timeoutTicks = 1200, batch = "expedition_e")
    public static void netherExpeditionResolvesRoute(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel nether = server.getLevel(Level.NETHER);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();

        BlockPos terminalPos = new BlockPos(0, 80, 0);
        clearAndFloor(nether, terminalPos);
        nether.setBlock(terminalPos, ModBlocks.EXPEDITION_TERMINAL.get().defaultBlockState(), 3);
        player.teleportTo(nether, 1.5, 80.0, 0.5, Set.of(), 0f, 0f);
        ExpeditionManager manager = ExpeditionManager.get(server);
        // A generated route may refuse on a bad roll — a player just clicks
        // the terminal again, so retry the same way a few times.
        for (int attempt = 0;
                attempt < 5 && manager.activeRun(player.getUUID()).isEmpty(); attempt++) {
            useBlockAt(nether, player, terminalPos);
            clickMenuStart(player);
        }

        UUID playerId = player.getUUID();
        ExpeditionRun run = manager.activeRun(playerId).orElseThrow(
                () -> new IllegalStateException("terminal interaction must start a nether run"));
        helper.assertTrue("minecraft:the_nether".equals(run.definition().dimension()),
                "run must target the nether, was " + run.definition().dimension());

        for (Waypoint w : run.checkpoints()) {
            helper.assertTrue(w.y() >= 33 && w.y() <= 122,
                    "nether checkpoint must resolve inside the safe scan window, was " + w.y());
            helper.assertTrue(nether.getBlockState(ExpeditionManager.toPos(w))
                            .is(ModBlocks.EXPEDITION_CHECKPOINT.get()),
                    "nether checkpoint marker must be placed at " + w);
        }
        helper.assertTrue(nether.getBlockState(ExpeditionManager.toPos(run.exit()))
                        .is(ModBlocks.EXPEDITION_EXIT.get()),
                "nether exit marker must be placed");

        // End any foreign active runs (test retries leak mock players) so
        // marker removal is verifiable deterministically.
        for (ExpeditionRun other : manager.runs()) {
            if (other.isActive() && !other.playerId().equals(playerId)) {
                other.fail();
            }
        }
        manager.failRun(player);
        for (Waypoint w : run.checkpoints()) {
            helper.assertTrue(nether.getBlockState(ExpeditionManager.toPos(w)).isAir(),
                    "nether markers must be removed when the run ends");
        }
        helper.succeed();
    }

    /**
     * Dimensions without a configured expedition and adapter — here the End —
     * host no local expedition: a terminal click opens the options menu but
     * starts nothing on its own.
     */
    @GameTest(template = "arena", timeoutTicks = 1200, batch = "expedition_f")
    public static void unsupportedDimensionRejected(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel end = server.getLevel(Level.END);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();

        BlockPos terminalPos = new BlockPos(0, 60, 0);
        clearAndFloor(end, terminalPos);
        end.setBlock(terminalPos, ModBlocks.EXPEDITION_TERMINAL.get().defaultBlockState(), 3);
        player.teleportTo(end, 1.5, 60.0, 0.5, Set.of(), 0f, 0f);
        useBlockAt(end, player, terminalPos);

        ExpeditionManager manager = ExpeditionManager.get(server);
        helper.assertTrue(manager.activeRun(player.getUUID()).isEmpty(),
                "no run may start in an unsupported dimension");
        helper.succeed();
    }

    /**
     * Save→load round trip: an active mid-run player and a completed, rewarded
     * player both come back with identical state — status, progress, resolved
     * route, reward flags and token balances.
     */
    @GameTest(template = "arena", timeoutTicks = 600, batch = "expedition_g")
    public static void persistenceRoundTrips(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ExpeditionManager manager = ExpeditionManager.get(server);

        ServerPlayer active = helper.makeMockServerPlayerInLevel();
        ExpeditionRun activeRun = startExpedition(helper, manager, active);
        manager.activateCheckpoint(active, ExpeditionManager.toPos(activeRun.checkpoints().get(0)));

        ServerPlayer done = helper.makeMockServerPlayerInLevel();
        ExpeditionRun doneRun = startExpedition(helper, manager, done);
        for (Waypoint w : doneRun.checkpoints()) {
            manager.activateCheckpoint(done, ExpeditionManager.toPos(w));
        }
        manager.completeRun(done);
        int expectedBalance = manager.tokenBalance(done.getUUID());

        ExpeditionSavedData loaded = roundTrip(helper, server);
        ExpeditionLedger ledger = loaded.ledger();

        ExpeditionRun restoredActive = ledger.activeRun(active.getUUID()).orElseThrow(
                () -> new IllegalStateException("active run must survive a save/load"));
        helper.assertTrue(restoredActive.status() == ExpeditionStatus.ACTIVE,
                "restored run must be ACTIVE");
        helper.assertTrue(restoredActive.completedCheckpoints() == 1,
                "checkpoint progress must survive, was " + restoredActive.completedCheckpoints());
        helper.assertTrue(restoredActive.checkpoints().equals(activeRun.checkpoints()),
                "resolved route must be preserved");
        helper.assertTrue(restoredActive.startedGameTime() == activeRun.startedGameTime(),
                "start time must be preserved");

        ExpeditionRun restoredDone = ledger.run(done.getUUID()).orElseThrow(
                () -> new IllegalStateException("completed run must survive a save/load"));
        helper.assertTrue(restoredDone.status() == ExpeditionStatus.COMPLETED,
                "terminal state must survive");
        helper.assertTrue(restoredDone.isRewarded(),
                "reward flag must survive");
        helper.assertTrue(restoredDone.tokensAwarded() == doneRun.tokensAwarded(),
                "awarded amount must survive");
        helper.assertTrue(ledger.tokenBalance(done.getUUID()) == expectedBalance,
                "token balance must survive");

        manager.failRun(active); // leave no active run behind for later tests
        helper.succeed();
    }

    /**
     * Malformed persisted records are skipped without corrupting the rest:
     * an unknown status and a structurally invalid run are dropped while a
     * valid balance still loads; a negative balance entry is dropped.
     */
    @GameTest(template = "arena", timeoutTicks = 400, batch = "expedition_h")
    public static void malformedSaveDataSkipped(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        ExpeditionManager manager = ExpeditionManager.get(server);
        startExpedition(helper, manager, player);
        manager.failRun(player);

        ExpeditionSavedData data = savedData(server);
        CompoundTag tag = data.save(new CompoundTag(), helper.getLevel().registryAccess());
        // Corrupt every run record (the shared ledger may hold runs from other
        // tests' mock players): an unknown status and stripped checkpoints.
        for (Tag t : tag.getList("Runs", Tag.TAG_COMPOUND)) {
            CompoundTag runTag = (CompoundTag) t;
            runTag.putString("Status", "BOGUS");
            runTag.put("Checkpoints", new ListTag());
        }

        CompoundTag goodBalance = new CompoundTag();
        goodBalance.putUUID("Player", player.getUUID());
        goodBalance.putInt("Balance", 42);
        CompoundTag badBalance = new CompoundTag();
        badBalance.putUUID("Player", UUID.randomUUID());
        badBalance.putInt("Balance", -99);
        ListTag tokens = new ListTag();
        tokens.add(goodBalance);
        tokens.add(badBalance);
        tag.put("Tokens", tokens);

        ExpeditionSavedData loaded =
                ExpeditionSavedData.load(tag, helper.getLevel().registryAccess());
        helper.assertTrue(loaded.ledger().runs().isEmpty(),
                "corrupt run records must be skipped");
        helper.assertTrue(loaded.ledger().tokenBalance(player.getUUID()) == 42,
                "valid balance must still load");
        helper.assertTrue(loaded.ledger().tokenBalances().size() == 1,
                "negative balance must be skipped");
        helper.succeed();
    }

    /**
     * Recovery policy — invalid world state: a run whose marker blocks vanished
     * (crash, rollback, admin removal) fails closed on resume: FAILED, no
     * reward, remaining markers cleaned up.
     */
    @GameTest(template = "arena", timeoutTicks = 400, batch = "expedition_i")
    public static void resumeFailsWhenMarkersVanish(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ExpeditionManager manager = ExpeditionManager.get(level.getServer());
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        UUID playerId = player.getUUID();

        ExpeditionRun run = startExpedition(helper, manager, player);
        // Simulate losing a marker (e.g. rolled-back chunk): remove the block
        // directly, bypassing the manager's tracking.
        level.removeBlock(ExpeditionManager.toPos(run.checkpoints().get(0)), false);

        // End foreign active runs (test retries leak mock players) so marker
        // cleanup is not held back by runs sharing the configured positions.
        for (ExpeditionRun other : manager.runs()) {
            if (other.isActive() && !other.playerId().equals(playerId)) {
                other.fail();
            }
        }
        helper.assertTrue(manager.resumeOrFail(player).isEmpty(),
                "unrecoverable run must not resume");
        helper.assertTrue(run.status() == ExpeditionStatus.FAILED,
                "unrecoverable run must fail closed, was " + run.status());
        helper.assertTrue(manager.tokenBalance(playerId) == 0,
                "no tokens may be granted");
        for (Waypoint w : run.checkpoints()) {
            helper.assertTrue(level.getBlockState(ExpeditionManager.toPos(w)).isAir(),
                    "surviving markers must be cleaned up");
        }
        helper.succeed();
    }

    /**
     * Recovery policy — valid state: a reconnect-style resume keeps the run
     * with its progress, and starting a second concurrent run stays impossible.
     */
    @GameTest(template = "arena", timeoutTicks = 400, batch = "expedition_j")
    public static void resumeKeepsValidRunAndBlocksSecondStart(GameTestHelper helper) {
        ExpeditionManager manager = ExpeditionManager.get(helper.getLevel().getServer());
        ServerPlayer player = helper.makeMockServerPlayerInLevel();

        ExpeditionRun run = startExpedition(helper, manager, player);
        manager.activateCheckpoint(player, ExpeditionManager.toPos(run.checkpoints().get(0)));

        ExpeditionRun resumed = manager.resumeOrFail(player).orElseThrow(
                () -> new IllegalStateException("valid run must resume"));
        helper.assertTrue(resumed == run, "the same run instance must resume");
        helper.assertTrue(resumed.completedCheckpoints() == 1,
                "progress must be intact after resume");

        boolean secondRunRejected = false;
        try {
            manager.startRun(player, helper.absolutePos(new BlockPos(1, 1, 1)));
        } catch (IllegalStateException e) {
            secondRunRejected = true;
        }
        helper.assertTrue(secondRunRejected,
                "a second active run must not start while one is valid");

        manager.failRun(player); // leave no active run behind for later tests
        helper.succeed();
    }

    /**
     * Reward integrity across reload: a completed, rewarded run restores as
     * terminal — it cannot be completed, rewarded or interacted with again.
     */
    @GameTest(template = "arena", timeoutTicks = 400, batch = "expedition_k")
    public static void rewardNotDuplicatedByReloadOrRepeat(GameTestHelper helper) {
        ExpeditionManager manager = ExpeditionManager.get(helper.getLevel().getServer());
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        UUID playerId = player.getUUID();

        ExpeditionRun run = startExpedition(helper, manager, player);
        for (Waypoint w : run.checkpoints()) {
            manager.activateCheckpoint(player, ExpeditionManager.toPos(w));
        }
        manager.completeRun(player);
        int balance = manager.tokenBalance(playerId);

        // Repeat completion attempt: no active run exists, so nothing happens.
        boolean repeatRejected = false;
        try {
            manager.completeRun(player);
        } catch (IllegalStateException e) {
            repeatRejected = true;
        }
        helper.assertTrue(repeatRejected, "a terminal run must not complete again");
        helper.assertTrue(manager.tokenBalance(playerId) == balance,
                "repeat completion must not pay out again");

        // Reload: the restored run is terminal and already rewarded.
        ExpeditionSavedData loaded = roundTrip(helper, helper.getLevel().getServer());
        ExpeditionRun restored = loaded.ledger().run(playerId).orElseThrow(
                () -> new IllegalStateException("run must survive the save/load"));
        boolean secondRewardRejected = false;
        try {
            restored.markRewarded(8);
        } catch (IllegalStateException e) {
            secondRewardRejected = true;
        }
        helper.assertTrue(secondRewardRejected,
                "a restored rewarded run must reject another reward");
        helper.assertTrue(loaded.ledger().tokenBalance(playerId) == balance,
                "balance must be unchanged after reload");
        helper.succeed();
    }

    /**
     * Startup cleanup: tracked marker blocks no active run references (e.g.
     * orphaned by a crash) are removed, while markers of a live run survive.
     */
    @GameTest(template = "arena", timeoutTicks = 400, batch = "expedition_l")
    public static void orphanMarkersPrunedOnStart(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        ExpeditionManager manager = ExpeditionManager.get(server);
        ExpeditionSavedData data = savedData(server);

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        ExpeditionRun run = startExpedition(helper, manager, player);

        // Simulate a crash-orphaned marker: block placed + tracked, but no
        // run references it.
        BlockPos orphanPos = ExpeditionManager.toPos(run.exit()).offset(3, 0, 0);
        level.setBlock(orphanPos, ModBlocks.EXPEDITION_EXIT.get().defaultBlockState(), 3);
        data.trackMarker("minecraft:overworld", orphanPos);

        ExpeditionManager.pruneOrphanMarkers(server);

        helper.assertTrue(level.getBlockState(orphanPos).isAir(),
                "orphaned marker must be pruned");
        helper.assertTrue(!savedData(server).liveMarkers().values().stream()
                        .anyMatch(set -> set.contains(orphanPos)),
                "orphaned marker must be untracked");
        for (Waypoint w : run.checkpoints()) {
            helper.assertTrue(level.getBlockState(ExpeditionManager.toPos(w))
                            .is(ModBlocks.EXPEDITION_CHECKPOINT.get()),
                    "live run's markers must survive pruning");
        }

        manager.failRun(player); // leave no active run behind for later tests
        helper.succeed();
    }

    /**
     * A datapack expedition definition is parsed into the domain shape: id
     * from the file path, route anchors, rewards and bonus time converted to
     * ticks.
     */
    @GameTest(template = "arena", timeoutTicks = 400, batch = "expedition_m")
    public static void datapackDefinitionParses(GameTestHelper helper) {
        String json = """
                {
                  "display_name": "Custom Trial",
                  "dimension": "minecraft:overworld",
                  "difficulty": 3,
                  "checkpoints": [[10, 10], [20, 20]],
                  "exit": [30, 30],
                  "tokens": 25,
                  "bonus_tokens": 5,
                  "bonus_time_seconds": 60
                }
                """;
        Map<ResourceLocation, JsonElement> entries = Map.of(
                ResourceLocation.fromNamespaceAndPath("test", "custom_trial"),
                JsonParser.parseString(json));
        try {
            ExpeditionDefinitions.reload(entries);

            ExpeditionDefinition def = ExpeditionDefinitions
                    .forDimension("minecraft:overworld").orElseThrow(
                            () -> new IllegalStateException("datapack definition must load"));
            helper.assertTrue(def.id().equals("test:custom_trial"),
                    "id must come from the file path, was " + def.id());
            helper.assertTrue(def.displayName().equals("Custom Trial"), "display name must parse");
            helper.assertTrue(def.route() instanceof ExpeditionRoute route
                            && route.checkpoints().size() == 2, "route must parse");
            helper.assertTrue(def.tokenReward() == 25, "tokens must parse");
            helper.assertTrue(def.bonusTimeTicks() == 1200,
                    "bonus time must convert seconds to ticks, was " + def.bonusTimeTicks());
            helper.assertTrue(def.totalReward(600) == 30,
                    "bonus must apply inside the window");
        } finally {
            ExpeditionDefinitions.reload(Map.of());
        }
        helper.succeed();
    }

    /**
     * A datapack definition for the same dimension takes precedence over the
     * config-defined expedition: the terminal starts the datapack one.
     */
    @GameTest(template = "arena", timeoutTicks = 400, batch = "expedition_n")
    public static void datapackDefinitionShadowsConfig(GameTestHelper helper) {
        String json = """
                {
                  "display_name": "Datapack Expedition",
                  "dimension": "minecraft:overworld",
                  "checkpoints": [[8, 8], [16, 8], [24, 8]],
                  "exit": [32, 8],
                  "tokens": 42
                }
                """;
        Map<ResourceLocation, JsonElement> entries = Map.of(
                ResourceLocation.fromNamespaceAndPath("test", "shadowing"),
                JsonParser.parseString(json));
        ExpeditionManager manager = ExpeditionManager.get(helper.getLevel().getServer());
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        try {
            ExpeditionDefinitions.reload(entries);
            ExpeditionRun run = startExpedition(helper, manager, player);
            helper.assertTrue(run.definition().id().equals("test:shadowing"),
                    "datapack definition must be used, was " + run.definition().id());
            helper.assertTrue(run.definition().tokenReward() == 42,
                    "datapack reward must be used");
            manager.failRun(player); // leave no active run behind for later tests
        } finally {
            ExpeditionDefinitions.reload(Map.of());
        }
        helper.succeed();
    }

    /**
     * Malformed datapack entries are skipped, not fatal: a bad file never
     * prevents valid siblings from loading.
     */
    @GameTest(template = "arena", timeoutTicks = 400, batch = "expedition_o")
    public static void malformedDatapackSkipped(GameTestHelper helper) {
        Map<ResourceLocation, JsonElement> entries = Map.of(
                ResourceLocation.fromNamespaceAndPath("test", "broken"),
                JsonParser.parseString("{\"dimension\": \"minecraft:overworld\"}"),
                ResourceLocation.fromNamespaceAndPath("test", "valid"),
                JsonParser.parseString("""
                        {
                          "dimension": "minecraft:overworld",
                          "checkpoints": [[1, 1]],
                          "exit": [2, 2]
                        }
                        """));
        try {
            ExpeditionDefinitions.reload(entries);
            helper.assertTrue(ExpeditionDefinitions.all().size() == 1,
                    "only the valid definition must load, got " + ExpeditionDefinitions.all().size());
            helper.assertTrue(ExpeditionDefinitions.all().containsKey("test:valid"),
                    "the valid definition must be present");
        } finally {
            ExpeditionDefinitions.reload(Map.of());
        }
        helper.succeed();
    }

    /**
     * A datapack expedition with a "generate" block produces a fresh route per
     * run anchored to the player's position at the terminal — resolved and
     * materialized like an authored route.
     */
    @GameTest(template = "arena", timeoutTicks = 400, batch = "expedition_p")
    public static void generatedRouteRuns(GameTestHelper helper) {
        String json = """
                {
                  "display_name": "Wildlands",
                  "dimension": "minecraft:overworld",
                  "generate": { "checkpoints": 2, "min_spacing": 16,
                                "max_spacing": 24, "exit_min": 24, "exit_max": 32 },
                  "tokens": 30
                }
                """;
        Map<ResourceLocation, JsonElement> entries = Map.of(
                ResourceLocation.fromNamespaceAndPath("test", "generated"),
                JsonParser.parseString(json));
        ExpeditionManager manager = ExpeditionManager.get(helper.getLevel().getServer());
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        try {
            ExpeditionDefinitions.reload(entries);
            ExpeditionRun run = startExpedition(helper, manager, player);
            helper.assertTrue(run.definition().id().equals("test:generated"),
                    "generated definition must be used");
            helper.assertTrue(run.checkpoints().size() == 2,
                    "generated route must have 2 checkpoints");
            for (Waypoint w : run.checkpoints()) {
                helper.assertTrue(helper.getLevel().getBlockState(ExpeditionManager.toPos(w))
                                .is(ModBlocks.EXPEDITION_CHECKPOINT.get()),
                        "generated checkpoint marker must be placed at " + w);
            }
            helper.assertTrue(helper.getLevel().getBlockState(ExpeditionManager.toPos(run.exit()))
                            .is(ModBlocks.EXPEDITION_EXIT.get()),
                    "generated exit marker must be placed");

            // End foreign runs so marker cleanup is not held back, then end
            // this run and confirm its generated markers are removed.
            for (ExpeditionRun other : manager.runs()) {
                if (other.isActive() && !other.playerId().equals(player.getUUID())) {
                    other.fail();
                }
            }
            manager.failRun(player);
            for (Waypoint w : run.checkpoints()) {
                helper.assertTrue(helper.getLevel().getBlockState(ExpeditionManager.toPos(w))
                                .isAir(), "generated markers must be cleaned up");
            }
        } finally {
            ExpeditionDefinitions.reload(Map.of());
        }
        helper.succeed();
    }

    /**
     * The expedition compass is granted on start, retargets to the next
     * checkpoint after each activation, points at the exit once all are done,
     * and is removed on completion.
     */
    @GameTest(template = "arena", timeoutTicks = 400, batch = "expedition_q")
    public static void compassTracksRouteAndIsRemoved(GameTestHelper helper) {
        ExpeditionManager manager = ExpeditionManager.get(helper.getLevel().getServer());
        ServerPlayer player = helper.makeMockServerPlayerInLevel();

        ExpeditionRun run = startExpedition(helper, manager, player);
        manager.enterPortal(player, ExpeditionManager.toPos(run.portal()));
        assertCompassTarget(helper, player, ExpeditionManager.toPos(run.checkpoints().get(0)));

        manager.activateCheckpoint(player, ExpeditionManager.toPos(run.checkpoints().get(0)));
        assertCompassTarget(helper, player, ExpeditionManager.toPos(run.checkpoints().get(1)));

        for (Waypoint w : run.checkpoints().subList(1, run.checkpoints().size())) {
            manager.activateCheckpoint(player, ExpeditionManager.toPos(w));
        }
        assertCompassTarget(helper, player, ExpeditionManager.toPos(run.exit()));

        manager.completeRun(player);
        helper.assertTrue(compassOf(player).isEmpty(),
                "completed run must remove the compass");
        helper.succeed();
    }

    /**
     * A failed run (death or abandon) also removes the compass — it can never
     * outlive its run.
     */
    @GameTest(template = "arena", timeoutTicks = 400, batch = "expedition_q")
    public static void compassRemovedOnFail(GameTestHelper helper) {
        ExpeditionManager manager = ExpeditionManager.get(helper.getLevel().getServer());
        ServerPlayer player = helper.makeMockServerPlayerInLevel();

        ExpeditionRun run = startExpedition(helper, manager, player);
        manager.enterPortal(player, ExpeditionManager.toPos(run.portal()));
        helper.assertTrue(compassOf(player).isPresent(),
                "entering the portal must grant an expedition compass");

        manager.failRun(player);
        helper.assertTrue(compassOf(player).isEmpty(),
                "failed run must remove the compass");
        helper.succeed();
    }

    /**
     * The terminal spawns a portal above itself; stepping into it teleports
     * the player to the run's remote entry and grants the compass.
     */
    @GameTest(template = "arena", timeoutTicks = 400, batch = "expedition_s")
    public static void portalTeleportsToEntry(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ExpeditionManager manager = ExpeditionManager.get(level.getServer());
        ServerPlayer player = helper.makeMockServerPlayerInLevel();

        ExpeditionRun run = startExpedition(helper, manager, player);
        BlockPos portalPos = ExpeditionManager.toPos(run.portal());
        helper.assertTrue(level.getBlockState(portalPos)
                        .is(ModBlocks.EXPEDITION_PORTAL.get()),
                "portal must spawn above the terminal");
        helper.assertTrue(compassOf(player).isEmpty(),
                "no compass before entering the portal");

        manager.enterPortal(player, portalPos);
        Waypoint entry = run.entry();
        helper.assertTrue(player.blockPosition().distSqr(
                        new BlockPos(entry.x(), entry.y(), entry.z())) <= 4,
                "portal must teleport the player to the run entry, was "
                        + player.blockPosition());
        helper.assertTrue(compassOf(player).isPresent(),
                "portal entry must grant the compass");

        manager.failRun(player); // leave no active run behind for later tests
        helper.succeed();
    }

    /**
     * A second player entering the portal joins the party: the generated
     * route extends, progress is shared, and the first checkpoint activation
     * seals the party by removing the portal.
     */
    @GameTest(template = "arena", timeoutTicks = 400, batch = "expedition_s")
    public static void partyJoinExtendsRouteAndSealsPortal(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ExpeditionManager manager = ExpeditionManager.get(level.getServer());
        ServerPlayer owner = helper.makeMockServerPlayerInLevel();
        ServerPlayer member = helper.makeMockServerPlayerInLevel();

        ExpeditionRun run = startExpedition(helper, manager, owner);
        int baseCheckpoints = run.totalCheckpoints();
        BlockPos portalPos = ExpeditionManager.toPos(run.portal());

        manager.enterPortal(member, portalPos);
        helper.assertTrue(run.isMember(member.getUUID()),
                "portal entry must add the player to the party");
        helper.assertTrue(run.totalCheckpoints() > baseCheckpoints,
                "joining member must extend the generated route");
        helper.assertTrue(manager.activeRun(member.getUUID()).isPresent(),
                "member must see the shared run as their active run");

        // The member advances the shared run; the portal seals.
        manager.activateCheckpoint(member, ExpeditionManager.toPos(run.checkpoints().get(0)));
        helper.assertTrue(run.completedCheckpoints() == 1,
                "member activation must advance the shared run");
        helper.assertTrue(!level.getBlockState(portalPos)
                        .is(ModBlocks.EXPEDITION_PORTAL.get()),
                "first checkpoint must remove the portal");
        helper.assertTrue(!run.portalOpen(), "party must be sealed");

        manager.failRun(owner); // owner leaves; member still has the run
        helper.assertTrue(run.isActive(), "run must continue while a member remains");
        manager.failRun(member); // last member leaving fails the run
        helper.assertTrue(!run.isActive(), "run must fail when the party empties");
        helper.succeed();
    }

    /**
     * Party completion: the scaled pool is split among members (default
     * config) and everyone is teleported back to the terminal.
     */
    @GameTest(template = "arena", timeoutTicks = 400, batch = "expedition_s")
    public static void partyCompletionSplitsRewardAndReturnsMembers(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ExpeditionManager manager = ExpeditionManager.get(level.getServer());
        ServerPlayer owner = helper.makeMockServerPlayerInLevel();
        ServerPlayer member = helper.makeMockServerPlayerInLevel();

        ExpeditionRun run = startExpedition(helper, manager, owner);
        manager.enterPortal(owner, ExpeditionManager.toPos(run.portal()));
        manager.enterPortal(member, ExpeditionManager.toPos(run.portal()));

        for (Waypoint w : run.checkpoints()) {
            manager.activateCheckpoint(owner, ExpeditionManager.toPos(w));
        }
        manager.completeRun(owner);

        // Reward pool scales with party size (base incl. time bonus, scaled up
        // per extra member) then splits evenly: both members get the same share.
        int share = run.tokensAwarded();
        helper.assertTrue(manager.tokenBalance(owner.getUUID()) == share,
                "owner must receive the member share");
        helper.assertTrue(manager.tokenBalance(member.getUUID()) == share,
                "member must receive the member share");
        helper.assertTrue(share > 0, "reward must be positive");

        Waypoint terminal = run.terminal();
        for (ServerPlayer p : new ServerPlayer[]{owner, member}) {
            helper.assertTrue(p.blockPosition().distSqr(
                            new BlockPos(terminal.x(), terminal.y() + 1, terminal.z())) <= 4,
                    "members must be teleported back to the terminal, was "
                            + p.blockPosition());
            helper.assertTrue(compassOf(p).isEmpty(), "member compass must be removed");
        }
        helper.succeed();
    }

    /**
     * A member dying leaves the party without failing the run; the survivors
     * continue. Their own run participation ends.
     */
    @GameTest(template = "arena", timeoutTicks = 400, batch = "expedition_s")
    public static void memberDeathDropsOutOfParty(GameTestHelper helper) {
        ExpeditionManager manager = ExpeditionManager.get(helper.getLevel().getServer());
        ServerPlayer owner = helper.makeMockServerPlayerInLevel();
        ServerPlayer member = helper.makeMockServerPlayerInLevel();
        member.setGameMode(GameType.SURVIVAL);

        ExpeditionRun run = startExpedition(helper, manager, owner);
        manager.enterPortal(member, ExpeditionManager.toPos(run.portal()));

        member.hurt(member.damageSources().genericKill(), Float.MAX_VALUE);

        helper.assertTrue(!run.isMember(member.getUUID()),
                "dead member must leave the party");
        helper.assertTrue(run.isActive(),
                "run must continue for surviving members");
        helper.assertTrue(manager.activeRun(member.getUUID()).isEmpty(),
                "dead member must have no active run");
        helper.assertTrue(manager.activeRun(owner.getUUID()).isPresent(),
                "owner keeps the shared run");

        manager.failRun(owner); // leave no active run behind for later tests
        helper.succeed();
    }

    /**
     * The built-in Overworld expedition generates its route by default: the
     * config definition carries a RouteGeneration spec, a terminal run gets
     * three generated checkpoints, and every resolved destination satisfies
     * the safety contract (sturdy, hazard-free floor; free headroom).
     */
    @GameTest(template = "arena", timeoutTicks = 400, batch = "expedition_r")
    public static void configOverworldGeneratesRoute(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ExpeditionManager manager = ExpeditionManager.get(level.getServer());
        ServerPlayer player = helper.makeMockServerPlayerInLevel();

        ExpeditionDefinition def = ExpeditionsConfig.definitionFor(Level.OVERWORLD)
                .orElseThrow(() -> new IllegalStateException("overworld expedition must exist"));
        helper.assertTrue(def.route() instanceof RouteGeneration,
                "config overworld expedition must generate routes, was " + def.route());

        ExpeditionRun run = startExpedition(helper, manager, player);
        helper.assertTrue(run.checkpoints().size() == 3,
                "default generated route must have 3 checkpoints");
        for (Waypoint w : run.checkpoints()) {
            assertSafeDestination(helper, level, ExpeditionManager.toPos(w));
        }
        assertSafeDestination(helper, level, ExpeditionManager.toPos(run.exit()));

        for (ExpeditionRun other : manager.runs()) {
            if (other.isActive() && !other.playerId().equals(player.getUUID())) {
                other.fail();
            }
        }
        manager.failRun(player);
        helper.succeed();
    }

    /**
     * An authored anchor resolving to a hazardous floor (magma) refuses the
     * run start entirely: no active run, no markers, no partial state.
     */
    @GameTest(template = "arena", timeoutTicks = 400, batch = "expedition_r")
    public static void hazardDestinationRejected(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ExpeditionManager manager = ExpeditionManager.get(level.getServer());
        ServerPlayer player = helper.makeMockServerPlayerInLevel();

        // Put a hazardous floor where the authored checkpoint anchor resolves:
        // force-load the column's chunk (like the adapter does), then scan down
        // to the top sturdy block and replace it with magma.
        level.getChunk(200 >> 4, 200 >> 4);
        BlockPos.MutableBlockPos surface = new BlockPos.MutableBlockPos(
                200, level.getMaxBuildHeight() - 2, 200);
        while (!level.getBlockState(surface).isFaceSturdy(level, surface, Direction.UP)
                && surface.getY() > level.getMinBuildHeight()) {
            surface.move(Direction.DOWN);
        }
        BlockState original = level.getBlockState(surface);
        level.setBlock(surface, Blocks.MAGMA_BLOCK.defaultBlockState(), 3);

        String json = """
                {
                  "dimension": "minecraft:overworld",
                  "checkpoints": [[%d, %d]],
                  "exit": [400, 400],
                  "tokens": 5
                }
                """.formatted(200, 200);
        try {
            ExpeditionDefinitions.reload(Map.of(
                    ResourceLocation.fromNamespaceAndPath("test", "hazardous"),
                    JsonParser.parseString(json)));
            helper.setBlock(new BlockPos(1, 1, 1),
                    ModBlocks.EXPEDITION_TERMINAL.get().defaultBlockState());
            helper.useBlock(new BlockPos(1, 1, 1), player);
            clickMenuStart(player);

            helper.assertTrue(manager.activeRun(player.getUUID()).isEmpty(),
                    "a run must not start when a destination is hazardous");
        } finally {
            ExpeditionDefinitions.reload(Map.of());
            level.setBlock(surface, original, 3);
        }
        helper.succeed();
    }

    /**
     * Checkpoint challenge — the horde: activating a checkpoint spawns the
     * configured mob count around it, every entity hunts a party member and
     * is tracked on the run. The next checkpoint discards the old horde and
     * rolls a fresh one; run end discards whatever remains.
     */
    @GameTest(template = "arena", timeoutTicks = 1200, batch = "expedition_t")
    public static void checkpointHordeSpawnsAndCleansUp(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        level.setDayTime(18000); // horde mobs must not burn mid-test
        ExpeditionManager manager = ExpeditionManager.get(level.getServer());
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        // The horde is force-targeted at a member and promotion polls can
        // take a while — creative so the zombies can't kill the player and
        // cascade-fail the run mid-test.
        player.setGameMode(GameType.CREATIVE);

        ExpeditionRun run = startExpedition(helper, manager, player);
        manager.enterPortal(player, ExpeditionManager.toPos(run.portal()));

        // Real click path: the player is adjacent, so the horde spawns into
        // chunks a real player would keep loaded.
        useBlockAt(helper, player, ExpeditionManager.toPos(run.checkpoints().get(0)));
        helper.assertTrue(run.challengeEntities().size()
                        == ExpeditionsConfig.challengeHordeSize(),
                "horde must spawn the configured size, got " + run.challengeEntities().size());
        BlockPos firstCheckpoint = ExpeditionManager.toPos(run.checkpoints().get(0));
        int hordeRadius = ExpeditionsConfig.challengeHordeSpawnRadius();

        // Mock players provide no chunk tickets, so freshly spawned mobs sit
        // in hidden entity sections until real tickets promote them — force
        // the chunks and wait for promotion so cleanup can reach them.
        List<UUID> firstHorde = List.copyOf(run.challengeEntities());
        forceLoadArea(level, firstCheckpoint, hordeRadius);
        pollUntil(helper,
                () -> firstHorde.stream().allMatch(id -> level.getEntity(id) != null),
                "the horde's entity sections to become accessible", () -> {
            for (UUID id : firstHorde) {
                Entity entity = findEntity(level, firstCheckpoint, hordeRadius, id);
                helper.assertTrue(entity instanceof Mob,
                        "horde member " + id + " must exist as a Mob, was " + entity);
                helper.assertTrue(((Mob) entity).getTarget() == player,
                        "horde member must hunt a party member, target was "
                                + ((Mob) entity).getTarget());
            }
            finishHordeTest(helper, manager, player, run, level, firstHorde, hordeRadius);
        }, 400, () -> dumpArea(level, firstCheckpoint, hordeRadius, firstHorde));
    }

    private static void finishHordeTest(GameTestHelper helper, ExpeditionManager manager,
            ServerPlayer player, ExpeditionRun run, ServerLevel level,
            List<UUID> firstHorde, int hordeRadius) {
        // The forced sections are accessible now, so the previous horde's
        // cleanup lands deterministically on the second activation.
        useBlockAt(helper, player, ExpeditionManager.toPos(run.checkpoints().get(1)));
        helper.assertTrue(!run.challengeEntities().containsAll(firstHorde),
                "the previous horde must be discarded at the next checkpoint");
        helper.assertTrue(!run.challengeEntities().isEmpty(),
                "a fresh horde must replace the previous one");

        for (ExpeditionRun other : manager.runs()) {
            if (other.isActive() && !other.playerId().equals(player.getUUID())) {
                other.fail();
            }
        }
        List<UUID> lastHorde = List.copyOf(run.challengeEntities());
        BlockPos lastCheckpoint = ExpeditionManager.toPos(
                run.checkpoints().get(run.completedCheckpoints() - 1));
        // Same pattern: promote the sections before run end so cleanup can
        // actually reach the entities.
        forceLoadArea(level, lastCheckpoint, hordeRadius);
        pollUntil(helper,
                () -> lastHorde.stream().allMatch(id -> level.getEntity(id) != null),
                "the last horde's sections to become accessible",
                () -> {
            manager.failRun(player);
            helper.assertTrue(run.challengeEntities().isEmpty(),
                    "run end must clear challenge tracking");
            for (UUID id : lastHorde) {
                helper.assertTrue(findEntity(level, lastCheckpoint, hordeRadius, id) == null,
                        "the remaining horde must be discarded when the run ends");
            }
            helper.succeed();
        }, 400, () -> dumpArea(level, lastCheckpoint, hordeRadius, lastHorde));
    }

    /**
     * Cross-dimensional interactions are rejected: a checkpoint block in the
     * Overworld at the Nether run's next-checkpoint coordinates must not
     * advance the run — the run's dimension is authoritative.
     */
    @GameTest(template = "arena", timeoutTicks = 1200, batch = "expedition_u")
    public static void crossDimensionActivationRejected(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel nether = server.getLevel(Level.NETHER);
        ServerLevel overworld = helper.getLevel();
        ExpeditionManager manager = ExpeditionManager.get(server);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();

        // Start a Nether run via a Nether terminal.
        BlockPos terminalPos = new BlockPos(0, 80, 0);
        clearAndFloor(nether, terminalPos);
        nether.setBlock(terminalPos, ModBlocks.EXPEDITION_TERMINAL.get().defaultBlockState(), 3);
        player.teleportTo(nether, 1.5, 80.0, 0.5, Set.of(), 0f, 0f);
        for (int attempt = 0;
                attempt < 5 && manager.activeRun(player.getUUID()).isEmpty(); attempt++) {
            useBlockAt(nether, player, terminalPos);
            clickMenuStart(player);
        }
        ExpeditionRun run = manager.activeRun(player.getUUID()).orElseThrow(
                () -> new IllegalStateException("terminal interaction must start a nether run"));

        // The Nether route is generated by default and lives in the Nether.
        helper.assertTrue(run.definition().route() instanceof RouteGeneration,
                "the Nether expedition must generate its route by default");
        for (Waypoint w : run.checkpoints()) {
            helper.assertTrue(nether.getBlockState(ExpeditionManager.toPos(w))
                            .is(ModBlocks.EXPEDITION_CHECKPOINT.get()),
                    "nether checkpoint marker must be placed at " + w);
        }

        // A same-coordinates checkpoint block in the Overworld cannot advance
        // the Nether run — the interaction dimension must match the run's.
        Waypoint next = run.checkpoints().get(0);
        BlockPos foreign = new BlockPos(next.x(), next.y(), next.z());
        overworld.setBlock(foreign, ModBlocks.EXPEDITION_CHECKPOINT.get().defaultBlockState(), 3);
        player.teleportTo(overworld, foreign.getX() + 0.5, foreign.getY() + 1.0,
                foreign.getZ() + 0.5, Set.of(), 0f, 0f);
        useBlockAt(overworld, player, foreign);
        helper.assertTrue(run.completedCheckpoints() == 0,
                "a checkpoint in the wrong dimension must not advance the run");

        // The genuine Nether checkpoint still works after the rejection.
        player.teleportTo(nether, next.x() + 0.5, next.y() + 1.0, next.z() + 0.5,
                Set.of(), 0f, 0f);
        useBlockAt(nether, player, ExpeditionManager.toPos(next));
        helper.assertTrue(run.completedCheckpoints() == 1,
                "the real nether checkpoint must still activate");

        manager.failRun(player);
        overworld.setBlock(foreign, Blocks.AIR.defaultBlockState(), 3);
        helper.succeed();
    }

    /**
     * A persisted run whose dimension no longer exists — e.g. the mod that
     * provided it was removed — must fail closed on resume: FAILED, no
     * reward, no crash, nothing partially cleaned up.
     */
    @GameTest(template = "arena", timeoutTicks = 400, batch = "expedition_v")
    public static void missingDimensionRunFailsClosed(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ExpeditionManager manager = ExpeditionManager.get(server);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();

        // Inject the run as though a save restored it after its dimension
        // vanished — the ledger trusts persisted records; resume validates.
        ExpeditionDefinition gone = new ExpeditionDefinition(
                "expeditions:gone_trial", "Gone Trial", 0, "expeditions:missing_dimension",
                new ExpeditionRoute(List.of(new RouteAnchor(0, 0)), new RouteAnchor(8, 8)),
                10, 0, 0);
        ExpeditionRun run = ExpeditionRun.start(UUID.randomUUID(), player.getUUID(), gone,
                List.of(new Waypoint(0, 60, 0)), new Waypoint(8, 60, 8));
        savedData(server).ledger().restoreRun(run);

        helper.assertTrue(manager.resumeOrFail(player).isEmpty(),
                "a run in a missing dimension must not resume");
        helper.assertTrue(run.status() == ExpeditionStatus.FAILED,
                "an unrecoverable run must be failed, was " + run.status());
        helper.assertTrue(manager.tokenBalance(player.getUUID()) == 0,
                "no reward may be paid for an unrecoverable run");
        helper.succeed();
    }

    /**
     * All three objective kinds progress and pay: a checkpoint visit
     * completes on activation, item pickups count toward collection (exact
     * item and tag match), and a generous time limit completes at
     * completion. The payout is the deterministic base plus each completed
     * objective's bonus — awarded exactly once.
     */
    @GameTest(template = "arena", timeoutTicks = 400, batch = "expedition_w")
    public static void objectivesProgressAndPayOut(GameTestHelper helper) {
        String json = """
                {
                  "display_name": "Bounty Trial",
                  "dimension": "minecraft:overworld",
                  "checkpoints": [[8, 8], [16, 8], [24, 8]],
                  "exit": [32, 8],
                  "tokens": 20,
                  "objectives": [
                    {"id": "midpoint", "description": "Reach checkpoint 2",
                     "type": "visit_checkpoint", "checkpoint": 2, "bonus_tokens": 3},
                    {"id": "bones", "description": "Collect 4 bones",
                     "type": "collect_items", "item": "minecraft:bone",
                     "count": 4, "bonus_tokens": 5},
                    {"id": "logs", "description": "Collect 2 logs",
                     "type": "collect_items", "tag": "minecraft:logs",
                     "count": 2, "bonus_tokens": 2},
                    {"id": "speedy", "description": "Finish within a minute",
                     "type": "time_limit", "seconds": 60, "bonus_tokens": 7}
                  ]
                }
                """;
        Map<ResourceLocation, JsonElement> entries = Map.of(
                ResourceLocation.fromNamespaceAndPath("test", "bounty_trial"),
                JsonParser.parseString(json));
        ExpeditionManager manager = ExpeditionManager.get(helper.getLevel().getServer());
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        try {
            ExpeditionDefinitions.reload(entries);
            ExpeditionRun run = startExpedition(helper, manager, player);
            helper.assertTrue(run.definition().objectives().size() == 4,
                    "all four objectives must be defined");
            helper.assertTrue(run.objectiveStates().size() == 4,
                    "every defined objective must be tracked from run start");

            manager.activateCheckpoint(player,
                    ExpeditionManager.toPos(run.checkpoints().get(0)));
            helper.assertTrue(!run.objectiveState("midpoint").orElseThrow().completed(),
                    "checkpoint 1 must not satisfy the checkpoint-2 objective");

            manager.activateCheckpoint(player,
                    ExpeditionManager.toPos(run.checkpoints().get(1)));
            helper.assertTrue(run.objectiveState("midpoint").orElseThrow().completed(),
                    "reaching checkpoint 2 must complete the visit objective");

            // Wrong item does not count; partial stacks accumulate.
            manager.onItemPickup(player, new ItemStack(Items.ROTTEN_FLESH, 9));
            helper.assertTrue(run.objectiveState("bones").orElseThrow().progress() == 0,
                    "non-matching items must not count");
            manager.onItemPickup(player, new ItemStack(Items.BONE, 3));
            helper.assertTrue(run.objectiveState("bones").orElseThrow().progress() == 3,
                    "pickups must accumulate, was "
                            + run.objectiveState("bones").orElseThrow().progress());
            manager.onItemPickup(player, new ItemStack(Items.BONE, 5));
            helper.assertTrue(run.objectiveState("bones").orElseThrow().completed(),
                    "overshooting the count must complete the objective");
            helper.assertTrue(run.objectiveState("bones").orElseThrow().progress() == 4,
                    "progress must clamp at the target");

            // A pickup on a completed objective must not double-complete
            // (no second reward is possible by construction).
            manager.onItemPickup(player, new ItemStack(Items.BONE, 8));
            helper.assertTrue(run.objectiveState("bones").orElseThrow().progress() == 4,
                    "completed objectives must latch");

            manager.onItemPickup(player, new ItemStack(Items.OAK_LOG, 2));
            helper.assertTrue(run.objectiveState("logs").orElseThrow().completed(),
                    "tag-matched items must count");

            manager.activateCheckpoint(player,
                    ExpeditionManager.toPos(run.checkpoints().get(2)));
            ExpeditionRun done = manager.completeRun(player);
            helper.assertTrue(done.objectiveState("speedy").orElseThrow().completed(),
                    "a fast finish must satisfy the time limit");
            helper.assertTrue(done.tokensAwarded() == 37,
                    "expected 20 base + 3 visit + 5 item + 2 tag + 7 timed, was "
                            + done.tokensAwarded());
            helper.assertTrue(manager.tokenBalance(player.getUUID()) == 37,
                    "balance must equal the paid reward");
        } finally {
            ExpeditionDefinitions.reload(Map.of());
        }
        helper.succeed();
    }

    /**
     * Optional objectives missed entirely do not block completion: the run
     * pays only the deterministic base reward.
     */
    @GameTest(template = "arena", timeoutTicks = 400, batch = "expedition_w")
    public static void optionalObjectivesDoNotBlockCompletion(GameTestHelper helper) {
        String json = """
                {
                  "display_name": "Casual Trial",
                  "dimension": "minecraft:overworld",
                  "checkpoints": [[8, 8], [16, 8]],
                  "exit": [24, 8],
                  "tokens": 12,
                  "objectives": [
                    {"id": "bones", "description": "Collect 4 bones",
                     "type": "collect_items", "item": "minecraft:bone",
                     "count": 4, "bonus_tokens": 5}
                  ]
                }
                """;
        Map<ResourceLocation, JsonElement> entries = Map.of(
                ResourceLocation.fromNamespaceAndPath("test", "casual_trial"),
                JsonParser.parseString(json));
        ExpeditionManager manager = ExpeditionManager.get(helper.getLevel().getServer());
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        try {
            ExpeditionDefinitions.reload(entries);
            ExpeditionRun run = startExpedition(helper, manager, player);
            for (Waypoint w : run.checkpoints()) {
                manager.activateCheckpoint(player, ExpeditionManager.toPos(w));
            }
            ExpeditionRun done = manager.completeRun(player);
            helper.assertTrue(done.status() == ExpeditionStatus.COMPLETED,
                    "optional objectives must never block completion");
            helper.assertTrue(done.tokensAwarded() == 12,
                    "only the base reward may be paid, was " + done.tokensAwarded());
            helper.assertTrue(done.completedObjectiveIds().isEmpty(),
                    "an untouched collect objective must not complete");
        } finally {
            ExpeditionDefinitions.reload(Map.of());
        }
        helper.succeed();
    }

    /**
     * A required objective gates the exit: clicking it with the objective
     * unfinished refuses completion and pays nothing; finishing the
     * objective afterwards lets the run complete and pays its bonus.
     */
    @GameTest(template = "arena", timeoutTicks = 400, batch = "expedition_w")
    public static void requiredObjectivesGateCompletion(GameTestHelper helper) {
        String json = """
                {
                  "display_name": "Tribute Trial",
                  "dimension": "minecraft:overworld",
                  "checkpoints": [[8, 8], [16, 8]],
                  "exit": [24, 8],
                  "tokens": 10,
                  "objectives": [
                    {"id": "tribute", "description": "Deliver 2 diamonds",
                     "type": "collect_items", "item": "minecraft:diamond",
                     "count": 2, "bonus_tokens": 6, "required": true}
                  ]
                }
                """;
        Map<ResourceLocation, JsonElement> entries = Map.of(
                ResourceLocation.fromNamespaceAndPath("test", "tribute_trial"),
                JsonParser.parseString(json));
        ExpeditionManager manager = ExpeditionManager.get(helper.getLevel().getServer());
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        try {
            ExpeditionDefinitions.reload(entries);
            ExpeditionRun run = startExpedition(helper, manager, player);
            for (Waypoint w : run.checkpoints()) {
                manager.activateCheckpoint(player, ExpeditionManager.toPos(w));
            }
            try {
                manager.completeRun(player);
                throw new IllegalStateException(
                        "exit must refuse while a required objective is unfinished");
            } catch (IllegalStateException expected) {
                helper.assertTrue(run.status() == ExpeditionStatus.ACTIVE,
                        "a refused run must stay active");
                helper.assertTrue(!run.isRewarded(), "a refused run must not be rewarded");
                helper.assertTrue(manager.tokenBalance(player.getUUID()) == 0,
                        "a refused run must pay nothing");
            }
            // Simulate the real click path for the refusal message too.
            useBlockAt(helper, player, ExpeditionManager.toPos(run.exit()));
            helper.assertTrue(run.status() == ExpeditionStatus.ACTIVE,
                    "the exit block must also refuse");

            manager.onItemPickup(player, new ItemStack(Items.DIAMOND, 2));
            ExpeditionRun done = manager.completeRun(player);
            helper.assertTrue(done.tokensAwarded() == 16,
                    "required objective bonus must pay on completion, was "
                            + done.tokensAwarded());
        } finally {
            ExpeditionDefinitions.reload(Map.of());
        }
        helper.succeed();
    }

    /**
     * Objective progress survives a save/load: collection progress and the
     * completed visit latch round-trip through NBT.
     */
    @GameTest(template = "arena", timeoutTicks = 400, batch = "expedition_w")
    public static void objectiveProgressPersists(GameTestHelper helper) {
        String json = """
                {
                  "display_name": "Durable Trial",
                  "dimension": "minecraft:overworld",
                  "checkpoints": [[8, 8], [16, 8]],
                  "exit": [24, 8],
                  "tokens": 10,
                  "objectives": [
                    {"id": "first", "description": "Reach checkpoint 1",
                     "type": "visit_checkpoint", "checkpoint": 1, "bonus_tokens": 3},
                    {"id": "bones", "description": "Collect 4 bones",
                     "type": "collect_items", "item": "minecraft:bone",
                     "count": 4, "bonus_tokens": 5}
                  ]
                }
                """;
        Map<ResourceLocation, JsonElement> entries = Map.of(
                ResourceLocation.fromNamespaceAndPath("test", "durable_trial"),
                JsonParser.parseString(json));
        MinecraftServer server = helper.getLevel().getServer();
        ExpeditionManager manager = ExpeditionManager.get(server);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        try {
            ExpeditionDefinitions.reload(entries);
            ExpeditionRun run = startExpedition(helper, manager, player);
            manager.activateCheckpoint(player,
                    ExpeditionManager.toPos(run.checkpoints().get(0)));
            manager.onItemPickup(player, new ItemStack(Items.BONE, 3));

            ExpeditionRun restored = roundTrip(helper, server).ledger()
                    .activeRun(player.getUUID()).orElseThrow(
                            () -> new IllegalStateException("run must survive a save/load"));
            helper.assertTrue(restored.definition().objectives().size() == 2,
                    "objective definitions must round-trip");
            helper.assertTrue(new ObjectiveState(1, true)
                            .equals(restored.objectiveState("first").orElseThrow()),
                    "completed visit must persist, was "
                            + restored.objectiveState("first").orElseThrow());
            helper.assertTrue(new ObjectiveState(3, false)
                            .equals(restored.objectiveState("bones").orElseThrow()),
                    "collection progress must persist, was "
                            + restored.objectiveState("bones").orElseThrow());

            manager.failRun(player); // leave no active run behind for later tests
        } finally {
            ExpeditionDefinitions.reload(Map.of());
        }
        helper.succeed();
    }

    /**
     * Broken objective definitions are rejected at load like any malformed
     * datapack entry — skipped and logged, never silently accepted.
     */
    @GameTest(template = "arena", timeoutTicks = 400, batch = "expedition_w")
    public static void invalidObjectivesAreRejected(GameTestHelper helper) {
        Map<ResourceLocation, JsonElement> entries = Map.of(
                ResourceLocation.fromNamespaceAndPath("test", "bad_type"),
                JsonParser.parseString("""
                        {"dimension": "minecraft:overworld",
                         "checkpoints": [[1, 1]], "exit": [2, 2],
                         "objectives": [{"id": "x", "type": "slay_dragons"}]}"""),
                ResourceLocation.fromNamespaceAndPath("test", "unknown_item"),
                JsonParser.parseString("""
                        {"dimension": "minecraft:overworld",
                         "checkpoints": [[1, 1]], "exit": [2, 2],
                         "objectives": [{"id": "x", "type": "collect_items",
                          "item": "test:not_a_real_item", "count": 1}]}"""),
                ResourceLocation.fromNamespaceAndPath("test", "bad_index"),
                JsonParser.parseString("""
                        {"dimension": "minecraft:overworld",
                         "checkpoints": [[1, 1]], "exit": [2, 2],
                         "objectives": [{"id": "x", "type": "visit_checkpoint",
                          "checkpoint": 9}]}"""),
                ResourceLocation.fromNamespaceAndPath("test", "valid"),
                JsonParser.parseString("""
                        {"dimension": "minecraft:overworld",
                         "checkpoints": [[1, 1]], "exit": [2, 2],
                         "objectives": [{"id": "x", "type": "visit_checkpoint",
                          "checkpoint": 1}]}"""));
        try {
            ExpeditionDefinitions.reload(entries);
            helper.assertTrue(ExpeditionDefinitions.all().size() == 1,
                    "only the valid definition must load, got "
                            + ExpeditionDefinitions.all().size());
            helper.assertTrue(ExpeditionDefinitions.all().containsKey("test:valid"),
                    "the valid definition must be present");
        } finally {
            ExpeditionDefinitions.reload(Map.of());
        }
        helper.succeed();
    }

    /**
     * Completion grants bonus item drops drawn from the run dimension's
     * reward tag pool on top of the unchanged token award: every granted
     * stack must come from `expeditions:reward_items/overworld`, and the
     * draw must produce at least one stack.
     */
    @GameTest(template = "arena", timeoutTicks = 400, batch = "expedition_x")
    public static void bonusDropsComeFromDimensionPool(GameTestHelper helper) {
        ExpeditionManager manager = ExpeditionManager.get(helper.getLevel().getServer());
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        ExpeditionRun run = startExpedition(helper, manager, player);
        for (Waypoint w : run.checkpoints()) {
            manager.activateCheckpoint(player, ExpeditionManager.toPos(w));
        }
        ExpeditionRun done = manager.completeRun(player);
        List<Item> pool = ItemRewards.pool(run.definition().dimension());
        helper.assertTrue(!pool.isEmpty(), "overworld reward pool must be configured");
        int drops = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.isEmpty() || stack.is(ModItems.EXPEDITION_COMPASS.get())) {
                continue;
            }
            helper.assertTrue(pool.contains(stack.getItem()),
                    "drop " + stack.getItem() + " must come from the overworld reward pool");
            drops++;
        }
        helper.assertTrue(drops > 0,
                "completion must grant at least one bonus drop, found " + drops);
        helper.assertTrue(manager.tokenBalance(player.getUUID()) == done.tokensAwarded(),
                "token reward must still be paid in full alongside drops");
        helper.succeed();
    }

    /**
     * Reward pools are dimension-sensitive: a Nether completion draws only
     * from `reward_items/the_nether`, never the Overworld or default pools.
     */
    @GameTest(template = "arena", timeoutTicks = 1200, batch = "expedition_x")
    public static void netherBonusDropsUseNetherPool(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel nether = server.getLevel(Level.NETHER);
        ExpeditionManager manager = ExpeditionManager.get(server);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.CREATIVE); // hordes must not kill the runner mid-test

        BlockPos terminalPos = new BlockPos(0, 80, 0);
        clearAndFloor(nether, terminalPos);
        nether.setBlock(terminalPos, ModBlocks.EXPEDITION_TERMINAL.get().defaultBlockState(), 3);
        player.teleportTo(nether, 1.5, 80.0, 0.5, Set.of(), 0f, 0f);
        for (int attempt = 0;
                attempt < 5 && manager.activeRun(player.getUUID()).isEmpty(); attempt++) {
            useBlockAt(nether, player, terminalPos);
            clickMenuStart(player);
        }
        ExpeditionRun run = manager.activeRun(player.getUUID()).orElseThrow(
                () -> new IllegalStateException("terminal interaction must start a nether run"));

        for (Waypoint w : run.checkpoints()) {
            manager.activateCheckpoint(player, ExpeditionManager.toPos(w));
        }
        ExpeditionRun done = manager.completeRun(player);
        List<Item> pool = ItemRewards.pool("minecraft:the_nether");
        helper.assertTrue(!pool.isEmpty(), "nether reward pool must be configured");
        int drops = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.isEmpty() || stack.is(ModItems.EXPEDITION_COMPASS.get())) {
                continue;
            }
            helper.assertTrue(pool.contains(stack.getItem()),
                    "drop " + stack.getItem() + " must come from the nether reward pool");
            drops++;
        }
        helper.assertTrue(drops > 0, "nether completion must grant bonus drops");
        helper.assertTrue(done.isRewarded(), "token reward must still be recorded");
        helper.succeed();
    }

    /**
     * `/expeditions redeem` is the token sink: it deducts the configured cost
     * exactly once and grants items from the player's current-dimension pool;
     * with an insufficient balance it refuses without touching the balance.
     */
    @GameTest(template = "arena", timeoutTicks = 400, batch = "expedition_x")
    public static void redeemConvertsTokensToSupplies(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ExpeditionManager manager = ExpeditionManager.get(server);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        int cost = ExpeditionsConfig.redeemTokenCost();

        helper.assertTrue(!manager.redeemTokens(player),
                "redeem with a zero balance must refuse");
        helper.assertTrue(manager.tokenBalance(player.getUUID()) == 0,
                "a refused redeem must not touch the balance");

        savedData(server).ledger().addTokens(player.getUUID(), cost * 2);
        helper.assertTrue(manager.redeemTokens(player), "first redeem must succeed");
        helper.assertTrue(manager.tokenBalance(player.getUUID()) == cost,
                "balance must drop by the redeem cost, was "
                        + manager.tokenBalance(player.getUUID()));

        List<Item> pool = ItemRewards.pool("minecraft:overworld");
        int drops = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            helper.assertTrue(pool.contains(stack.getItem()),
                    "redeemed item " + stack.getItem() + " must come from the dimension pool");
            drops++;
        }
        helper.assertTrue(drops > 0, "redeem must grant at least one item");

        helper.assertTrue(manager.redeemTokens(player), "second redeem must also succeed");
        helper.assertTrue(manager.tokenBalance(player.getUUID()) == 0,
                "balance must reach exactly zero, was " + manager.tokenBalance(player.getUUID()));
        helper.assertTrue(!manager.redeemTokens(player),
                "a third redeem must refuse on the drained balance");
        helper.succeed();
    }

    /**
     * ItemValuator ranks by real item stats: equipment outranks raw
     * materials, which outrank food and junk — for vanilla and any modded
     * items that carry the same components.
     */
    @GameTest(template = "arena", timeoutTicks = 400, batch = "expedition_x")
    public static void itemValuationRanksTiers(GameTestHelper helper) {
        int stick = ItemValuator.score(new ItemStack(Items.STICK));
        int bread = ItemValuator.score(new ItemStack(Items.BREAD));
        int diamond = ItemValuator.score(new ItemStack(Items.DIAMOND));
        int chestplate = ItemValuator.score(new ItemStack(Items.DIAMOND_CHESTPLATE));
        int netherite = ItemValuator.score(new ItemStack(Items.NETHERITE_SWORD));
        helper.assertTrue(bread > stick,
                "food must outrank junk: stick=" + stick + " bread=" + bread);
        helper.assertTrue(diamond > bread,
                "a gem must outrank food: bread=" + bread + " diamond=" + diamond);
        helper.assertTrue(chestplate > diamond,
                "equipment must outrank the raw gem: diamond=" + diamond
                        + " chestplate=" + chestplate);
        helper.assertTrue(netherite > chestplate,
                "higher-tier equipment must outrank lower: chestplate=" + chestplate
                        + " netherite=" + netherite);
        helper.succeed();
    }

    /**
     * Explicit expedition selection can cross dimensions: picking the Nether
     * trial from an Overworld terminal resolves the route in the Nether
     * while the portal stays at home — and run end removes it via the
     * portal's own dimension, not the run's.
     */
    @GameTest(template = "arena", timeoutTicks = 1200, batch = "expedition_y")
    public static void remoteExpeditionRunsInNether(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel nether = server.getLevel(Level.NETHER);
        ExpeditionManager manager = ExpeditionManager.get(server);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.CREATIVE);

        // Nether routes resolve over real terrain — retry like a player
        // re-rolling when the walk lands somewhere unresolvable.
        ExpeditionRun run = null;
        for (int attempt = 0; attempt < 4 && run == null; attempt++) {
            try {
                run = manager.startRun(player, helper.absolutePos(new BlockPos(1, 1, 1)),
                        new ExpeditionOptions("expeditions:nether_trial",
                                Difficulty.STANDARD, 0, 0));
            } catch (IllegalStateException unresolvable) {
                // Try the next roll.
            }
        }
        helper.assertTrue(run != null, "a nether route must resolve within retries");
        helper.assertTrue("minecraft:the_nether".equals(run.definition().dimension()),
                "run dimension must be the nether, was " + run.definition().dimension());
        helper.assertTrue("minecraft:overworld".equals(run.portalDimension()),
                "portal must stay in the overworld, was " + run.portalDimension());

        for (Waypoint w : run.checkpoints()) {
            helper.assertTrue(nether.getBlockState(ExpeditionManager.toPos(w))
                            .is(ModBlocks.EXPEDITION_CHECKPOINT.get()),
                    "nether marker must be placed in the nether level");
        }
        helper.assertTrue(helper.getLevel().getBlockState(ExpeditionManager.toPos(run.portal()))
                        .is(ModBlocks.EXPEDITION_PORTAL.get()),
                "join portal must be placed in the overworld");

        // End any foreign active runs so marker removal is verifiable
        // deterministically — a leaked run in the nether legitimately keeps
        // shared markers alive.
        for (ExpeditionRun other : manager.runs()) {
            if (other.isActive() && !other.playerId().equals(player.getUUID())) {
                other.fail();
            }
        }
        manager.failRun(player);
        helper.assertTrue(!helper.getLevel().getBlockState(ExpeditionManager.toPos(run.portal()))
                        .is(ModBlocks.EXPEDITION_PORTAL.get()),
                "run end must remove the portal from its home dimension");
        for (Waypoint w : run.checkpoints()) {
            helper.assertTrue(!nether.getBlockState(ExpeditionManager.toPos(w))
                            .is(ModBlocks.EXPEDITION_CHECKPOINT.get()),
                    "run end must remove nether markers");
        }
        helper.succeed();
    }

    /**
     * Per-run options survive save/load so a restarted server resumes the
     * run with the same modifiers — the menu choices are part of the run.
     */
    @GameTest(template = "arena", timeoutTicks = 400, batch = "expedition_y")
    public static void optionsPersistAcrossReload(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ExpeditionManager manager = ExpeditionManager.get(server);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        ExpeditionRun run = manager.startRun(player, helper.absolutePos(new BlockPos(1, 1, 1)),
                new ExpeditionOptions("", Difficulty.HARD, 2, 150));

        ExpeditionSavedData restored = roundTrip(helper, server);
        ExpeditionRun reloaded = restored.ledger().run(player.getUUID()).orElseThrow(
                () -> new IllegalStateException("run must survive the round trip"));
        helper.assertTrue(reloaded.options().difficulty() == Difficulty.HARD,
                "difficulty must persist, was " + reloaded.options().difficulty());
        helper.assertTrue(reloaded.options().checkpoints() == 2,
                "checkpoint override must persist, was " + reloaded.options().checkpoints());
        helper.assertTrue(reloaded.options().bonusTimePercent() == 150,
                "time percent must persist, was " + reloaded.options().bonusTimePercent());
        helper.assertTrue(reloaded.portalDimension().equals(run.portalDimension()),
                "portal dimension must persist");
        manager.failRun(player);
        helper.succeed();
    }

    /**
     * The checkpoints option resizes generated routes while authored routes
     * stay untouched (the option only applies to generation).
     */
    @GameTest(template = "arena", timeoutTicks = 400, batch = "expedition_y")
    public static void checkpointsOptionResizesRoute(GameTestHelper helper) {
        ExpeditionManager manager = ExpeditionManager.get(helper.getLevel().getServer());
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        ExpeditionRun run = manager.startRun(player, helper.absolutePos(new BlockPos(1, 1, 1)),
                new ExpeditionOptions("", Difficulty.STANDARD, 1, 0));
        helper.assertTrue(run.checkpoints().size() == 1,
                "checkpoints=1 must produce a one-checkpoint route, got "
                        + run.checkpoints().size());
        manager.failRun(player);
        helper.succeed();
    }

    /**
     * Difficulty scales the payout: BRUTAL doubles the scaled reward pool
     * (8 base + 4 time bonus = 12, x200% = 24) with a solo party.
     */
    @GameTest(template = "arena", timeoutTicks = 400, batch = "expedition_y")
    public static void brutalDifficultyDoublesReward(GameTestHelper helper) {
        ExpeditionManager manager = ExpeditionManager.get(helper.getLevel().getServer());
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.CREATIVE);
        ExpeditionRun run = manager.startRun(player, helper.absolutePos(new BlockPos(1, 1, 1)),
                new ExpeditionOptions("", Difficulty.BRUTAL, 0, 0));
        for (Waypoint w : run.checkpoints()) {
            manager.activateCheckpoint(player, ExpeditionManager.toPos(w));
        }
        ExpeditionRun done = manager.completeRun(player);
        helper.assertTrue(done.tokensAwarded() == 24,
                "brutal must double the 12-token pool, awarded " + done.tokensAwarded());
        helper.succeed();
    }

    /**
     * The terminal click opens the options menu, and its start button
     * launches a run with the chosen options.
     */
    @GameTest(template = "arena", timeoutTicks = 400, batch = "expedition_y")
    public static void menuStartLaunchesRun(GameTestHelper helper) {
        ExpeditionManager manager = ExpeditionManager.get(helper.getLevel().getServer());
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.CREATIVE);
        BlockPos terminalRel = new BlockPos(1, 1, 1);
        helper.setBlock(terminalRel, ModBlocks.EXPEDITION_TERMINAL.get().defaultBlockState());
        useBlockAt(helper, player, helper.absolutePos(terminalRel));
        helper.assertTrue(player.containerMenu instanceof ExpeditionMenu,
                "click must open the expedition menu, got " + player.containerMenu);
        // Slot 31 is the menu's start button.
        player.containerMenu.clicked(31, 0, ClickType.PICKUP, player);
        ExpeditionRun run = manager.activeRun(player.getUUID()).orElseThrow(
                () -> new IllegalStateException("the start button must launch a run"));
        helper.assertTrue(run.options().difficulty() == Difficulty.STANDARD,
                "default menu selection must be standard difficulty");
        manager.failRun(player);
        helper.succeed();
    }

    /**
     * `/expeditions start` accepts the same modifiers without a terminal:
     * the command anchors its portal at the player's feet.
     */
    @GameTest(template = "arena", timeoutTicks = 400, batch = "expedition_y")
    public static void startCommandAppliesModifiers(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ExpeditionManager manager = ExpeditionManager.get(server);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.CREATIVE);
        int result;
        try {
            result = server.getCommands().getDispatcher().execute(
                    "expeditions start expeditions:overworld_trial hard 2 100",
                    player.createCommandSourceStack());
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            throw new IllegalStateException("start command must parse", e);
        }
        helper.assertTrue(result > 0, "start command must succeed, returned " + result);
        ExpeditionRun run = manager.activeRun(player.getUUID()).orElseThrow(
                () -> new IllegalStateException("the start command must launch a run"));
        helper.assertTrue("expeditions:overworld_trial".equals(run.options().expeditionId()),
                "command must record the picked expedition, was "
                        + run.options().expeditionId());
        helper.assertTrue(run.options().difficulty() == Difficulty.HARD,
                "command must apply the difficulty, was " + run.options().difficulty());
        helper.assertTrue(run.checkpoints().size() == 2,
                "command must apply the checkpoint override, got "
                        + run.checkpoints().size());
        manager.failRun(player);
        helper.succeed();
    }

    /**
     * Datapack {@code "modifiers"} parse: weather plus member/horde effect
     * lists with amplifiers land on the definition.
     */
    @GameTest(template = "arena", timeoutTicks = 400, batch = "expedition_z")
    public static void modifiersParseFromDatapack(GameTestHelper helper) {
        String json = """
                {
                  "dimension": "minecraft:overworld",
                  "checkpoints": [[10, 10]],
                  "exit": [30, 30],
                  "tokens": 10,
                  "modifiers": {
                    "weather": "thunder",
                    "player_effects": [{"id": "minecraft:speed", "amplifier": 1}],
                    "horde_effects": [{"id": "minecraft:strength"}]
                  }
                }
                """;
        try {
            ExpeditionDefinitions.reload(Map.of(
                    ResourceLocation.fromNamespaceAndPath("test", "modified"),
                    JsonParser.parseString(json)));
            ExpeditionDefinition def = ExpeditionDefinitions.all().get("test:modified");
            helper.assertTrue(def != null, "the modified definition must load");
            helper.assertTrue(def.modifiers().weather() == WeatherModifier.THUNDER,
                    "weather must parse, was " + def.modifiers().weather());
            helper.assertTrue(def.modifiers().playerEffects().equals(
                            List.of(new EffectSpec("minecraft:speed", 1))),
                    "player effects must parse, was " + def.modifiers().playerEffects());
            helper.assertTrue(def.modifiers().hordeEffects().equals(
                            List.of(new EffectSpec("minecraft:strength", 0))),
                    "horde effects must parse, was " + def.modifiers().hordeEffects());
        } finally {
            ExpeditionDefinitions.reload(Map.of());
        }
        helper.succeed();
    }

    /**
     * Bad modifier values fail the definition loudly: unknown weather names
     * and unregistered effect ids get the whole file skipped on reload.
     */
    @GameTest(template = "arena", timeoutTicks = 400, batch = "expedition_z")
    public static void malformedModifiersSkipped(GameTestHelper helper) {
        String base = """
                {
                  "dimension": "minecraft:overworld",
                  "checkpoints": [[1, 1]],
                  "exit": [2, 2],
                  "modifiers": %s
                }
                """;
        try {
            ExpeditionDefinitions.reload(Map.of(
                    ResourceLocation.fromNamespaceAndPath("test", "bad_weather"),
                    JsonParser.parseString(base.formatted("{\"weather\": \"apocalypse\"}")),
                    ResourceLocation.fromNamespaceAndPath("test", "bad_effect"),
                    JsonParser.parseString(base.formatted(
                            "{\"player_effects\": [{\"id\": \"minecraft:not_a_real_effect\"}]}")),
                    ResourceLocation.fromNamespaceAndPath("test", "valid"),
                    JsonParser.parseString("""
                            {
                              "dimension": "minecraft:overworld",
                              "checkpoints": [[1, 1]],
                              "exit": [2, 2]
                            }
                            """)));
            helper.assertTrue(ExpeditionDefinitions.all().size() == 1,
                    "bad modifier definitions must be skipped, loaded "
                            + ExpeditionDefinitions.all().keySet());
        } finally {
            ExpeditionDefinitions.reload(Map.of());
        }
        helper.succeed();
    }

    /**
     * Member effects: the expedition's {@code player_effects} land on the
     * party when the run starts and are stripped when the run ends.
     */
    @GameTest(template = "arena", timeoutTicks = 400, batch = "expedition_z")
    public static void playerEffectsApplyAndRemove(GameTestHelper helper) {
        ExpeditionManager manager = ExpeditionManager.get(helper.getLevel().getServer());
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        String json = """
                {
                  "dimension": "minecraft:overworld",
                  "generate": {"checkpoints": 1, "min_spacing": 16, "max_spacing": 24,
                               "exit_min": 24, "exit_max": 32,
                               "entry_min": 32, "entry_max": 48},
                  "tokens": 1,
                  "modifiers": {"player_effects": [{"id": "minecraft:speed", "amplifier": 1}]}
                }
                """;
        try {
            ExpeditionDefinitions.reload(Map.of(
                    ResourceLocation.fromNamespaceAndPath("test", "speedy"),
                    JsonParser.parseString(json)));
            ExpeditionRun run = startExpedition(helper, manager, player);
            try {
                helper.assertTrue(run.definition().id().equals("test:speedy"),
                        "the modified definition must start, was " + run.definition().id());
                helper.assertTrue(player.hasEffect(MobEffects.MOVEMENT_SPEED),
                        "member must gain the expedition's effect at start");
            } finally {
                manager.failRun(player);
            }
            helper.assertTrue(!player.hasEffect(MobEffects.MOVEMENT_SPEED),
                    "member must lose the expedition's effect at run end");
        } finally {
            ExpeditionDefinitions.reload(Map.of());
        }
        helper.succeed();
    }

    /**
     * Weather modifier: starting the run forces the declared weather on the
     * expedition's dimension and ending the run clears it.
     */
    @GameTest(template = "arena", timeoutTicks = 400, batch = "expedition_z")
    public static void weatherModifierAppliesAndClears(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ExpeditionManager manager = ExpeditionManager.get(level.getServer());
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        String json = """
                {
                  "dimension": "minecraft:overworld",
                  "generate": {"checkpoints": 1, "min_spacing": 16, "max_spacing": 24,
                               "exit_min": 24, "exit_max": 32,
                               "entry_min": 32, "entry_max": 48},
                  "tokens": 1,
                  "modifiers": {"weather": "rain"}
                }
                """;
        level.setWeatherParameters(6000, 0, false, false);
        try {
            ExpeditionDefinitions.reload(Map.of(
                    ResourceLocation.fromNamespaceAndPath("test", "stormy"),
                    JsonParser.parseString(json)));
            ExpeditionRun run = startExpedition(helper, manager, player);
            try {
                helper.assertTrue(run.definition().id().equals("test:stormy"),
                        "the weather definition must start, was " + run.definition().id());
                // isRaining() lerps per tick for visuals; the level data flag
                // is the authoritative state the modifier sets.
                helper.assertTrue(level.getLevelData().isRaining(),
                        "the run must force its declared weather onto the dimension");
            } finally {
                manager.failRun(player);
            }
            helper.assertTrue(!level.getLevelData().isRaining(),
                    "run end must clear the forced weather");
        } finally {
            ExpeditionDefinitions.reload(Map.of());
            level.setWeatherParameters(6000, 0, false, false);
        }
        helper.succeed();
    }

    /**
     * The horde rolls its type from the dimension's {@code horde_mobs} pool
     * (not the single config mob) and every member carries the expedition's
     * declared buffs.
     */
    @GameTest(template = "arena", timeoutTicks = 1200, batch = "expedition_z")
    public static void hordeSpawnsPooledAndBuffed(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        level.setDayTime(18000); // pool mobs must not burn mid-test
        ExpeditionManager manager = ExpeditionManager.get(level.getServer());
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.CREATIVE);
        String json = """
                {
                  "dimension": "minecraft:overworld",
                  "generate": {"checkpoints": 1, "min_spacing": 16, "max_spacing": 24,
                               "exit_min": 24, "exit_max": 32,
                               "entry_min": 32, "entry_max": 48},
                  "tokens": 1,
                  "modifiers": {"horde_effects": [{"id": "minecraft:resistance", "amplifier": 2}]}
                }
                """;
        try {
            ExpeditionDefinitions.reload(Map.of(
                    ResourceLocation.fromNamespaceAndPath("test", "buffed"),
                    JsonParser.parseString(json)));
            ExpeditionRun run = startExpedition(helper, manager, player);
            manager.enterPortal(player, ExpeditionManager.toPos(run.portal()));
            useBlockAt(helper, player, ExpeditionManager.toPos(run.checkpoints().get(0)));
            helper.assertTrue(!run.challengeEntities().isEmpty(),
                    "the checkpoint must roll a horde");
            List<UUID> horde = List.copyOf(run.challengeEntities());
            BlockPos checkpoint = ExpeditionManager.toPos(run.checkpoints().get(0));
            int radius = ExpeditionsConfig.challengeHordeSpawnRadius();
            forceLoadArea(level, checkpoint, radius);
            pollUntil(helper,
                    () -> horde.stream().allMatch(id -> level.getEntity(id) != null),
                    "the horde's entity sections to become accessible", () -> {
                for (UUID id : horde) {
                    Entity entity = findEntity(level, checkpoint, radius, id);
                    helper.assertTrue(entity instanceof Mob,
                            "horde member must be a Mob, was " + entity);
                    helper.assertTrue(
                            HordePools.pool("minecraft:overworld").contains(entity.getType()),
                            "horde member must come from the overworld pool, was "
                                    + entity.getType());
                    helper.assertTrue(((Mob) entity).hasEffect(MobEffects.DAMAGE_RESISTANCE),
                            "horde member must carry the expedition's buff");
                }
                manager.failRun(player);
                helper.succeed();
            }, 400, () -> dumpArea(level, checkpoint, radius, horde));
        } finally {
            ExpeditionDefinitions.reload(Map.of());
        }
    }

    /**
     * Modifiers ride the run's persisted definition: a save/load round trip
     * restores weather and both effect lists.
     */
    @GameTest(template = "arena", timeoutTicks = 400, batch = "expedition_z")
    public static void modifiersPersistAcrossReload(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ExpeditionManager manager = ExpeditionManager.get(server);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        String json = """
                {
                  "dimension": "minecraft:overworld",
                  "generate": {"checkpoints": 1, "min_spacing": 16, "max_spacing": 24,
                               "exit_min": 24, "exit_max": 32,
                               "entry_min": 32, "entry_max": 48},
                  "tokens": 1,
                  "modifiers": {
                    "weather": "clear",
                    "player_effects": [{"id": "minecraft:luck"}],
                    "horde_effects": [{"id": "minecraft:speed", "amplifier": 2}]
                  }
                }
                """;
        try {
            ExpeditionDefinitions.reload(Map.of(
                    ResourceLocation.fromNamespaceAndPath("test", "modified"),
                    JsonParser.parseString(json)));
            ExpeditionRun run = startExpedition(helper, manager, player);
            ExpeditionSavedData restored = roundTrip(helper, server);
            ExpeditionRun reloaded = restored.ledger().run(player.getUUID()).orElseThrow(
                    () -> new IllegalStateException("run must survive the round trip"));
            helper.assertTrue(reloaded.definition().modifiers()
                            .equals(run.definition().modifiers()),
                    "modifiers must round-trip, was " + reloaded.definition().modifiers());
            manager.failRun(player);
        } finally {
            ExpeditionDefinitions.reload(Map.of());
        }
        helper.succeed();
    }

    private static ExpeditionSavedData savedData(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                ExpeditionSavedData.FACTORY, ExpeditionSavedData.DATA_NAME);
    }

    /** Serializes the live saved data and loads it into a fresh instance. */
    private static ExpeditionSavedData roundTrip(GameTestHelper helper, MinecraftServer server) {
        CompoundTag tag = savedData(server)
                .save(new CompoundTag(), helper.getLevel().registryAccess());
        return ExpeditionSavedData.load(tag, helper.getLevel().registryAccess());
    }

    /** Makes {@code terminalPos} clickable: sturdy floor below, air above and beside. */
    private static void clearAndFloor(ServerLevel level, BlockPos terminalPos) {
        for (int dx = 0; dx <= 1; dx++) {
            BlockPos base = terminalPos.east(dx);
            level.setBlock(base.below(), Blocks.NETHERRACK.defaultBlockState(), 3);
            level.setBlock(base, Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(base.above(), Blocks.AIR.defaultBlockState(), 3);
        }
    }

    private static ExpeditionRun startExpedition(GameTestHelper helper, ExpeditionManager manager,
            ServerPlayer player) {
        // Interact with a real terminal block via the same useWithoutItem path a
        // player click would take — the terminal opens its options menu and the
        // run starts from the menu's start button (slot 31).
        BlockPos terminalRel = new BlockPos(1, 1, 1);
        helper.setBlock(terminalRel, ModBlocks.EXPEDITION_TERMINAL.get().defaultBlockState());
        helper.useBlock(terminalRel, player);
        clickMenuStart(player);
        ExpeditionRun run = manager.activeRun(player.getUUID()).orElseThrow(
                () -> new IllegalStateException("terminal interaction must start a run"));
        return run;
    }

    /** Clicks the expedition menu's start button if a menu is open. */
    private static void clickMenuStart(ServerPlayer player) {
        if (player.containerMenu instanceof ExpeditionMenu) {
            player.containerMenu.clicked(31, 0, ClickType.PICKUP, player);
        }
    }

    /**
     * Simulates a real right-click: moves the player next to the block and runs
     * the server interaction path ({@code ServerPlayerGameMode.useItemOn}) that
     * handles genuine player input, including reach checks.
     */
    private static void useBlockAt(GameTestHelper helper, ServerPlayer player, BlockPos absolute) {
        useBlockAt(helper.getLevel(), player, absolute);
    }

    private static void useBlockAt(ServerLevel level, ServerPlayer player, BlockPos absolute) {
        player.setPos(absolute.getX() + 0.5, absolute.getY() + 1.0, absolute.getZ() + 0.5);
        BlockHitResult hit = new BlockHitResult(
                Vec3.atCenterOf(absolute), Direction.UP, absolute, false);
        player.gameMode.useItemOn(player, level,
                player.getItemInHand(InteractionHand.MAIN_HAND), InteractionHand.MAIN_HAND, hit);
    }

    /** Persistent ticket on every chunk covering the area — unlike a
     * transient {@code getChunk} load, this eventually makes the chunk's
     * entity sections accessible (same as a real player standing there). */
    private static void forceLoadArea(ServerLevel level, BlockPos center, int radius) {
        for (int cx = (center.getX() - radius) >> 4;
                cx <= (center.getX() + radius) >> 4; cx++) {
            for (int cz = (center.getZ() - radius) >> 4;
                    cz <= (center.getZ() + radius) >> 4; cz++) {
                level.setChunkForced(cx, cz, true);
            }
        }
    }

    /** Retries {@code condition} once per tick (bounded), then runs
     * {@code then}. Entity-section promotion is asynchronous — tests must
     * wait for the state, not guess the tick. */
    private static void pollUntil(GameTestHelper helper, BooleanSupplier condition,
            String what, Runnable then) {
        pollUntil(helper, condition, what, then, 400);
    }

    private static void pollUntil(GameTestHelper helper, BooleanSupplier condition,
            String what, Runnable then, int triesLeft) {
        pollUntil(helper, condition, what, then, triesLeft, () -> "");
    }

    private static void pollUntil(GameTestHelper helper, BooleanSupplier condition,
            String what, Runnable then, int triesLeft, java.util.function.Supplier<String> dump) {
        if (condition.getAsBoolean()) {
            then.run();
            return;
        }
        if (triesLeft <= 0) {
            helper.fail("timed out waiting for " + what + ". " + dump.get());
            return;
        }
        helper.runAfterDelay(1, () -> pollUntil(helper, condition, what, then, triesLeft - 1, dump));
    }

    /** Diagnostic: chunk full-statuses, entity-section contents, and the
     * per-uuid state ({@code knownUuids} includes pending entities) —
     * explains whether a mob is hidden, serialized-away, or actually gone
     * when a poll times out. */
    private static String dumpArea(ServerLevel level, BlockPos center, int radius, List<UUID> ids) {
        StringBuilder sb = new StringBuilder("area ").append(center).append(" r=").append(radius);
        EntitySectionStorage<Entity> storage = entitySectionStorage(level);
        for (int cx = (center.getX() - radius) >> 4; cx <= (center.getX() + radius) >> 4; cx++) {
            for (int cz = (center.getZ() - radius) >> 4;
                    cz <= (center.getZ() + radius) >> 4; cz++) {
                sb.append(" [chunk ").append(cx).append(',').append(cz)
                        .append(" status=").append(level.getChunk(cx, cz).getFullStatus());
                storage.getExistingSectionPositionsInChunk(ChunkPos.asLong(cx, cz))
                        .forEach(sp -> {
                            EntitySection<Entity> section = storage.getSection(sp);
                            sb.append(" secY=").append(net.minecraft.core.SectionPos.y(sp))
                                    .append('/').append(section.getStatus())
                                    .append('(').append(section.size()).append(')');
                            section.getEntities().forEach(e -> sb.append(" <")
                                    .append(e.getType().toShortString())
                                    .append('/').append(e.getUUID()).append('>'));
                        });
                sb.append(']');
            }
        }
        for (UUID id : ids) {
            sb.append(" id=").append(id).append(": visible=").append(level.getEntity(id) != null)
                    .append(" known=").append(entityManager(level).isLoaded(id));
        }
        return sb.toString();
    }

    private static net.minecraft.world.level.entity.PersistentEntitySectionManager<Entity>
            entityManager(ServerLevel level) {
        try {
            java.lang.reflect.Field manager =
                    ServerLevel.class.getDeclaredField("entityManager");
            manager.setAccessible(true);
            return (net.minecraft.world.level.entity.PersistentEntitySectionManager<Entity>)
                    manager.get(level);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("cannot reach the entity manager", e);
        }
    }

    /**
     * Finds an entity by UUID by reading the entity section storage directly.
     * {@code level.getEntity(uuid)} and {@code getEntities(AABB)} only see
     * <em>accessible</em> sections — entities parked in non-entity-ticking
     * chunks (mock players provide no tickets) stay in section storage but
     * are invisible to every public query. Loading the chunks first also
     * restores any entities serialized with them.
     */
    private static Entity findEntity(ServerLevel level, BlockPos center, int radius, UUID id) {
        EntitySectionStorage<Entity> storage = entitySectionStorage(level);
        for (int cx = (center.getX() - radius) >> 4; cx <= (center.getX() + radius) >> 4; cx++) {
            for (int cz = (center.getZ() - radius) >> 4;
                    cz <= (center.getZ() + radius) >> 4; cz++) {
                level.getChunk(cx, cz);
                for (EntitySection<Entity> section
                        : storage.getExistingSectionsInChunk(ChunkPos.asLong(cx, cz)).toList()) {
                    for (Entity entity : section.getEntities().toList()) {
                        if (entity.getUUID().equals(id)) {
                            return entity;
                        }
                    }
                }
            }
        }
        return null;
    }

    /** Reflective access to {@code ServerLevel.entityManager.sectionStorage}
     * — the only API surface that sees entities in non-ticking sections. */
    @SuppressWarnings("unchecked")
    private static EntitySectionStorage<Entity> entitySectionStorage(ServerLevel level) {
        try {
            java.lang.reflect.Field manager =
                    ServerLevel.class.getDeclaredField("entityManager");
            manager.setAccessible(true);
            java.lang.reflect.Field storage =
                    manager.get(level).getClass().getDeclaredField("sectionStorage");
            storage.setAccessible(true);
            return (EntitySectionStorage<Entity>) storage.get(manager.get(level));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("cannot reach the entity section storage", e);
        }
    }

    private static Optional<ItemStack> compassOf(ServerPlayer player) {
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.is(ModItems.EXPEDITION_COMPASS.get())) {
                return Optional.of(stack);
            }
        }
        return Optional.empty();
    }

    /** Asserts a resolved destination satisfies the adapter safety contract. */
    private static void assertSafeDestination(GameTestHelper helper, ServerLevel level, BlockPos pos) {
        BlockState at = level.getBlockState(pos);
        helper.assertTrue(at.is(ModBlocks.EXPEDITION_CHECKPOINT.get())
                        || at.is(ModBlocks.EXPEDITION_EXIT.get()),
                "marker must be placed at " + pos);
        BlockState above = level.getBlockState(pos.above());
        helper.assertTrue(above.isAir() || above.canBeReplaced(),
                "destination must have free headroom at " + pos);
        BlockState floor = level.getBlockState(pos.below());
        helper.assertTrue(floor.isFaceSturdy(level, pos.below(), Direction.UP),
                "destination floor must be sturdy at " + pos);
        helper.assertTrue(floor.getFluidState().isEmpty(),
                "destination floor must not be fluid at " + pos);
    }

    /** Asserts the player's compass has a lodestone target equal to {@code pos}. */
    private static void assertCompassTarget(GameTestHelper helper, ServerPlayer player, BlockPos pos) {
        ItemStack compass = compassOf(player).orElseThrow(
                () -> new IllegalStateException("player must carry an expedition compass"));
        LodestoneTracker tracker = compass.get(DataComponents.LODESTONE_TRACKER);
        helper.assertTrue(tracker != null, "compass must have a lodestone tracker");
        GlobalPos target = tracker.target().orElseThrow(
                () -> new IllegalStateException("compass tracker must have a target"));
        helper.assertTrue(target.pos().equals(pos),
                "compass must point at " + pos + ", was " + target.pos());
    }
}
