package com.expeditions.gametest;

import com.expeditions.core.ExpeditionRun;
import com.expeditions.core.ExpeditionStatus;
import com.expeditions.core.Waypoint;
import com.expeditions.registry.ModBlocks;
import com.expeditions.server.ExpeditionManager;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
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
        useBlockAt(nether, player, terminalPos);

        ExpeditionManager manager = ExpeditionManager.get(server);
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
     * refuse to start a run: the terminal click leaves no active expedition.
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
        // player click would take.
        BlockPos terminalRel = new BlockPos(1, 1, 1);
        helper.setBlock(terminalRel, ModBlocks.EXPEDITION_TERMINAL.get().defaultBlockState());
        helper.useBlock(terminalRel, player);
        ExpeditionRun run = manager.activeRun(player.getUUID()).orElseThrow(
                () -> new IllegalStateException("terminal interaction must start a run"));
        return run;
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
}
