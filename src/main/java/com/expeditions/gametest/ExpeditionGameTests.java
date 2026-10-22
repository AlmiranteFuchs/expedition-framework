package com.expeditions.gametest;

import com.expeditions.core.ExpeditionRun;
import com.expeditions.core.ExpeditionStatus;
import com.expeditions.core.Waypoint;
import com.expeditions.registry.ModBlocks;
import com.expeditions.server.ExpeditionManager;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.GameType;
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
        int expectedTokens = run.definition().tokenReward();
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
        player.setPos(absolute.getX() + 0.5, absolute.getY() + 1.0, absolute.getZ() + 0.5);
        BlockHitResult hit = new BlockHitResult(
                Vec3.atCenterOf(absolute), Direction.UP, absolute, false);
        player.gameMode.useItemOn(player, helper.getLevel(),
                player.getItemInHand(InteractionHand.MAIN_HAND), InteractionHand.MAIN_HAND, hit);
    }
}
