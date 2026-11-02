package com.expeditions.gametest;

import com.expeditions.block.ExpeditionTerminalBlock;
import com.expeditions.core.JournalChapter;
import com.expeditions.registry.ModAttachments;
import com.expeditions.registry.ModBlocks;
import com.expeditions.registry.ModItems;
import com.expeditions.server.JournalDiscovery;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("expeditions_journal")
@PrefixGameTestTemplate(false)
public final class PresentationInteractionTests {
    private PresentationInteractionTests() { }

    @GameTest(template="arena", batch="journal_recipe_discovery")
    public static void practicalStarterRecipesRemainReadableWhileAdvancedSourcesNeedDiscovery(GameTestHelper helper) throws Exception {
        var entries=new java.util.HashMap<String,com.google.gson.JsonObject>();
        var recipes=new java.util.HashMap<String,java.util.Set<String>>();
        for(String id:java.util.List.of("first_steps","essence","preparation","liberation")){
            try(var stream=PresentationInteractionTests.class.getResourceAsStream("/assets/expeditions/patchouli_books/field_guide/en_us/entries/"+id+".json")){
                var entry=com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(java.util.Objects.requireNonNull(stream),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                entries.put(id,entry);var found=new java.util.HashSet<String>();
                for(var value:entry.getAsJsonArray("pages"))if(value.getAsJsonObject().has("recipe"))found.add(value.getAsJsonObject().get("recipe").getAsString());
                recipes.put(id,found);
            }
        }
        helper.assertTrue(!entries.get("first_steps").has("advancement")&&recipes.get("first_steps").containsAll(java.util.Set.of("expeditions:dormant_knot","expeditions:stillness_dynamo","expeditions:essence_condenser")),
                "a first book must teach the complete starter workshop without another discovery gate");
        for(String id:java.util.List.of("first_steps","essence"))helper.assertTrue(java.util.Collections.disjoint(recipes.get(id),java.util.Set.of("expeditions:living_heart_engine","expeditions:severance_core","expeditions:severance_generator")),
                "the first flake must not reveal captured-source or liberation recipes");
        helper.assertTrue(recipes.get("preparation").contains("expeditions:living_heart_engine")&&entries.get("preparation").get("advancement").getAsString().equals("expeditions:journal/preparation"),
                "the captured-source chapter must teach its engine under the earned preparation gate");
        helper.assertTrue(recipes.get("liberation").containsAll(java.util.Set.of("expeditions:severance_core","expeditions:severance_generator"))&&entries.get("liberation").get("advancement").getAsString().equals("expeditions:journal/liberation"),
                "stable remnant power must be explained only in its discovered liberation chapter");
        helper.succeed();
    }

    @GameTest(template="arena", batch="guide_resource_registry")
    public static void guideIconsRecipesAndEntitiesResolveAgainstRealRegistries(GameTestHelper helper) throws Exception {
        var entries = new java.util.ArrayList<String>();
        for (var chapter : JournalChapter.values()) entries.add(chapter.id());
        entries.add("discovery_trail");
        for (var id : entries) {
            String path = "/assets/expeditions/patchouli_books/field_guide/en_us/entries/" + id + ".json";
            try (var stream = PresentationInteractionTests.class.getResourceAsStream(path)) {
                helper.assertTrue(stream != null, "guide entry must be packaged: " + id);
                var entry = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(stream,
                        java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                helper.assertTrue(net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(
                        ResourceLocation.parse(entry.get("icon").getAsString())), "guide icon must be an actual item: " + id);
                for (var element : entry.getAsJsonArray("pages")) {
                    var page = element.getAsJsonObject();
                    if (page.has("item")) helper.assertTrue(net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(
                            ResourceLocation.parse(page.get("item").getAsString())), "spotlight item must exist: " + id);
                    if (page.has("recipe")) helper.assertTrue(helper.getLevel().getRecipeManager().byKey(
                            ResourceLocation.parse(page.get("recipe").getAsString())).isPresent(), "guide recipe must exist: " + id);
                    if (page.has("entity")) helper.assertTrue(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.containsKey(
                            ResourceLocation.parse(page.get("entity").getAsString())), "guide entity must exist: " + id);
                }
            }
        }
        helper.succeed();
    }

    @GameTest(template="arena", batch="terminal_facing")
    public static void placedTerminalFacesItsBuilderAndRotates(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        var floor = helper.absolutePos(new BlockPos(3, 1, 3));
        helper.getLevel().setBlockAndUpdate(floor, Blocks.STONE.defaultBlockState());
        helper.getLevel().setBlockAndUpdate(floor.above(), Blocks.AIR.defaultBlockState());
        player.teleportTo(floor.getX() + .5, floor.getY() + 1, floor.getZ() + 2.5);
        player.setYRot(180);
        var stack = new ItemStack(ModItems.EXPEDITION_TERMINAL_ITEM.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        player.gameMode.useItemOn(player, helper.getLevel(), stack, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(floor).add(0, .5, 0), Direction.UP, floor, false));
        var placed = helper.getLevel().getBlockState(floor.above());
        helper.assertTrue(placed.is(ModBlocks.EXPEDITION_TERMINAL.get())
                && placed.getValue(ExpeditionTerminalBlock.FACING) == Direction.SOUTH,
                "the actual block-item placement must turn the screen towards its builder");
        helper.assertTrue(placed.rotate(Rotation.CLOCKWISE_90).getValue(ExpeditionTerminalBlock.FACING) == Direction.WEST,
                "structure rotation must preserve screen orientation");
        helper.succeed();
    }

    @GameTest(template="arena", batch="portal_outline")
    public static void portalSelectionHasAnApertureAndNoWalkingCollision(GameTestHelper helper) {
        var state = ModBlocks.EXPEDITION_PORTAL.get().defaultBlockState();
        var shape = state.getShape(helper.getLevel(), helper.absolutePos(BlockPos.ZERO));
        helper.assertTrue(!shape.isEmpty(), "the visible root frame must remain selectable");
        helper.assertTrue(shape.clip(new Vec3(-1, .55, -1), new Vec3(2, .55, 2), BlockPos.ZERO) == null,
                "a diagonal ray through both empty apertures must reach the block behind");
        helper.assertTrue(shape.clip(new Vec3(.15, .55, -1), new Vec3(.15, .55, 2), BlockPos.ZERO) != null,
                "the root rim must take the selection ray");
        helper.assertTrue(state.getCollisionShape(helper.getLevel(), BlockPos.ZERO).isEmpty(),
                "the selection outline must never obstruct portal entry");
        var rotated = state.rotate(Rotation.CLOCKWISE_90);
        helper.assertTrue(rotated.getValue(com.expeditions.block.ExpeditionPortalBlock.AXIS) == Direction.Axis.Z,
                "the portal frame must rotate with its selection plane");
        helper.assertTrue(rotated.getShape(helper.getLevel(), BlockPos.ZERO).clip(
                new Vec3(-1, .55, .15), new Vec3(2, .55, .15), BlockPos.ZERO) != null,
                "the rotated frame must have selectable roots in the rotated plane");
        helper.succeed();
    }

    @GameTest(template="arena", batch="journal_advancement_migration")
    public static void oldDiscoveriesUnlockOnlyTheirMatchingGuideEntries(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        int known = JournalChapter.CARGO.unlock(JournalChapter.ESSENCE.unlock(0));
        player.setData(ModAttachments.JOURNAL_DISCOVERIES, known);
        JournalDiscovery.reconcileBookDiscoveries(player);
        for (var chapter : new JournalChapter[]{JournalChapter.ESSENCE, JournalChapter.CARGO, JournalChapter.GUARDIAN}) {
            var advancement = player.server.getAdvancements().get(
                    ResourceLocation.parse("expeditions:journal/" + chapter.id()));
            helper.assertTrue(advancement != null, "shipped guide discovery advancement must exist");
            helper.assertTrue(player.getAdvancements().getOrStartProgress(advancement).isDone()
                    == chapter.known(known), "migration must mirror only server-owned discoveries");
        }
        helper.assertTrue(player.getData(ModAttachments.JOURNAL_DISCOVERIES) == known,
                "migrating the guide must not invent discovery bits");
        JournalDiscovery.unlock(player, JournalChapter.DEFENSE);
        var defense = player.server.getAdvancements().get(ResourceLocation.parse("expeditions:journal/defense"));
        helper.assertTrue(player.getAdvancements().getOrStartProgress(defense).isDone(),
                "a new discovery must unlock its guide chapter immediately");
        helper.succeed();
    }
}
