package com.expeditions.gametest;

import com.expeditions.core.Difficulty;
import com.expeditions.core.ExpeditionOptions;
import com.expeditions.registry.ModBlocks;
import com.expeditions.registry.ModDataComponents;
import com.expeditions.registry.ModItems;
import com.expeditions.server.ExpeditionManager;
import com.expeditions.server.ExpeditionMenu;
import com.expeditions.server.ExpeditionSavedData;
import com.expeditions.server.data.ExpeditionDefinitions;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("expeditions_prog")
@PrefixGameTestTemplate(false)
public final class FirstBearingTests {
    private FirstBearingTests() { }

    @GameTest(template = "arena", batch = "training_course", timeoutTicks = 400)
    public static void trainingGrantsItsTrophyOnlyAfterBothMarksAndExtraction(GameTestHelper h) throws Exception {
        var level = h.getLevel();
        var manager = ExpeditionManager.get(level.getServer());
        var owner = h.makeMockServerPlayerInLevel();
        var guest = h.makeMockServerPlayerInLevel();
        owner.setGameMode(GameType.CREATIVE);
        guest.setGameMode(GameType.CREATIVE);
        var id = ResourceLocation.parse(ExpeditionOptions.FIRST_BEARING);
        var terminal = h.absolutePos(new BlockPos(1, 1, 1));
        try (var reader = new InputStreamReader(Objects.requireNonNull(FirstBearingTests.class.getResourceAsStream(
                "/data/expeditions/expeditions/first_bearing.json")), StandardCharsets.UTF_8)) {
            ExpeditionDefinitions.reload(Map.of(id, JsonParser.parseReader(reader)));
            var requested = new ExpeditionOptions(id.toString(), Difficulty.BRUTAL, 9, 200);
            var run = manager.startRun(owner, terminal, requested);
            h.assertTrue(run.options().guided() && run.options().solo() && run.totalCheckpoints() == 2
                    && run.options().difficulty() == Difficulty.STANDARD, "practice settings are fixed on the server");
            var portal = ExpeditionManager.toPos(run.portal());
            manager.enterPortal(guest, portal);
            h.assertTrue(manager.activeRun(guest.getUUID()).isEmpty() && run.portalOpen()
                    && level.getBlockState(portal).is(ModBlocks.EXPEDITION_PORTAL.get()), "a guest cannot join or close a private portal");
            manager.enterPortal(owner, portal);
            assertBearing(h, owner, 1, ExpeditionManager.toPos(run.checkpoints().getFirst()));
            h.assertTrue(!manager.trainingComplete(owner.getUUID()), "entry does not grant the trophy");
            try {
                manager.completeRun(owner);
                h.fail("an early extraction must refuse");
            } catch (IllegalStateException expected) { }
            manager.activateCheckpoint(owner, ExpeditionManager.toPos(run.checkpoints().getFirst()));
            assertBearing(h, owner, 2, ExpeditionManager.toPos(run.checkpoints().getLast()));
            h.assertTrue(!run.portalOpen() && run.challengeEntities().isEmpty(), "first mark seals the portal without a horde");
            manager.activateCheckpoint(owner, ExpeditionManager.toPos(run.checkpoints().getLast()));
            assertBearing(h, owner, 3, ExpeditionManager.toPos(run.exit()));
            h.assertTrue(run.securedTokens(owner.getUUID()) == 0, "repeatable failed practice cannot bank its extraction reward");
            h.assertTrue(!manager.trainingComplete(owner.getUUID()) && run.challengeEntities().isEmpty(), "the second mark still requires extraction");
            manager.completeRun(owner);
            h.assertTrue(manager.trainingComplete(owner.getUUID()) && manager.unlocks(owner.getUUID()).contains("expeditions:field_clear")
                    && run.tokensAwarded() == 8, "the novice trophy and field proof are awarded through ordinary extraction");
            var saved = ExpeditionSavedData.load(manager.savedData().save(new CompoundTag(), level.registryAccess()), level.registryAccess());
            h.assertTrue(saved.ledger().trophies(owner.getUUID()).contains(id.toString()), "novice trophy survives save/load");
            try {
                manager.startRun(owner, terminal, requested);
                h.fail("completed practice cannot be replayed for rewards");
            } catch (IllegalStateException expected) {
                h.assertTrue(expected.getMessage().contains("already recorded"), "replay refuses before planning a route");
            }
        } finally {
            if (manager.activeRun(owner.getUUID()).isPresent()) manager.failRun(owner);
            ExpeditionDefinitions.reload(Map.of());
        }
        h.succeed();
    }

    @GameTest(template = "arena", batch = "training_solo_menu", timeoutTicks = 400)
    public static void soloMenuKeepsPortalPrivateAcrossSaveWithLegacyOpenDefault(GameTestHelper h) {
        var level = h.getLevel();
        var manager = ExpeditionManager.get(level.getServer());
        var owner = h.makeMockServerPlayerInLevel();
        var guest = h.makeMockServerPlayerInLevel();
        owner.setGameMode(GameType.CREATIVE);
        var terminal = h.absolutePos(new BlockPos(1, 1, 1));
        try {
            var menu = new ExpeditionMenu(0, owner.getInventory(), manager, terminal, "minecraft:overworld");
            menu.clicked(ExpeditionMenu.SLOT_PARTY, 0, ClickType.PICKUP, owner);
            menu.clicked(ExpeditionMenu.SLOT_START, 0, ClickType.PICKUP, owner);
            var run = manager.activeRun(owner.getUUID()).orElseThrow();
            h.assertTrue(run.options().solo() && !run.options().guided(), "ordinary charters can go solo without guidance");
            var data = manager.savedData().save(new CompoundTag(), level.registryAccess());
            var restored = ExpeditionSavedData.load(data, level.registryAccess()).ledger().activeRun(owner.getUUID()).orElseThrow();
            h.assertTrue(restored.portalOpen() && !restored.acceptsGuests(), "a saved private portal remains open to its owner only");
            try {
                restored.joinParty(guest.getUUID());
                h.fail("the domain must reject direct joins to a restored solo run");
            } catch (IllegalStateException expected) { }
            var rows = data.getList("Runs", Tag.TAG_COMPOUND);
            var malformed = data.copy();
            var malformedRows = malformed.getList("Runs", Tag.TAG_COMPOUND);
            for (int i = 0; i < malformedRows.size(); i++) {
                var row = malformedRows.getCompound(i);
                if (row.getUUID("Player").equals(owner.getUUID())) row.getCompound("Options").putString("Solo", "invalid");
            }
            h.assertTrue(ExpeditionSavedData.load(malformed, level.registryAccess()).ledger().activeRun(owner.getUUID()).isEmpty(),
                    "a corrupt private flag must not reopen the portal to guests");
            for (int i = 0; i < rows.size(); i++) {
                var row = rows.getCompound(i);
                if (row.getUUID("Player").equals(owner.getUUID())) row.getCompound("Options").remove("Solo");
            }
            var legacy = ExpeditionSavedData.load(data, level.registryAccess()).ledger().activeRun(owner.getUUID()).orElseThrow();
            h.assertTrue(legacy.acceptsGuests(), "legacy open portals remain open to parties");
            var portal = ExpeditionManager.toPos(run.portal());
            manager.enterPortal(guest, portal);
            h.assertTrue(manager.activeRun(guest.getUUID()).isEmpty(), "the world interaction respects the menu choice");
            manager.enterPortal(owner, portal);
            h.assertTrue(compass(owner).get(ModDataComponents.GUIDED_STEP.get()) == null, "regular solo run has no guide HUD");
        } finally {
            if (manager.activeRun(owner.getUUID()).isPresent()) manager.failRun(owner);
        }
        h.succeed();
    }

    private static ItemStack compass(ServerPlayer player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            var stack = player.getInventory().getItem(i);
            if (stack.is(ModItems.EXPEDITION_COMPASS.get())) return stack;
        }
        throw new IllegalStateException("No compass was issued");
    }

    private static void assertBearing(GameTestHelper h, ServerPlayer player, int step, BlockPos expected) {
        var stack = compass(player);
        var tracker = stack.get(DataComponents.LODESTONE_TRACKER);
        h.assertTrue(Objects.equals(step, stack.get(ModDataComponents.GUIDED_STEP.get()))
                && tracker != null && tracker.target().orElseThrow().pos().equals(expected),
                "practice cue and compass must target the same next step");
    }
}
