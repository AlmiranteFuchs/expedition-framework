package com.expeditions.gametest;

import com.expeditions.item.ExpeditionCompassItem;
import com.expeditions.registry.ModItems;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.LodestoneTracker;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("expeditions_compass")
@PrefixGameTestTemplate(false)
public final class CompassAttunementTests {
    private CompassAttunementTests() { }

    @GameTest(template = "arena", batch = "compass_attune")
    public static void localCompassAttunesInEitherHandWithoutChangingItsTarget(GameTestHelper helper) {
        var level = helper.getLevel();
        for (var hand : InteractionHand.values()) {
            var player = helper.makeMockServerPlayerInLevel();
            var compass = new ItemStack(ModItems.EXPEDITION_COMPASS.get());
            var target = GlobalPos.of(level.dimension(), helper.absolutePos(new BlockPos(2, 24, 2)));
            compass.set(DataComponents.LODESTONE_TRACKER, new LodestoneTracker(Optional.of(target), false));
            player.setItemInHand(hand, compass);
            var result = player.gameMode.useItem(player, level, compass, hand);
            helper.assertTrue(result.consumesAction() && player.isUsingItem(),
                    "using a local compass must begin attunement in either hand");
            helper.assertTrue(player.getUsedItemHand() == hand,
                    "attunement must use the selected hand");
            helper.assertTrue(player.getCooldowns().isOnCooldown(compass.getItem()),
                    "attunement must have a cooldown");
            helper.assertTrue(compass.get(DataComponents.LODESTONE_TRACKER).target().orElseThrow().equals(target),
                    "attunement must not alter the server-authoritative target");
            player.releaseUsingItem();
        }
        helper.succeed();
    }

    @GameTest(template = "arena", batch = "compass_attune")
    public static void unboundOrOtherDimensionCompassDoesNotAttune(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        var level = helper.getLevel();
        var compass = new ItemStack(ModItems.EXPEDITION_COMPASS.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, compass);
        player.gameMode.useItem(player, level, compass, InteractionHand.MAIN_HAND);
        helper.assertTrue(!player.isUsingItem(), "unbound compasses must stay dormant");
        compass.set(DataComponents.LODESTONE_TRACKER, new LodestoneTracker(
                Optional.of(GlobalPos.of(Level.NETHER, BlockPos.ZERO)), false));
        player.gameMode.useItem(player, level, compass, InteractionHand.MAIN_HAND);
        helper.assertTrue(!player.isUsingItem()
                        && !player.getCooldowns().isOnCooldown(compass.getItem()),
                "a target in another dimension must not consume an attunement or start its cooldown");
        helper.assertTrue(ExpeditionCompassItem.localTarget(compass, level) == null,
                "other-dimension targets must not produce a vertical cue");
        helper.succeed();
    }
}
