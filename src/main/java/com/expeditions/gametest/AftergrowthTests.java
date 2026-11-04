package com.expeditions.gametest;

import com.expeditions.core.Difficulty;
import com.expeditions.core.ExpeditionOptions;
import com.expeditions.core.FrontierProgression;
import com.expeditions.registry.ModItems;
import com.expeditions.server.ExpeditionManager;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("expeditions_prog")
@PrefixGameTestTemplate(false)
public final class AftergrowthTests {
    private AftergrowthTests() { }

    @GameTest(template = "arena", batch = "aftergrowth", timeoutTicks = 300)
    public static void clearedDimensionOpensQuietRepeatableSurvey(GameTestHelper helper) {
        var level = helper.getLevel();
        var manager = ExpeditionManager.get(level.getServer());
        var data = manager.savedData();
        String dimension = level.dimension().location().toString();
        String id = FrontierProgression.aftergrowthId(dimension);
        var owner = helper.makeMockServerPlayerInLevel();
        var guest = helper.makeMockServerPlayerInLevel();
        owner.setGameMode(GameType.CREATIVE);
        guest.setGameMode(GameType.CREATIVE);
        var desk = helper.absolutePos(new BlockPos(2, 1, 2));
        var request = new ExpeditionOptions(id, Difficulty.BRUTAL, 12, 150);
        data.resetCampaign(dimension);
        try {
            helper.assertTrue(manager.listStartable().stream().noneMatch(definition -> definition.id().equals(id)),
                    "survey stays off the charter list before clearance");
            try {
                manager.startRun(owner, desk, request);
                helper.fail("a direct request cannot bypass clearance");
            } catch (IllegalStateException expected) {
                helper.assertTrue(manager.activeRun(owner.getUUID()).isEmpty(), "the refusal places no run");
            }
            data.setDimensionCleared(dimension);
            int pressure = data.pressure(dimension);
            var run = manager.startRun(owner, desk, request);
            helper.assertTrue(run.totalCheckpoints() == 2 && run.options().difficulty() == Difficulty.STANDARD
                            && run.options().bonusTimePercent() == 0,
                    "difficulty, length and bonus overrides cannot alter the survey");
            var portal = ExpeditionManager.toPos(run.portal());
            manager.enterPortal(owner, portal);
            manager.enterPortal(guest, portal);
            helper.assertTrue(run.party().contains(guest.getUUID()) && run.totalCheckpoints() == 2,
                    "a guest can join without extending the quiet route");
            for (var checkpoint : run.checkpoints()) {
                manager.activateCheckpoint(owner, ExpeditionManager.toPos(checkpoint));
                helper.assertTrue(run.challengeEntities().isEmpty(), "aftergrowth has no checkpoint horde");
            }
            manager.completeRun(owner);
            helper.assertTrue(owner.getInventory().countItem(ModItems.RETURN_KNOT.get()) == 2,
                    "two return knots settle at the hollow");
            helper.assertTrue(data.campaignCompletions(dimension) == 0 && !manager.unlocks(owner.getUUID()).contains("expeditions:field_clear"),
                    "a repeat survey does not farm campaign completions or field proof");
            helper.assertTrue(!data.ledger().hasUnlock(owner.getUUID(), "expeditions:reclaiming_gear/" + dimension),
                    "a quiet survey cannot grant the hard extraction trace");
            helper.assertTrue(data.pressure(dimension) == pressure, "a return to cleared ground does not stir the Bloom");
            helper.assertTrue(manager.listStartable().stream().anyMatch(d -> d.id().equals(id)),
                    "a settled survey remains available");
        } finally {
            if (manager.activeRun(guest.getUUID()).isPresent()) manager.failRun(guest);
            if (manager.activeRun(owner.getUUID()).isPresent()) manager.failRun(owner);
            data.resetCampaign(dimension);
        }
        helper.succeed();
    }
}
