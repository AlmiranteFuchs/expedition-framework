package com.expeditions.gametest;

import com.expeditions.core.ExpeditionRun;
import com.expeditions.core.ExpeditionStatus;
import com.expeditions.core.Waypoint;
import com.expeditions.server.ExpeditionManager;
import com.expeditions.server.Microbiome;
import com.expeditions.server.data.ExpeditionDefinitionLoader;
import com.expeditions.server.data.ExpeditionTuningLoader;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Fast smoke suite under its own {@code expeditions_smoke} namespace, for
 * iteration: run it alone with
 * {@code ./gradlew runGameTestServer -PgtNamespaces=expeditions_smoke}.
 * Covers the load-bearing path — terminal start, checkpoint activation,
 * completion, reward, marker cleanup — in seconds instead of the full
 * suite's ~85 tests. Helpers are shared with {@link ExpeditionGameTests}.
 */
@GameTestHolder("expeditions_smoke")
@PrefixGameTestTemplate(false)
public final class ExpeditionSmokeTests {
    static {
        // Same ambient-seeding suppression as the main suite: mock players
        // tick once at the world spawn before teleporting.
        Microbiome.suppressSeedingForTests(true);
        Microbiome.suppressTraversalForTests(true);
        ExpeditionDefinitionLoader.suppressLoadingForTests(true);
        ExpeditionTuningLoader.suppressLoadingForTests(true);
    }

    private ExpeditionSmokeTests() {
    }

    /**
     * Terminal → run → all checkpoints → exit → completion + reward +
     * marker cleanup: the one path everything else hangs off.
     */
    @GameTest(template = "arena", timeoutTicks = 400, batch = "expedition_smoke")
    public static void fullLoopCompletesAndRewards(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        ExpeditionManager manager = ExpeditionManager.get(helper.getLevel().getServer());
        UUID playerId = player.getUUID();

        ExpeditionRun run = ExpeditionGameTests.startExpedition(helper, manager, player);
        for (Waypoint checkpoint : run.checkpoints()) {
            ExpeditionGameTests.useBlockAt(helper, player,
                    ExpeditionManager.toPos(checkpoint));
        }
        helper.assertTrue(run.allCheckpointsDone(),
                "all checkpoints must be activated");
        ExpeditionGameTests.failLeakedForeignRuns(player);
        BlockPos exitPos = ExpeditionManager.toPos(run.exit());
        ExpeditionGameTests.useBlockAt(helper, player, exitPos);

        helper.assertTrue(run.status() == ExpeditionStatus.COMPLETED,
                "run must be COMPLETED, was " + run.status());
        helper.assertTrue(run.isRewarded(), "run must be rewarded");
        int expected = run.definition().tokenReward()
                + run.definition().bonusTokens();
        helper.assertTrue(manager.tokenBalance(playerId) == expected,
                "token balance must be " + expected + ", was "
                        + manager.tokenBalance(playerId));
        for (Waypoint checkpoint : run.checkpoints()) {
            helper.assertTrue(
                    helper.getLevel()
                            .getBlockState(ExpeditionManager.toPos(checkpoint))
                            .isAir(),
                    "checkpoint marker must be removed");
        }
        helper.assertTrue(helper.getLevel().getBlockState(exitPos).isAir(),
                "exit marker must be removed");
        helper.succeed();
    }
}
