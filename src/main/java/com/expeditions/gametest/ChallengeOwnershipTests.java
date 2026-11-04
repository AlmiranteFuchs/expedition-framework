package com.expeditions.gametest;

import com.expeditions.server.ChallengeOwnership;
import com.expeditions.server.ExpeditionManager;
import com.expeditions.server.data.ExpeditionDefinitions;
import com.google.gson.JsonParser;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("expeditions_horde")
@PrefixGameTestTemplate(false)
public final class ChallengeOwnershipTests {
    static {
        com.expeditions.server.Microbiome.suppressSeedingForTests(true);
        com.expeditions.server.Microbiome.suppressTraversalForTests(true);
        com.expeditions.server.Assimilation.suppressForTests(true);
        com.expeditions.server.data.ExpeditionDefinitionLoader.suppressLoadingForTests(true);
        com.expeditions.server.data.ExpeditionTuningLoader.suppressLoadingForTests(true);
    }
    private ChallengeOwnershipTests() { }
    private static void definition() {
        ExpeditionDefinitions.reload(Map.of(ResourceLocation.parse("test:ownership"), JsonParser.parseString("""
                {"dimension":"minecraft:overworld","checkpoints":[[2600,2600]],"exit":[2632,2600],"tokens":0}
                """)));
    }

    @GameTest(template="arena", batch="owned_horde_split")
    public static void splitChildrenKeepOwnershipAndEndedHordesCannotSplit(GameTestHelper helper) {
        var manager = ExpeditionManager.get(helper.getLevel().getServer());
        var player = helper.makeMockServerPlayerInLevel();
        var parent = EntityType.SLIME.create(helper.getLevel());
        var child = EntityType.SLIME.create(helper.getLevel());
        try {
            definition();
            var run = manager.startRun(player, helper.absolutePos(new BlockPos(2,1,2)));
            ChallengeOwnership.mark(parent, run);
            var split = new net.neoforged.neoforge.event.entity.living.MobSplitEvent(parent,
                    new java.util.ArrayList<>(List.of(child)));
            NeoForge.EVENT_BUS.post(split);
            helper.assertTrue(!split.isCanceled() && run.challengeEntities().isEmpty(),
                    "a proposed child must not be tracked before it actually ticks");
            child.tickCount = 20 - Math.floorMod(child.getId(), 20);
            NeoForge.EVENT_BUS.post(new EntityTickEvent.Post(child));
            helper.assertTrue(run.challengeEntities().contains(child.getUUID()),
                    "a live split child must inherit its run ownership");
            manager.failRun(player);
            NeoForge.EVENT_BUS.post(new EntityTickEvent.Post(child));
            helper.assertTrue(child.isRemoved() && run.challengeEntities().isEmpty(),
                    "split children must discard after the expedition ends");
            var endedSplit = new net.neoforged.neoforge.event.entity.living.MobSplitEvent(parent,
                    new java.util.ArrayList<>());
            NeoForge.EVENT_BUS.post(endedSplit);
            helper.assertTrue(endedSplit.isCanceled(), "cleanup must not spawn another generation of slimes");
        } finally {
            parent.discard(); child.discard();
            if (manager.activeRun(player.getUUID()).isPresent()) manager.failRun(player);
            ExpeditionDefinitions.reload(Map.of());
        }
        helper.succeed();
    }

    @GameTest(template="arena", batch="owned_horde_tick")
    public static void aLoadedStragglerLivesDuringTheRunAndDiscardsAfterItEnds(GameTestHelper helper) {
        var manager = ExpeditionManager.get(helper.getLevel().getServer());
        var player = helper.makeMockServerPlayerInLevel();
        var mob = EntityType.ZOMBIE.create(helper.getLevel());
        try {
            definition();
            var run = manager.startRun(player, helper.absolutePos(new BlockPos(2,1,2)));
            mob.moveTo(2900.5, 80, 2900.5, 0, 0);
            ChallengeOwnership.mark(mob, run);
            run.trackChallengeEntities(List.of(mob.getUUID()));
            mob.tickCount = 20 - Math.floorMod(mob.getId(), 20);
            NeoForge.EVENT_BUS.post(new EntityTickEvent.Post(mob));
            helper.assertTrue(!mob.isRemoved() && run.challengeEntities().contains(mob.getUUID()),
                    "ownership must never discard an active run's horde");
            manager.failRun(player);
            NeoForge.EVENT_BUS.post(new EntityTickEvent.Post(mob));
            helper.assertTrue(mob.isRemoved() && run.challengeEntities().isEmpty(),
                    "an ended run's far-away straggler must discard through the real tick event");
        } finally {
            mob.discard();
            if (manager.activeRun(player.getUUID()).isPresent()) manager.failRun(player);
            ExpeditionDefinitions.reload(Map.of());
        }
        helper.succeed();
    }

    @GameTest(template="arena", batch="owned_horde_reload")
    public static void ownershipSurvivesEntityNbtAndAnUnloadIsNotADeath(GameTestHelper helper) {
        var manager = ExpeditionManager.get(helper.getLevel().getServer());
        var player = helper.makeMockServerPlayerInLevel();
        var mob = EntityType.ZOMBIE.create(helper.getLevel());
        var restored = EntityType.ZOMBIE.create(helper.getLevel());
        try {
            definition();
            var run = manager.startRun(player, helper.absolutePos(new BlockPos(2,1,2)));
            ChallengeOwnership.mark(mob, run);
            run.trackChallengeEntities(List.of(mob.getUUID()));
            CompoundTag saved = mob.saveWithoutId(new CompoundTag());
            mob.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
            NeoForge.EVENT_BUS.post(new EntityLeaveLevelEvent(mob, helper.getLevel()));
            helper.assertTrue(run.challengeEntities().contains(mob.getUUID()),
                    "unloading must retain cleanup responsibility");
            manager.failRun(player);
            restored.load(saved);
            var join = new EntityJoinLevelEvent(restored, helper.getLevel(), true);
            NeoForge.EVENT_BUS.post(join);
            helper.assertTrue(join.isCanceled() && restored.isRemoved() && run.challengeEntities().isEmpty(),
                    "an ended run's reloaded entity must be rejected and actually discarded");
        } finally {
            mob.discard(); restored.discard();
            if (manager.activeRun(player.getUUID()).isPresent()) manager.failRun(player);
            ExpeditionDefinitions.reload(Map.of());
        }
        helper.succeed();
    }
}
