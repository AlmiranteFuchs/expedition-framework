package com.expeditions.gametest;

import com.expeditions.core.RouteAnchor;
import com.expeditions.core.Waypoint;
import com.expeditions.registry.ModBlocks;
import com.expeditions.server.ExpeditionManager;
import com.expeditions.server.data.ExpeditionDefinitions;
import com.expeditions.server.dimension.AutomaticDimensionAdapter;
import com.expeditions.server.dimension.ExpeditionAdapters;
import com.google.gson.JsonParser;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("expeditions_dim")
@PrefixGameTestTemplate(false)
public final class DimensionCompatibilityTests {
    static {
        com.expeditions.server.Microbiome.suppressSeedingForTests(true);
        com.expeditions.server.Microbiome.suppressTraversalForTests(true);
        com.expeditions.server.Assimilation.suppressForTests(true);
        com.expeditions.server.data.ExpeditionDefinitionLoader.suppressLoadingForTests(true);
        com.expeditions.server.data.ExpeditionTuningLoader.suppressLoadingForTests(true);
    }

    private DimensionCompatibilityTests() { }

    @GameTest(template = "arena", batch = "dimension_listing")
    public static void loadedDimensionsHaveDefaultMissions(GameTestHelper helper) throws Exception {
        var server = helper.getLevel().getServer();
        var manager = ExpeditionManager.get(server);
        for (var level : server.getAllLevels()) {
            helper.assertTrue(manager.listStartable().stream().anyMatch(d ->
                            d.dimension().equals(level.dimension().location().toString())),
                    "every loaded dimension needs a default expedition");
        }
        helper.assertTrue(manager.listStartable().stream().anyMatch(d ->
                        d.id().equals("expeditions:auto/minecraft/the_end")),
                "automatic mission IDs must be stable and command-addressable");
        int result = server.getCommands().getDispatcher().execute("expeditions dimensions",
                server.createCommandSourceStack());
        helper.assertTrue(result == server.levelKeys().size(),
                "dimension listing must include every loaded dimension");
        helper.succeed();
    }

    @GameTest(template = "arena", batch = "dimension_scan", timeoutTicks = 400)
    public static void automaticScanRejectsHazardsAndVoid(GameTestHelper helper) {
        var level = helper.getLevel().getServer().getLevel(Level.END);
        var adapter = new AutomaticDimensionAdapter(Level.END);
        int x = 1024, z = 1024;
        level.getChunk(x >> 4, z >> 4);
        for (int y = level.getMinBuildHeight(); y < level.getMaxBuildHeight(); y++) {
            level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 3);
        }
        var anchor = new RouteAnchor(x, z);
        helper.assertTrue(adapter.resolveAnchor(level, anchor).isEmpty(),
                "void must refuse rather than invent a landing platform");
        var floor = new BlockPos(x, 100, z);
        try {
            level.setBlock(floor, Blocks.LAVA.defaultBlockState(), 3);
            helper.assertTrue(adapter.resolveAnchor(level, anchor).isEmpty(),
                    "lava must not host an expedition");
            level.setBlock(floor, Blocks.MAGMA_BLOCK.defaultBlockState(), 3);
            helper.assertTrue(adapter.resolveAnchor(level, anchor).isEmpty(),
                    "a hazardous solid floor must also refuse");
            level.setBlock(floor, Blocks.STONE.defaultBlockState(), 3);
            helper.assertTrue(adapter.resolveAnchor(level, anchor).orElseThrow()
                            .equals(floor.above()),
                    "floating islands must resolve above a safe floor");
        } finally {
            level.setBlock(floor, Blocks.AIR.defaultBlockState(), 3);
        }
        helper.succeed();
    }

    @GameTest(template = "arena", batch = "dimension_ceiling", timeoutTicks = 400)
    public static void automaticScanStaysBelowCeiling(GameTestHelper helper) {
        var level = helper.getLevel().getServer().getLevel(Level.NETHER);
        var adapter = new AutomaticDimensionAdapter(Level.NETHER);
        int x = 320, z = 320;
        level.getChunk(x >> 4, z >> 4);
        for (int y = 101; y <= 126; y++) {
            level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 3);
        }
        level.setBlock(new BlockPos(x, 100, z), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(new BlockPos(x, 127, z), Blocks.BEDROCK.defaultBlockState(), 3);
        helper.assertTrue(adapter.resolveAnchor(level, new RouteAnchor(x, z))
                        .orElseThrow().getY() == 101,
                "enclosed dimensions must resolve below their roof");
        helper.succeed();
    }

    @GameTest(template = "arena", batch = "dimension_lifecycle", timeoutTicks = 1200)
    public static void automaticAdapterHostsRemoteDatapackMission(GameTestHelper helper) throws Exception {
        var server = helper.getLevel().getServer();
        var end = server.getLevel(Level.END);
        var manager = ExpeditionManager.get(server);
        var player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.CREATIVE);
        // Unique columns keep this probe independent of the shared route tests.
        for (int x : new int[]{64, 72, 80}) {
            end.getChunk(x >> 4, 0);
            for (int y = 101; y < end.getMaxBuildHeight(); y++) {
                end.setBlock(new BlockPos(x, y, 8), Blocks.AIR.defaultBlockState(), 3);
            }
            end.setBlock(new BlockPos(x, 100, 8), Blocks.STONE.defaultBlockState(), 3);
        }
        try {
            helper.assertTrue(ExpeditionAdapters.forLevel(end).orElseThrow()
                            instanceof AutomaticDimensionAdapter,
                    "the unregistered End must use automatic resolution");
            ExpeditionDefinitions.reload(Map.of(ResourceLocation.parse("test:auto_end"),
                    JsonParser.parseString("""
                        {"dimension":"minecraft:the_end", "display_name":"Automatic End",
                         "checkpoints":[[64,8],[72,8]], "exit":[80,8], "tokens":8}
                        """)));
            helper.assertTrue(manager.listStartable().stream().noneMatch(d ->
                            d.id().equals("expeditions:auto/minecraft/the_end")),
                    "datapack missions must replace the automatic default");
            int result = server.getCommands().getDispatcher().execute(
                    "expeditions start test:auto_end", player.createCommandSourceStack());
            helper.assertTrue(result == 1, "the command must start the remote expedition");
            var run = manager.activeRun(player.getUUID()).orElseThrow();
            helper.assertTrue(run.portalDimension().equals("minecraft:overworld"),
                    "remote missions must keep the portal at home");
            manager.enterPortal(player, ExpeditionManager.toPos(run.portal()));
            for (Waypoint waypoint : run.checkpoints()) {
                helper.assertTrue(waypoint.y() == 101, "automatic floor resolution must be used");
                manager.activateCheckpoint(player, ExpeditionManager.toPos(waypoint));
            }
            manager.completeRun(player);
            helper.assertTrue(run.isRewarded(), "automatic missions must settle rewards");
            for (Waypoint waypoint : run.checkpoints()) {
                helper.assertTrue(!end.getBlockState(ExpeditionManager.toPos(waypoint))
                                .is(ModBlocks.EXPEDITION_CHECKPOINT.get()),
                        "automatic mission markers must clean up");
            }
        } finally {
            ExpeditionGameTests.failRunIfActive(manager, player);
            ExpeditionDefinitions.reload(Map.of());
        }
        helper.succeed();
    }
}
