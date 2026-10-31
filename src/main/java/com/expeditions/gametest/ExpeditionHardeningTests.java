package com.expeditions.gametest;

import com.expeditions.server.ExpeditionManager;
import com.expeditions.server.HordePools;
import com.expeditions.server.MobSpawns;
import com.expeditions.server.data.ExpeditionDefinitions;
import com.google.gson.JsonParser;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("expeditions_horde")
@PrefixGameTestTemplate(false)
public final class ExpeditionHardeningTests {
    static {
        com.expeditions.server.Microbiome.suppressSeedingForTests(true);
        com.expeditions.server.Microbiome.suppressTraversalForTests(true);
        com.expeditions.server.Assimilation.suppressForTests(true);
        com.expeditions.server.data.ExpeditionDefinitionLoader.suppressLoadingForTests(true);
        com.expeditions.server.data.ExpeditionTuningLoader.suppressLoadingForTests(true);
    }
    private ExpeditionHardeningTests() { }

    @GameTest(template = "arena", templateNamespace = "expeditions_party", batch = "portal_reuse", timeoutTicks = 400)
    public static void anEndedPartyCannotShadowAReusedTerminalPortal(GameTestHelper helper) throws Exception {
        var manager = ExpeditionManager.get(helper.getLevel().getServer());
        var first = helper.makeMockServerPlayerInLevel();
        var second = helper.makeMockServerPlayerInLevel();
        var third = helper.makeMockServerPlayerInLevel();
        var terminal = helper.absolutePos(new BlockPos(2, 1, 2));
        try {
            ExpeditionDefinitions.reload(Map.of(ResourceLocation.parse("test:portal_reuse"), JsonParser.parseString("""
                    {"dimension":"minecraft:overworld","checkpoints":[[2304,2304]],"exit":[2320,2304],"tokens":0}
                    """)));
            var ended = manager.startRun(first, terminal);
            manager.failRun(first);
            var current = manager.startRun(second, terminal);
            helper.assertTrue(ended.portal().equals(current.portal()), "the terminal must reuse the same portal address");
            manager.enterPortal(second, ExpeditionManager.toPos(current.portal()));
            helper.assertTrue(second.blockPosition().equals(ExpeditionManager.toPos(current.entry())),
                    "an ended party record must not prevent the next party from entering");
            helper.assertTrue(helper.getLevel().getBlockState(ExpeditionManager.toPos(current.portal()))
                    .is(com.expeditions.registry.ModBlocks.EXPEDITION_PORTAL.get()), "entry must not remove the live party's portal");
            var messages = new java.util.ArrayList<String>();
            var capture = new net.minecraft.commands.CommandSource() {
                @Override public void sendSystemMessage(net.minecraft.network.chat.Component text) { messages.add(text.getString()); }
                @Override public boolean acceptsSuccess() { return true; }
                @Override public boolean acceptsFailure() { return true; }
                @Override public boolean shouldInformAdmins() { return false; }
            };
            var source = second.createCommandSourceStack().withPermission(2).withSource(capture);
            int balance = manager.tokenBalance(second.getUUID());
            helper.getLevel().getServer().getCommands().getDispatcher().execute("expeditions diagnostics", source);
            var json = JsonParser.parseString(messages.getLast()).getAsJsonObject();
            helper.assertTrue(json.getAsJsonArray("Runs").get(0).getAsJsonObject().get("RunId")
                    .getAsString().equals(current.runId().toString()), "diagnostics must read the invoking player's live run");
            helper.assertTrue(current.isActive() && current.completedCheckpoints() == 0
                    && manager.tokenBalance(second.getUUID()) == balance, "diagnostics must never mutate run or reward state");
            manager.activateCheckpoint(second, ExpeditionManager.toPos(current.checkpoints().getFirst()));
            ExpeditionDefinitions.reload(Map.of(ResourceLocation.parse("test:portal_reuse"), JsonParser.parseString("""
                    {"dimension":"minecraft:overworld","checkpoints":[[2368,2304]],"exit":[2384,2304],"tokens":0}
                    """)));
            var concurrent = manager.startRun(third, terminal);
            helper.assertTrue(current.portal().equals(concurrent.portal()), "a sealed party frees its terminal address");
            manager.enterPortal(third, ExpeditionManager.toPos(concurrent.portal()));
            helper.assertTrue(third.blockPosition().equals(ExpeditionManager.toPos(concurrent.entry())),
                    "a sealed but active party must not shadow the next open portal");
            manager.failRun(second);
            helper.assertTrue(helper.getLevel().getBlockState(ExpeditionManager.toPos(concurrent.portal()))
                    .is(com.expeditions.registry.ModBlocks.EXPEDITION_PORTAL.get()),
                    "ending the old party must preserve the next party's open portal");
        } finally {
            if (manager.activeRun(first.getUUID()).isPresent()) manager.failRun(first);
            if (manager.activeRun(second.getUUID()).isPresent()) manager.failRun(second);
            if (manager.activeRun(third.getUUID()).isPresent()) manager.failRun(third);
            ExpeditionDefinitions.reload(Map.of());
        }
        helper.succeed();
    }

    @GameTest(template = "arena", batch = "horde_body_fit", timeoutTicks = 400)
    public static void anOversizedNativeRollRetriesAnotherTypeInTheSameBiome(GameTestHelper helper) throws Exception {
        var level = helper.getLevel();
        var center = new BlockPos(2048, 80, 2048);
        for (int x = -11; x <= 11; x++) for (int z = -11; z <= 11; z++) {
            level.getChunk((center.getX() + x) >> 4, (center.getZ() + z) >> 4);
            level.setBlock(center.offset(x, -1, z), Blocks.STONE.defaultBlockState(), 3);
            for (int y = 0; y < 3; y++) level.setBlock(center.offset(x, y, z), Blocks.AIR.defaultBlockState(), 3);
            // The ceiling spans the whole upward scan, so its roof cannot be
            // mistaken for another spawn surface above this three-high room.
            for (int y = 3; y <= 17; y++) level.setBlock(center.offset(x, y, z), Blocks.STONE.defaultBlockState(), 3);
        }
        level.getServer().getCommands().getDispatcher().execute("fillbiome 2036 72 2036 2060 96 2060 minecraft:soul_sand_valley",
                level.getServer().createCommandSourceStack().withLevel(level));
        String dimension = "test:body_fit";
        var pool = HordePools.candidates(level, center, dimension);
        helper.assertTrue(pool.contains(EntityType.GHAST) && pool.contains(EntityType.SKELETON),
                "the real biome must supply both oversized and viable native types");
        helper.assertTrue(MobSpawns.trySpawn(EntityType.GHAST, level, center, 8) == null,
                "the oversized type must fail real bounding-box placement");
        long seed;
        for (seed = 0; seed < 1000; seed++) {
            level.random.setSeed(seed);
            if (HordePools.pick(level, center, dimension, level.random) == EntityType.GHAST) break;
        }
        helper.assertTrue(seed < 1000, "the fixture must deliberately roll a ghast first");
        level.random.setSeed(seed);
        var mob = MobSpawns.trySpawnFromPool(level, center, 8, dimension);
        try {
            helper.assertTrue(mob != null && mob.getType() != EntityType.GHAST && pool.contains(mob.getType()),
                    "a failed oversized roll must retry within the native pool instead of silencing the wave");
            helper.assertTrue(level.noCollision(mob), "the chosen body must actually fit");
        } finally { if (mob != null) mob.discard(); }
        helper.succeed();
    }
}
