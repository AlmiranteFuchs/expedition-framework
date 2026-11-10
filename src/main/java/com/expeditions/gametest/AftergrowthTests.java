package com.expeditions.gametest;

import com.expeditions.core.Difficulty;
import com.expeditions.core.ExpeditionOptions;
import com.expeditions.core.FrontierProgression;
import com.expeditions.registry.ModItems;
import com.expeditions.registry.ModBlocks;
import com.expeditions.server.ExpeditionManager;
import com.expeditions.server.ExpeditionSavedData;
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
            String masteryId = FrontierProgression.masteryId(dimension);
            var mastery = manager.listStartable().stream().filter(d -> d.id().equals(masteryId)).findFirst().orElseThrow();
            data.ledger().addTokens(owner.getUUID(), mastery.costTokens());
            helper.assertTrue(!manager.missingRequirements(owner.getUUID(), mastery).isSatisfied(),
                    "a cleared dimension and the fee alone do not give the host a personal trace");
            int pressure = data.pressure(dimension);
            var run = manager.startRun(owner, desk, request);
            helper.assertTrue(run.totalCheckpoints() == 2 && run.options().difficulty() == Difficulty.STANDARD
                            && run.options().bonusTimePercent() == 0,
                    "difficulty, length and bonus overrides cannot alter the survey");
            var portal = ExpeditionManager.toPos(run.portal());
            manager.enterPortal(owner, portal);
            manager.enterPortal(guest, portal);
            var inventory = owner.getInventory();
            for (int slot = 0; slot < 9; slot++) {
                if (inventory.getItem(slot).is(ModItems.EXPEDITION_COMPASS.get())) inventory.selected = slot;
            }
            var compass = owner.getMainHandItem();
            var tracker = compass.get(net.minecraft.core.component.DataComponents.LODESTONE_TRACKER);
            helper.assertTrue(tracker != null && manager.sharePartyBearing(owner)
                            && tracker.equals(compass.get(net.minecraft.core.component.DataComponents.LODESTONE_TRACKER)),
                    "sharing a party bearing leaves the route compass pointed at its witness");
            helper.assertTrue(run.party().contains(guest.getUUID()) && run.totalCheckpoints() == 2,
                    "a guest can join without extending the quiet route");
            var cache = ExpeditionManager.toPos(run.fieldCache().orElseThrow());
            ExpeditionGameTests.useBlockAt(helper, owner, cache);
            helper.assertTrue(!run.fieldCacheOpened(), "a sealed cache waits for the first witness");
            for (var checkpoint : run.checkpoints()) {
                manager.activateCheckpoint(owner, ExpeditionManager.toPos(checkpoint));
                helper.assertTrue(run.challengeEntities().isEmpty(), "aftergrowth has no checkpoint horde");
            }
            int settled = run.completedCheckpoints();
            var firstWitness = ExpeditionManager.toPos(run.checkpoints().getFirst());
            helper.assertTrue(manager.revealFieldCache(owner, firstWitness)
                            && !manager.revealFieldCache(owner, firstWitness.above()),
                    "only the settled first witness can repeat the cache detour bearing");
            ExpeditionGameTests.useBlockAt(helper, owner, firstWitness);
            helper.assertTrue(run.completedCheckpoints() == settled && !run.fieldCacheOpened(),
                    "rereading a cache bearing never advances or claims the run");
            ExpeditionGameTests.useBlockAt(helper, owner, cache);
            ExpeditionGameTests.useBlockAt(helper, guest, cache);
            helper.assertTrue(run.fieldCacheOpened() && owner.getInventory().countItem(ModItems.EXPEDITION_ARTIFACT.get()) == 1
                            && guest.getInventory().countItem(ModItems.EXPEDITION_ARTIFACT.get()) == 0,
                    "only the first party member opening the cache receives the find");
            var stored = data.save(new net.minecraft.nbt.CompoundTag(), level.registryAccess());
            var restored = ExpeditionSavedData.load(stored, level.registryAccess()).ledger().activeRun(owner.getUUID()).orElseThrow();
            helper.assertTrue(restored.fieldCache().equals(run.fieldCache()) && restored.fieldCacheOpened(),
                    "cache site and claim both survive save and reload");
            helper.assertTrue(restored.partyBearing().equals(run.partyBearing()),
                    "the shared bearing survives a save without extending its expiry");
            helper.assertTrue(restored.lastWitnessAt() == run.lastWitnessAt()
                            && restored.recoveryWitness(level.getGameTime()).orElseThrow().equals(run.checkpoints().getLast()),
                    "recovery points to the last activated witness after loading");
            manager.completeRun(owner);
            helper.assertTrue(!level.getBlockState(cache).is(ModBlocks.FIELD_CACHE.get())
                            && owner.getInventory().countItem(ModItems.EXPEDITION_ARTIFACT.get()) == 0,
                    "extraction removes the cache and settles the carried find");
            var returned = data.returnCaches(owner.getUUID()).getFirst();
            helper.assertTrue(returned.haul().getFirst().is(ModItems.RETURN_KNOT.get())
                            && returned.haul().getFirst().getCount() == 1 && returned.haul().size() == 4,
                    "the quiet survey files one knot, supplies and the carrier's sealed curiosity");
            helper.assertTrue(data.campaignCompletions(dimension) == 0 && !manager.unlocks(owner.getUUID()).contains("expeditions:field_clear"),
                    "a repeat survey does not farm campaign completions or field proof");
            helper.assertTrue(!data.ledger().hasUnlock(owner.getUUID(), "expeditions:reclaiming_gear/" + dimension),
                    "a quiet survey cannot grant the hard extraction trace");
            helper.assertTrue(data.pressure(dimension) == pressure, "a return to cleared ground does not stir the Bloom");
            helper.assertTrue(manager.listStartable().stream().anyMatch(d -> d.id().equals(id)),
                    "a settled survey remains available");
            helper.assertTrue(manager.missingRequirements(owner.getUUID(), mastery).isSatisfied(),
                    "the quiet extraction earns the trace needed to host Lasting Graft");

            var graft = manager.startRun(owner, desk, new ExpeditionOptions(masteryId, Difficulty.RELAXED, 2, 150));
            helper.assertTrue(graft.totalCheckpoints() == 3 && graft.options().difficulty() == Difficulty.HARD,
                    "Lasting Graft always uses three marks at Hard difficulty");
            manager.enterPortal(owner, ExpeditionManager.toPos(graft.portal()));
            for (var checkpoint : graft.checkpoints()) {
                manager.activateCheckpoint(owner, ExpeditionManager.toPos(checkpoint));
            }
            helper.assertTrue(graft.artifactIssued() && graft.defense().isPresent(),
                    "the mastery route issues its scion and begins the graft defense");
            helper.assertTrue(graft.missingRequiredObjectives().stream().anyMatch(o -> o.id().equals("graft")),
                    "the Hollow stays closed until the graft defense finishes");
            helper.assertTrue(data.pressure(dimension) == pressure,
                    "a mastery charter does not rekindle dimension pressure");
        } finally {
            if (manager.activeRun(guest.getUUID()).isPresent()) manager.failRun(guest);
            if (manager.activeRun(owner.getUUID()).isPresent()) manager.failRun(owner);
            data.resetCampaign(dimension);
        }
        helper.succeed();
    }

    @GameTest(template = "arena", batch = "quiet_market")
    public static void existingFarmerRestocksDormantSupplyAfterLiberation(GameTestHelper helper) {
        var level = helper.getLevel();
        var data = ExpeditionManager.get(level.getServer()).savedData();
        String dimension = level.dimension().location().toString();
        boolean wasCleared = data.isDimensionCleared(dimension);
        var farmer = helper.spawn(net.minecraft.world.entity.EntityType.VILLAGER, new BlockPos(2, 1, 2));
        farmer.setVillagerData(farmer.getVillagerData()
                .setProfession(net.minecraft.world.entity.npc.VillagerProfession.FARMER).setLevel(2));
        var player = helper.makeMockServerPlayerInLevel();
        try {
            data.unclearDimension(dimension);
            player.interactOn(farmer, net.minecraft.world.InteractionHand.MAIN_HAND);
            helper.assertTrue(farmer.getOffers().stream().noneMatch(o -> o.getResult().is(ModItems.DORMANT_FLAKE.get())),
                    "an uncleared Overworld farmer offers only ordinary stock");
            player.closeContainer();

            data.setDimensionCleared(dimension);
            player.interactOn(farmer, net.minecraft.world.InteractionHand.MAIN_HAND);
            helper.assertTrue(player.containerMenu instanceof net.minecraft.world.inventory.MerchantMenu,
                    "the same farmer opens the ordinary trade menu");
            var menu = (net.minecraft.world.inventory.MerchantMenu) player.containerMenu;
            var offer = menu.getOffers().getLast();
            helper.assertTrue(offer.getResult().is(ModItems.DORMANT_FLAKE.get())
                            && offer.getResult().getCount() == 4 && offer.getCostA().getCount() == 2
                            && offer.getMaxUses() == 4,
                    "liberation adds a modest, finite dormant supply");
            var discovery = com.expeditions.core.JournalChapter.QUIET_MARKET;
            helper.assertTrue(!discovery.known(player.getData(com.expeditions.registry.ModAttachments.JOURNAL_DISCOVERIES)),
                    "opening the market does not discover the journal entry");
            player.getInventory().add(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.EMERALD, 2));
            int tradeIndex = menu.getOffers().size() - 1;
            menu.setSelectionHint(tradeIndex);
            menu.tryMoveItems(tradeIndex);
            menu.quickMoveStack(player, 2);
            helper.assertTrue(offer.getUses() == 1 && player.getInventory().countItem(ModItems.DORMANT_FLAKE.get()) == 4
                            && discovery.known(player.getData(com.expeditions.registry.ModAttachments.JOURNAL_DISCOVERIES)),
                    "a completed purchase spends one use and records the discovery");
            player.closeContainer();

            var saved = farmer.saveWithoutId(new net.minecraft.nbt.CompoundTag());
            farmer.load(saved);
            player.interactOn(farmer, net.minecraft.world.InteractionHand.MAIN_HAND);
            var stocked = farmer.getOffers().stream().filter(o -> o.getResult().is(ModItems.DORMANT_FLAKE.get())).toList();
            helper.assertTrue(stocked.size() == 1 && stocked.getFirst().getUses() == 1,
                    "reopening after reload preserves a single offer and its used stock");
        } finally {
            player.closeContainer();
            if (!wasCleared) data.unclearDimension(dimension);
        }
        helper.succeed();
    }

    @GameTest(template = "arena", batch = "pale_chest")
    public static void paleChestStoresItemsAndOpens(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(2, 1, 2));
        level.setBlockAndUpdate(pos, ModBlocks.PALE_CHEST.get().defaultBlockState());
        var player = helper.makeMockServerPlayerInLevel();
        ExpeditionGameTests.useBlockAt(helper, player, pos);
        helper.assertTrue(player.containerMenu instanceof net.minecraft.world.inventory.ChestMenu,
                "the pale chest opens a standard storage menu");
        var chest = (net.minecraft.world.Container) level.getBlockEntity(pos);
        chest.setItem(0, new net.minecraft.world.item.ItemStack(ModItems.PALE_PLANKS.get(), 4));
        helper.assertTrue(chest.getItem(0).getCount() == 4, "the pale chest stores items");
        player.closeContainer();
        helper.succeed();
    }
}
