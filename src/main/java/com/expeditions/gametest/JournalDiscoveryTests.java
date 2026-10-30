package com.expeditions.gametest;

import com.expeditions.core.JournalChapter;
import com.expeditions.registry.ModAttachments;
import com.expeditions.registry.ModItems;
import com.expeditions.server.JournalDiscovery;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("expeditions_journal")
@PrefixGameTestTemplate(false)
public final class JournalDiscoveryTests {
    static {
        com.expeditions.server.Microbiome.suppressSeedingForTests(true);
        com.expeditions.server.Microbiome.suppressTraversalForTests(true);
        com.expeditions.server.Assimilation.suppressForTests(true);
        com.expeditions.server.data.ExpeditionDefinitionLoader.suppressLoadingForTests(true);
        com.expeditions.server.data.ExpeditionTuningLoader.suppressLoadingForTests(true);
    }

    private JournalDiscoveryTests() { }

    @GameTest(template = "arena", batch = "journal_memory")
    public static void discoveriesSurviveAReplacementBookSaveLoadAndDeath(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        JournalDiscovery.unlock(player, JournalChapter.CARGO);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.EXPEDITION_FIELD_JOURNAL.get()));
        ModItems.EXPEDITION_FIELD_JOURNAL.get().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        int known = player.getData(ModAttachments.JOURNAL_DISCOVERIES);
        helper.assertTrue(JournalChapter.CARGO.known(known), "a fresh book must retain personal discoveries");
        helper.assertTrue(!JournalChapter.WAYFINDING.known(known), "opening a book cannot invent portal entry");
        var restored = helper.makeMockServerPlayerInLevel();
        restored.load(player.saveWithoutId(new CompoundTag()));
        helper.assertTrue(restored.getData(ModAttachments.JOURNAL_DISCOVERIES) == known,
                "discoveries must round-trip through player NBT");
        var respawned = helper.makeMockServerPlayerInLevel();
        respawned.copyAttachmentsFrom(player, true);
        helper.assertTrue(respawned.getData(ModAttachments.JOURNAL_DISCOVERIES) == known,
                "the attachment must copy on player death");
        helper.succeed();
    }

    @GameTest(template = "arena", batch = "journal_observation")
    public static void samplesAreRememberedBeforeABookExists(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        player.getInventory().add(new ItemStack(ModItems.ASSIMILATION_ESSENCE.get()));
        JournalDiscovery.observe(player);
        helper.assertTrue(JournalChapter.ESSENCE.known(player.getData(ModAttachments.JOURNAL_DISCOVERIES)),
                "samples must be observed even before a journal is crafted");
        helper.assertTrue(!JournalChapter.EXTRACTION.known(player.getData(ModAttachments.JOURNAL_DISCOVERIES)),
                "possessing essence cannot grant successful extraction");
        helper.succeed();
    }

    @GameTest(template = "arena", batch = "journal_survival_recipe")
    public static void theDocumentedTerminalRecipeWorksInSurvival(GameTestHelper helper) {
        var level = helper.getLevel();
        var holder = level.getRecipeManager().byKey(ResourceLocation.fromNamespaceAndPath(
                "expeditions", "expedition_terminal")).orElseThrow();
        var recipe = (CraftingRecipe) holder.value();
        var input = CraftingInput.of(3, 3, java.util.List.of(
                new ItemStack(Items.IRON_INGOT), new ItemStack(Items.IRON_INGOT), new ItemStack(Items.IRON_INGOT),
                new ItemStack(ModItems.ASSIMILATION_ESSENCE.get()), new ItemStack(Items.AMETHYST_SHARD),
                new ItemStack(ModItems.ASSIMILATION_ESSENCE.get()), new ItemStack(Items.COPPER_INGOT),
                new ItemStack(Items.REDSTONE), new ItemStack(Items.COPPER_INGOT)));
        helper.assertTrue(recipe.matches(input, level), "the guide's recipe must match the actual crafting recipe");
        helper.assertTrue(recipe.assemble(input, level.registryAccess()).is(ModItems.EXPEDITION_TERMINAL_ITEM.get()),
                "the survival recipe must produce a usable terminal");
        helper.assertTrue(level.getServer().getAdvancements().get(ResourceLocation.fromNamespaceAndPath(
                "expeditions", "recipes/first_essence")) != null, "the recipe-book discovery advancement must load");
        helper.succeed();
    }
}
