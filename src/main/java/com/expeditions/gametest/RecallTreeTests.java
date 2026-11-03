package com.expeditions.gametest;

import com.expeditions.block.ExpeditionExitBlock;
import com.expeditions.block.RecallTreePartBlock;
import com.expeditions.core.*;
import com.expeditions.registry.ModBlocks;
import com.expeditions.server.*;
import com.expeditions.server.data.ExpeditionDefinitions;
import com.google.gson.JsonParser;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("expeditions_journal")
@PrefixGameTestTemplate(false)
public final class RecallTreeTests {
    private RecallTreeTests() { }
    private static final String ID = "test:recall_hollow";
    private static void floor(GameTestHelper helper, BlockPos root) {
        var level = helper.getLevel();
        for (int x=-1;x<=1;x++) {
            level.setBlockAndUpdate(root.offset(x,-1,0), Blocks.STONE.defaultBlockState());
            for (int y=0;y<3;y++) level.setBlockAndUpdate(root.offset(x,y,0), Blocks.AIR.defaultBlockState());
        }
        var cp=root.west(8);
        level.setBlockAndUpdate(cp.below(), Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(cp, Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(cp.above(), Blocks.AIR.defaultBlockState());
    }
    private static void definition(BlockPos root) {
        ExpeditionDefinitions.reload(Map.of(ResourceLocation.parse(ID),JsonParser.parseString("""
            {"dimension":"minecraft:overworld","checkpoints":[[%d,%d]],"exit":[%d,%d],"tokens":10,"cost_tokens":5,
             "objectives":[{"id":"timely","description":"Return in time","type":"time_limit","seconds":600,"required":true}]}
            """.formatted(root.getX()-8,root.getZ(),root.getX(),root.getZ()))));
    }
    private static ExpeditionRun start(GameTestHelper helper, net.minecraft.server.level.ServerPlayer player, BlockPos root) {
        definition(root);
        ExpeditionGameTests.savedData(player.server).ledger().addTokens(player.getUUID(),10);
        return ExpeditionManager.get(player.server).startRun(player,helper.absolutePos(new BlockPos(1,1,1)),
                new ExpeditionOptions(ID,Difficulty.STANDARD,0,0));
    }

    @GameTest(template="arena", batch="recall_footprint")
    public static void obstructedHollowRefusesBeforeAnyChargeOrMarker(GameTestHelper helper) {
        var root=new BlockPos(2200,80,2200);floor(helper,root);
        var level=helper.getLevel();var player=helper.makeMockServerPlayerInLevel();
        var manager=ExpeditionManager.get(player.server);
        var obstruction=root.east().above(2);
        level.setBlockAndUpdate(obstruction,Blocks.DIAMOND_BLOCK.defaultBlockState());
        definition(root);
        ExpeditionGameTests.savedData(player.server).ledger().addTokens(player.getUUID(),10);
        try {
            helper.assertTrue(!RecallTrees.fits(level,root),"an occupied branch cell must refuse the whole hollow");
            boolean refused=false;
            try {manager.startRun(player,helper.absolutePos(new BlockPos(1,1,1)),new ExpeditionOptions(ID,Difficulty.STANDARD,0,0));}
            catch(IllegalStateException expected){refused=true;}
            helper.assertTrue(refused&&manager.tokenBalance(player.getUUID())==10,"a refused footprint must not charge a launch");
            helper.assertTrue(level.getBlockState(obstruction).is(Blocks.DIAMOND_BLOCK),"a tree must never overwrite existing terrain");
            helper.assertTrue(!level.getBlockState(root.west(8)).is(ModBlocks.EXPEDITION_CHECKPOINT.get()),"all route validation precedes every marker write");
        } finally {ExpeditionGameTests.failRunIfActive(manager,player);ExpeditionDefinitions.reload(Map.of());level.removeBlock(obstruction,false);}
        helper.succeed();
    }

    @GameTest(template="arena", batch="recall_recovery")
    public static void allTreePartsPersistAndMissingBranchFailsRecoveryCleanly(GameTestHelper helper) {
        var root=new BlockPos(2240,80,2200);floor(helper,root);
        var level=helper.getLevel();var player=helper.makeMockServerPlayerInLevel();
        var manager=ExpeditionManager.get(player.server);
        try {
            start(helper,player,root);
            var data=ExpeditionGameTests.savedData(player.server);
            helper.assertTrue(RecallTrees.intact(level,root),"new routes must place the full hollow");
            var restored=ExpeditionSavedData.load(data.save(new net.minecraft.nbt.CompoundTag(),level.registryAccess()),level.registryAccess());
            helper.assertTrue(restored.liveMarkers().get("minecraft:overworld").containsAll(RecallTrees.parts(root)),"every branch needs persisted crash tracking");
            ExpeditionManager.pruneOrphanMarkers(player.server);
            helper.assertTrue(RecallTrees.intact(level,root)&&manager.resumeOrFail(player).isPresent(),"startup pruning must preserve a live tree");
            level.removeBlock(root.offset(RecallTrees.OFFSETS.get(4)),false);
            helper.assertTrue(manager.resumeOrFail(player).isEmpty(),"a damaged hollow must fail closed on recovery");
            for(var part:RecallTrees.parts(root)) {
                helper.assertTrue(!level.getBlockState(part).is(ModBlocks.RECALL_TREE_PART.get()),"failed recovery must remove every surviving branch");
                helper.assertTrue(!data.liveMarkers().getOrDefault("minecraft:overworld",java.util.Set.of()).contains(part),"even the missing branch must be untracked");
            }
            helper.assertTrue(level.getBlockState(root).isAir(),"the failed root must be removed");
        } finally {ExpeditionGameTests.failRunIfActive(manager,player);ExpeditionDefinitions.reload(Map.of());}
        helper.succeed();
    }

    @GameTest(template="arena", batch="recall_walk")
    public static void walkingThroughTheHollowRequiresTheRouteAndRecallsExactlyOnce(GameTestHelper helper) {
        var root=new BlockPos(2280,80,2200);floor(helper,root);
        var level=helper.getLevel();var player=helper.makeMockServerPlayerInLevel();player.setGameMode(GameType.CREATIVE);
        var manager=ExpeditionManager.get(player.server);
        try {
            var run=start(helper,player,root);
            player.teleportTo(root.getX()+.5,root.getY(),root.getZ()+.5);player.tickCount=20;
            level.getBlockState(root).entityInside(level,root,player);
            helper.assertTrue(run.isActive(),"the open-looking hollow must not skip checkpoints");
            manager.activateCheckpoint(player,ExpeditionManager.toPos(run.checkpoints().getFirst()));
            helper.assertTrue(!run.objectiveState("timely").orElseThrow().completed(),
                    "the required timed objective must still await extraction evaluation");
            ExpeditionExitBlock.recall(player,root.east(12),false);
            helper.assertTrue(run.isActive(),"an unrelated exit address must not settle this charter");
            var aperture=root.above();
            level.getBlockState(aperture).entityInside(level,aperture,player);
            helper.assertTrue(run.status()==ExpeditionStatus.COMPLETED,"the upper aperture must recall a fully eligible member");
            int balance=manager.tokenBalance(player.getUUID());
            ExpeditionExitBlock.recall(player,root,true);
            helper.assertTrue(manager.tokenBalance(player.getUUID())==balance,"a second crossing cannot award again");
            helper.assertTrue(RecallTrees.parts(root).stream().allMatch(p->!level.getBlockState(p).is(ModBlocks.RECALL_TREE_PART.get())),"successful recall cleans up the whole tree");
        } finally {ExpeditionGameTests.failRunIfActive(manager,player);ExpeditionDefinitions.reload(Map.of());}
        helper.succeed();
    }

    @GameTest(template="arena", batch="recall_frame_use")
    public static void trunkInteractionRecallsAndLegacyCompactExitsRemainRecoverable(GameTestHelper helper) {
        var root=new BlockPos(2320,80,2200);floor(helper,root);
        var level=helper.getLevel();var player=helper.makeMockServerPlayerInLevel();player.setGameMode(GameType.CREATIVE);
        var manager=ExpeditionManager.get(player.server);
        try {
            var run=start(helper,player,root);
            manager.activateCheckpoint(player,ExpeditionManager.toPos(run.checkpoints().getFirst()));
            var trunk=root.west();player.teleportTo(trunk.getX()+.5,trunk.getY(),trunk.getZ()-1.5);
            player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
            player.gameMode.useItemOn(player,level,ItemStack.EMPTY,InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(trunk).add(0,0,-.5),Direction.NORTH,trunk,false));
            helper.assertTrue(run.status()==ExpeditionStatus.COMPLETED,"clicking the actual trunk must resolve to its root");
            // A legacy saved block has no grown flag or extra branches.
            level.setBlockAndUpdate(root,ModBlocks.EXPEDITION_EXIT.get().defaultBlockState());
            helper.assertTrue(RecallTrees.intact(level,root),"legacy compact exits must remain valid without branches");
            level.removeBlock(root,false);
        } finally {ExpeditionGameTests.failRunIfActive(manager,player);ExpeditionDefinitions.reload(Map.of());}
        helper.succeed();
    }
}
