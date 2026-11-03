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

    @GameTest(template="arena", batch="recall_full_blocks")
    public static void fullTreeUsesWholeLogsAndLeavesWithARecessedPortal(GameTestHelper helper) {
        int wood=0,leaves=0,portal=0;
        for(int i=RecallTreeLayout.LEGACY_COUNT;i<RecallTreeLayout.PIECES.size();i++) {
            var piece=RecallTreeLayout.PIECES.get(i);
            var state=ModBlocks.RECALL_TREE_PART.get().defaultBlockState().setValue(RecallTreePartBlock.PART,i);
            if(piece.kind().equals("portal")) {
                portal++;
                helper.assertTrue(piece.offset().getZ()==1&&state.getCollisionShape(helper.getLevel(),BlockPos.ZERO).isEmpty(),
                        "the walkable portal must be recessed a full block into the trunk");
            } else {
                helper.assertTrue(state.isCollisionShapeFullBlock(helper.getLevel(),BlockPos.ZERO),
                        "each log and leaf must be an actual full block, not a thin enlarged model");
                if(piece.kind().equals("leaves"))leaves++;else wood++;
            }
        }
        helper.assertTrue(wood>25&&leaves>35&&portal==2,"the tree needs real trunk/root mass, a canopy and a two-high portal");
        helper.assertTrue(RecallTreeLayout.PIECES.stream().anyMatch(p->p.offset().getY()==7),"the full tree must be eight blocks tall");
        helper.succeed();
    }
    private static final String ID = "test:recall_hollow";
    private static void floor(GameTestHelper helper, BlockPos root) {
        var level = helper.getLevel();
        for (int x=-4;x<=4;x++) for(int z=-3;z<=5;z++) {
            level.setBlockAndUpdate(root.offset(x,-1,z), Blocks.STONE.defaultBlockState());
            for (int y=0;y<9;y++) level.setBlockAndUpdate(root.offset(x,y,z), Blocks.AIR.defaultBlockState());
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
            level.getBlockState(root.south()).entityInside(level,root.south(),player);
            helper.assertTrue(run.isActive(),"the open-looking hollow must not skip checkpoints");
            manager.activateCheckpoint(player,ExpeditionManager.toPos(run.checkpoints().getFirst()));
            helper.assertTrue(!run.objectiveState("timely").orElseThrow().completed(),
                    "the required timed objective must still await extraction evaluation");
            ExpeditionExitBlock.recall(player,root.east(12),false);
            helper.assertTrue(run.isActive(),"an unrelated exit address must not settle this charter");
            var aperture=root.above().south();
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
