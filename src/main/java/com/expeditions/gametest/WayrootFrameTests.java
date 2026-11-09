package com.expeditions.gametest;

import com.expeditions.block.ExpeditionPortalBlock;
import com.expeditions.block.ExpeditionTerminalBlock;
import com.expeditions.registry.ModBlocks;
import com.expeditions.registry.ModItems;
import com.expeditions.server.ExpeditionEventHandler;
import com.expeditions.server.ExpeditionManager;
import com.expeditions.server.WayrootFrame;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("expeditions_opts")
@PrefixGameTestTemplate(false)
public final class WayrootFrameTests {
    private WayrootFrameTests() { }

    private static void crossDoorway(GameTestHelper helper, ServerPlayer player, BlockPos opening) {
        // Mock login sends teleport #1, but there is no client to acknowledge it.
        player.connection.handleAcceptTeleportPacket(new ServerboundAcceptTeleportationPacket(1));
        double x = opening.getX() + .5;
        double y = opening.getY();
        double z = opening.getZ() + 1.15;
        player.absMoveTo(x, y, z + .2, 0, 0);
        player.connection.resetPosition();
        player.setOnGround(true);
        player.connection.handleMovePlayer(new ServerboundMovePlayerPacket.Pos(x, y, z, true));
        helper.assertTrue(Math.abs(player.getZ() - z) < .001 && Math.abs(player.getX() - x) < .001,
                "a normal walking packet must finish at the portal without a movement correction");
        helper.assertTrue(player.getInventory().countItem(ModItems.EXPEDITION_COMPASS.get()) == 0,
                "portal entry must wait until the walking packet has completed");
        player.connection.tick();
        ExpeditionEventHandler.onServerTick(new ServerTickEvent.Post(() -> true, player.server));
    }

    @GameTest(template = "arena", batch = "wayroot_drop")
    public static void survivalToolsRecoverWayrootExactlyOnce(GameTestHelper helper) {
        var level = helper.getLevel();
        var player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        var pos = helper.absolutePos(new BlockPos(2, 1, 2));
        player.teleportTo(pos.getX() + .5, pos.getY(), pos.getZ() + 2);
        for (var tool : java.util.List.of(net.minecraft.world.item.Items.WOODEN_AXE, net.minecraft.world.item.Items.WOODEN_PICKAXE)) {
            level.setBlockAndUpdate(pos, ModBlocks.EXPEDITION_TERMINAL.get().defaultBlockState());
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.item.ItemStack(tool));
            helper.assertTrue(player.hasCorrectToolForDrops(level.getBlockState(pos)), "axes and pickaxes can recover the Wayroot");
            helper.assertTrue(player.gameMode.destroyBlock(pos), "survival harvesting destroys the desk");
            var drops = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                    new net.minecraft.world.phys.AABB(pos).inflate(2), e -> e.getItem().is(ModBlocks.EXPEDITION_TERMINAL.get().asItem()));
            helper.assertTrue(drops.stream().mapToInt(e -> e.getItem().getCount()).sum() == 1,
                    "the missing desk loot table must never lose or duplicate its item");
            drops.forEach(net.minecraft.world.entity.Entity::discard);
        }
        helper.succeed();
    }

    @GameTest(template = "arena", batch = "wayroot_frame", timeoutTicks = 300)
    public static void framedPortalUsesDoorwayAndSealsWithoutRemovingFrame(GameTestHelper helper) {
        var level = helper.getLevel();
        var manager = ExpeditionManager.get(level.getServer());
        var player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        var desk = helper.absolutePos(new BlockPos(2, 1, 2));
        var opening = desk.relative(Direction.SOUTH.getClockWise(), 2);
        level.setBlockAndUpdate(desk, ModBlocks.EXPEDITION_TERMINAL.get().defaultBlockState()
                .setValue(ExpeditionTerminalBlock.FACING, Direction.SOUTH));
        level.setBlockAndUpdate(opening.below(), Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(opening, Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(opening.above(3), ModBlocks.GRAFT_COLLAR.get().defaultBlockState());
        helper.assertTrue(WayrootFrame.inspect(level, desk) == WayrootFrame.Condition.MISALIGNED,
                "a misplaced collar reports the layout instead of falling back to an overhead portal");
        level.removeBlock(opening.above(3), false);
        level.setBlockAndUpdate(opening.above(2), ModBlocks.GRAFT_COLLAR.get().defaultBlockState());
        helper.assertTrue(WayrootFrame.inspect(level, desk) == WayrootFrame.Condition.TIMBER, "collar reports missing timber");
        var post = ModBlocks.GRAFT_POST.get().defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y);
        for (int y = 0; y < 3; y++) {
            level.setBlockAndUpdate(opening.west().above(y), post);
            level.setBlockAndUpdate(opening.east().above(y), post);
        }
        var crown = com.expeditions.block.GraftPostBlock.CROWN;
        helper.assertTrue(level.getBlockState(opening.west().above(2)).getValue(crown) == Direction.EAST
                        && level.getBlockState(opening.east().above(2)).getValue(crown) == Direction.WEST,
                "the top posts turn their carved shoulders toward the collar");
        level.removeBlock(opening.above(2), false);
        helper.assertTrue(level.getBlockState(opening.west().above(2)).getValue(crown) == Direction.UP,
                "removing the collar restores the ordinary post");
        level.setBlockAndUpdate(opening.above(2), ModBlocks.GRAFT_COLLAR.get().defaultBlockState());
        helper.assertTrue(level.getBlockState(opening.west().above(2)).getValue(crown) == Direction.EAST,
                "replacing the collar reconnects its carved shoulder");
        level.setBlockAndUpdate(opening.above(), Blocks.STONE.defaultBlockState());
        try {
            try {
                manager.startRun(player, desk);
                helper.fail("a blocked doorway must refuse the charter");
            } catch (IllegalStateException expected) {
                helper.assertTrue(manager.activeRun(player.getUUID()).isEmpty()
                        && level.getBlockState(opening.above()).is(Blocks.STONE)
                        && level.getBlockState(opening).isAir(), "a refused arch must leave the doorway untouched");
            }
            level.setBlockAndUpdate(opening.above(), Blocks.AIR.defaultBlockState());
            var run = manager.startRun(player, desk);
            var portal = level.getBlockState(opening);
            helper.assertTrue(ExpeditionManager.toPos(run.portal()).equals(opening)
                    && portal.is(ModBlocks.EXPEDITION_PORTAL.get())
                    && portal.getValue(ExpeditionPortalBlock.FRAMED)
                    && level.getBlockState(opening.above()).isAir(), "one tracked marker fills the two-high doorway visually");
            var guest = helper.makeMockServerPlayerInLevel();
            guest.setGameMode(GameType.SURVIVAL);
            crossDoorway(helper, player, opening);
            helper.assertTrue(player.blockPosition().distSqr(ExpeditionManager.toPos(run.entry())) < 4
                            && run.portalOpen(), "the owner physically crosses without sealing the open party");
            crossDoorway(helper, guest, opening);
            helper.assertTrue(run.isMember(guest.getUUID()) && manager.activeRun(guest.getUUID()).orElseThrow() == run
                            && guest.blockPosition().distSqr(ExpeditionManager.toPos(run.entry())) < 4,
                    "a second player physically crossing the arch joins and teleports after the owner");
            manager.failRun(guest);
            manager.activateCheckpoint(player, ExpeditionManager.toPos(run.checkpoints().getFirst()));
            helper.assertTrue(!run.portalOpen() && level.getBlockState(opening).isAir()
                            && level.getBlockState(opening.above(2)).is(ModBlocks.GRAFT_COLLAR.get())
                            && level.getBlockState(opening.west()).is(ModBlocks.GRAFT_POST.get()),
                    "sealing clears the opening and leaves the builder's arch");
            manager.failRun(player);
            var second = manager.startRun(player, desk);
            level.setBlockAndUpdate(opening.east().above(), Blocks.AIR.defaultBlockState());
            helper.assertTrue(!manager.checkFramedPortal(level, opening) && !second.isActive()
                            && level.getBlockState(opening).isAir(), "a broken open frame closes and fails the pending route");
        } finally {
            if (manager.activeRun(player.getUUID()).isPresent()) manager.failRun(player);
        }
        helper.succeed();
    }
}
