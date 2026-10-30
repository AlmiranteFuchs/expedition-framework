package com.expeditions.gametest;

import com.expeditions.core.Difficulty;
import com.expeditions.core.ExpeditionOptions;
import com.expeditions.registry.ModBlocks;
import com.expeditions.server.ExpeditionManager;
import com.expeditions.server.ExpeditionMenu;
import com.expeditions.server.data.ExpeditionDefinitions;
import com.google.gson.JsonParser;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("expeditions_opts")
@PrefixGameTestTemplate(false)
public final class TerminalBriefingTests {
    static {
        com.expeditions.server.Microbiome.suppressSeedingForTests(true);
        com.expeditions.server.Microbiome.suppressTraversalForTests(true);
        com.expeditions.server.Assimilation.suppressForTests(true);
        com.expeditions.server.data.ExpeditionDefinitionLoader.suppressLoadingForTests(true);
        com.expeditions.server.data.ExpeditionTuningLoader.suppressLoadingForTests(true);
    }
    private TerminalBriefingTests() { }

    @GameTest(template = "arena", batch = "terminal_briefing")
    public static void readyHomeMissionIsPreselectedAndLocksExplainTheirProof(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        var manager = ExpeditionManager.get(helper.getLevel().getServer());
        try {
            ExpeditionDefinitions.reload(Map.of(
                    ResourceLocation.fromNamespaceAndPath("test", "a_locked"), JsonParser.parseString("""
                    {"dimension":"minecraft:overworld","display_name":"Locked work","generate":{"checkpoints":3},"tokens":5,"requires":["expeditions:field_clear"]}
                    """),
                    ResourceLocation.fromNamespaceAndPath("test", "b_ready"), JsonParser.parseString("""
                    {"dimension":"minecraft:overworld","display_name":"Ready work","generate":{"checkpoints":3},"tokens":5}
                    """)));
            var menu = new ExpeditionMenu(1, player.getInventory(), manager, helper.absolutePos(BlockPos.ZERO), "minecraft:overworld");
            helper.assertTrue(menu.getContainer().getItem(ExpeditionMenu.SLOT_EXPEDITION)
                    .get(DataComponents.LORE).lines().getFirst().getString().equals("Ready work"),
                    "an unlocked local mission should precede a locked alphabetical default");
            menu.clicked(ExpeditionMenu.SLOT_PREVIOUS, 0, ClickType.PICKUP, player);
            helper.assertTrue(menu.getContainer().getItem(ExpeditionMenu.SLOT_START).is(Items.BARRIER),
                    "locked missions must be visibly unavailable for deployment");
            String hints = menu.getContainer().getItem(ExpeditionMenu.SLOT_READINESS)
                    .get(DataComponents.LORE).lines().toString();
            helper.assertTrue(hints.contains("Complete Field Survey"), "requirements must say how to earn the missing proof");
            menu.clicked(ExpeditionMenu.SLOT_NEXT, 0, ClickType.PICKUP, player);
            helper.assertTrue(menu.getContainer().getItem(ExpeditionMenu.SLOT_START).is(Items.LIME_CONCRETE),
                    "the next-mission control must return to ready work");
        } finally { ExpeditionDefinitions.reload(Map.of()); }
        helper.succeed();
    }

    @GameTest(template = "arena", batch = "terminal_route_safety")
    public static void aRouteCannotBeShortenedPastItsRequiredObjective(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        var manager = ExpeditionManager.get(helper.getLevel().getServer());
        var terminal = helper.absolutePos(new BlockPos(2, 1, 2));
        try {
            ExpeditionDefinitions.reload(Map.of(ResourceLocation.fromNamespaceAndPath("test", "long_work"), JsonParser.parseString("""
                    {"dimension":"minecraft:overworld","generate":{"checkpoints":4},"tokens":5,
                    "objectives":[{"id":"reach","type":"visit_checkpoint","checkpoint":3,"required":true}]}
                    """)));
            helper.getLevel().setBlock(terminal, ModBlocks.EXPEDITION_TERMINAL.get().defaultBlockState(), 3);
            var menu = new ExpeditionMenu(1, player.getInventory(), manager, terminal, "minecraft:overworld");
            menu.clicked(ExpeditionMenu.SLOT_CHECKPOINTS, 0, ClickType.PICKUP, player);
            helper.assertTrue(menu.getContainer().getItem(ExpeditionMenu.SLOT_CHECKPOINTS)
                    .get(DataComponents.LORE).lines().getFirst().getString().startsWith("3 checkpoints"),
                    "the menu must skip route lengths below the required checkpoint");
            boolean refused = false;
            try { manager.startRun(player, terminal, new ExpeditionOptions("test:long_work", Difficulty.STANDARD, 1, 0)); }
            catch (IllegalStateException expected) { refused = expected.getMessage().contains("at least 3"); }
            helper.assertTrue(refused && manager.activeRun(player.getUUID()).isEmpty(),
                    "command/API callers must also refuse impossible route overrides before deploying");
        } finally {
            if (manager.activeRun(player.getUUID()).isPresent()) manager.failRun(player);
            ExpeditionDefinitions.reload(Map.of());
        }
        helper.succeed();
    }
}
