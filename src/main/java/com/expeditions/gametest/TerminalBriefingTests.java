package com.expeditions.gametest;

import com.expeditions.core.Difficulty;
import com.expeditions.core.ExpeditionOptions;
import com.expeditions.registry.ModBlocks;
import com.expeditions.server.ExpeditionManager;
import com.expeditions.server.ExpeditionMenu;
import com.expeditions.server.ExpeditionText;
import com.expeditions.server.data.ExpeditionDefinitions;
import com.google.gson.JsonParser;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
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
                    {"dimension":"minecraft:overworld","display_name":"Locked work","generate":{"checkpoints":3},
                     "tokens":5,"cost_tokens":15,"requires":["expeditions:field_clear","expeditions:recovery_clear"],
                     "objectives":[
                       {"id":"cargo","type":"recover_artifact","checkpoint":2,"required":true},
                       {"id":"siege","type":"defend_device","checkpoint":3,"seconds":90,"required":true},
                       {"id":"pace","description":"Extract within six minutes","type":"time_limit","seconds":360}
                     ]}
                    """),
                    ResourceLocation.fromNamespaceAndPath("test", "b_ready"), JsonParser.parseString("""
                    {"dimension":"minecraft:overworld","display_name":"Ready work","generate":{"checkpoints":3},
                     "tokens":5,"grants":["expeditions:field_clear"]}
                    """),
                    ResourceLocation.fromNamespaceAndPath("test", "c_recovery"), JsonParser.parseString("""
                    {"dimension":"minecraft:overworld","display_name":"Trace Recovery","generate":{"checkpoints":3},
                     "tokens":5,"requires":["expeditions:field_clear"],"grants":["expeditions:recovery_clear"]}
                    """)));
            var menu = new ExpeditionMenu(1, player.getInventory(), manager, helper.absolutePos(BlockPos.ZERO), "minecraft:overworld");
            helper.assertTrue(menu.getContainer().getItem(ExpeditionMenu.SLOT_EXPEDITION)
                    .get(DataComponents.LORE).lines().getFirst().getString().equals("Ready work"),
                    "an unlocked local mission should precede a locked alphabetical default");
            var followups = menu.getContainer().getItem(ExpeditionMenu.SLOT_BRIEFING)
                    .get(DataComponents.LORE).lines().stream().map(Component::getString).toList();
            helper.assertTrue(followups.contains("Trace Recovery") && !followups.contains("Locked work"),
                    "a briefing only lists follow-ups whose remaining proofs its own completion supplies");
            menu.clicked(ExpeditionMenu.SLOT_PREVIOUS, 0, ClickType.PICKUP, player);
            helper.assertTrue(menu.getContainer().getItem(ExpeditionMenu.SLOT_START).is(Items.BARRIER),
                    "locked missions must be visibly unavailable for deployment");
            var readiness = menu.getContainer().getItem(ExpeditionMenu.SLOT_READINESS)
                    .get(DataComponents.LORE).lines().stream().map(Component::getString).toList();
            helper.assertTrue(readiness.getFirst().equals("Complete Ready work.")
                            && readiness.stream().anyMatch(line -> line.contains("Complete Trace Recovery"))
                            && readiness.stream().anyMatch(line -> line.contains("0 / 15 tokens"))
                            && readiness.stream().noneMatch(line -> line.contains("expeditions:")),
                    "the locked charter shows its next action, missing proof and current fee progress in plain language");
            var mission = menu.getContainer().getItem(ExpeditionMenu.SLOT_BRIEFING)
                    .get(DataComponents.LORE).lines().stream().map(Component::getString).toList();
            var extras = menu.getContainer().getItem(ExpeditionMenu.SLOT_EXTRAS)
                    .get(DataComponents.LORE).lines().stream().map(Component::getString).toList();
            helper.assertTrue(mission.stream().anyMatch(line -> line.contains("cargo at stump 2"))
                            && mission.stream().anyMatch(line -> line.contains("90 seconds"))
                            && mission.stream().anyMatch(line -> line.contains("with the cargo"))
                            && mission.stream().noneMatch(line -> line.contains("six minutes"))
                            && extras.contains("Extract within six minutes"),
                    "the mission briefing explains required play while keeping timed bonuses in Extras");
            helper.assertTrue(menu.getContainer().getItem(ExpeditionMenu.SLOT_EXPEDITION)
                            .get(DataComponents.LORE).lines().get(1).getString().equals("Overworld")
                            && ExpeditionText.dimension("twilightforest:twilightforest").getString().equals("Twilight Forest")
                            && ExpeditionText.dimension("othermod:fractured_reaches").getString().equals("Fractured Reaches")
                            && ExpeditionText.title("Explore twilightforest:twilightforest").equals("Explore Twilight Forest"),
                    "vanilla and modded dimensions have readable names even without translations");
            menu.clicked(ExpeditionMenu.SLOT_NEXT, 0, ClickType.PICKUP, player);
            helper.assertTrue(menu.getContainer().getItem(ExpeditionMenu.SLOT_START).is(Items.LIME_CONCRETE),
                    "the next-mission control must return to ready work");
            manager.grantUnlock(player.getUUID(), "expeditions:field_clear");
            menu.clicked(ExpeditionMenu.SLOT_PREVIOUS, 0, ClickType.PICKUP, player);
            helper.assertTrue(menu.getContainer().getItem(ExpeditionMenu.SLOT_READINESS)
                            .get(DataComponents.LORE).lines().getFirst().getString().equals("Complete Trace Recovery."),
                    "the next action updates when the earlier proof has been earned");
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
