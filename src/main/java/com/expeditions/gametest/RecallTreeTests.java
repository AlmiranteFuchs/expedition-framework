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

    @GameTest(template="arena", batch="recall_legacy_orphans")
    public static void legacyCompactRunDoesNotClaimUnrelatedAuthoredBranches(GameTestHelper helper) {
        var root=new BlockPos(2400,80,2200);floor(helper,root);
        var level=helper.getLevel();var player=helper.makeMockServerPlayerInLevel();
        var manager=ExpeditionManager.get(player.server);
        try {
            start(helper,player,root);var data=ExpeditionGameTests.savedData(player.server);
            for(int i:data.recallTreeParts("minecraft:overworld",root).orElseThrow()) {
                var p=root.offset(RecallTrees.OFFSETS.get(i));level.removeBlock(p,false);data.untrackMarker("minecraft:overworld",p);
            }
            data.removeRecallTreeParts("minecraft:overworld",root);
            level.setBlockAndUpdate(root,ModBlocks.EXPEDITION_EXIT.get().defaultBlockState());
            int stray=java.util.stream.IntStream.range(RecallTreeLayout.AUTHORED_START,RecallTreeLayout.PIECES.size())
                    .filter(i->RecallTrees.OFFSETS.get(i).getY()>5).findFirst().orElseThrow();
            var orphan=root.offset(RecallTrees.OFFSETS.get(stray));
            level.setBlockAndUpdate(orphan,ModBlocks.RECALL_TREE_PART.get().defaultBlockState().setValue(RecallTreePartBlock.PART,stray));
            data.trackMarker("minecraft:overworld",orphan);
            ExpeditionManager.pruneOrphanMarkers(player.server);
            helper.assertTrue(level.getBlockState(orphan).isAir(),"a compact legacy exit must not protect a new-layout orphan by an imagined footprint");
            helper.assertTrue(manager.resumeOrFail(player).isPresent(),"legacy compact recovery must remain valid");
        } finally {ExpeditionGameTests.failRunIfActive(manager,player);ExpeditionDefinitions.reload(Map.of());}
        helper.succeed();
    }

    @GameTest(template="arena", batch="recall_native_source")
    public static void authoredNativeStructureLoadsWithoutTwilightForest(GameTestHelper helper) {
        var template=helper.getLevel().getStructureManager().getOrCreate(ResourceLocation.parse("expeditions:stillwood_hollow"));
        helper.assertTrue(template.getSize().equals(new net.minecraft.core.Vec3i(17,31,17)),
                "the native template must preserve the captured tree and its deep roots");
        helper.assertTrue(RecallTreeLayout.PIECES.size()-RecallTreeLayout.AUTHORED_START==930,
                "every one of the player's 930 non-air blocks must have a protected placement piece");
        helper.succeed();
    }

    @GameTest(template="arena", batch="recall_clipped_terrain")
    public static void treeYieldsToTerrainAndTracksOnlyPlacedPiecesWithoutDrops(GameTestHelper helper) {
        var root=new BlockPos(2360,80,2200);floor(helper,root);
        var level=helper.getLevel();var player=helper.makeMockServerPlayerInLevel();player.setGameMode(GameType.CREATIVE);
        var manager=ExpeditionManager.get(player.server);var obstruction=root.east();
        level.setBlockAndUpdate(obstruction,Blocks.DIAMOND_BLOCK.defaultBlockState());
        try {
            helper.assertTrue(RecallTrees.fits(level,root),"optional wood yields to terrain while the doorway stays safe");
            start(helper,player,root);
            var data=ExpeditionGameTests.savedData(player.server);
            var placed=data.recallTreeParts("minecraft:overworld",root).orElseThrow();
            helper.assertTrue(level.getBlockState(obstruction).is(Blocks.DIAMOND_BLOCK),"the dimension's existing block must remain untouched");
            helper.assertTrue(placed.stream().noneMatch(i->root.offset(RecallTrees.OFFSETS.get(i)).equals(obstruction)),"a clipped piece must never enter the placed manifest");
            helper.assertTrue(RecallTrees.intact(level,root),"intentional clipping must not look like a damaged tree");
            var restored=ExpeditionSavedData.load(data.save(new net.minecraft.nbt.CompoundTag(),level.registryAccess()),level.registryAccess());
            helper.assertTrue(restored.recallTreeParts("minecraft:overworld",root).orElseThrow().equals(placed),"the exact placed manifest must survive save/load");
            helper.assertTrue(RecallTrees.intact(level,root,restored.recallTreeParts("minecraft:overworld",root)),"a restarted clipped tree must remain valid");
            helper.assertTrue(!RecallTrees.intact(level,root,java.util.Optional.empty()),"a new clipped tree cannot recover without its placed-piece record");
            var corrupt=data.save(new net.minecraft.nbt.CompoundTag(),level.registryAccess());
            for(var value:corrupt.getList("RecallTrees",net.minecraft.nbt.Tag.TAG_COMPOUND)) {
                var tree=(net.minecraft.nbt.CompoundTag)value;
                if(tree.getInt("x")==root.getX()&&tree.getInt("y")==root.getY()&&tree.getInt("z")==root.getZ())tree.putIntArray("Parts",new int[]{Integer.MAX_VALUE});
            }
            var malformed=ExpeditionSavedData.load(corrupt,level.registryAccess());
            helper.assertTrue(!RecallTrees.intact(level,root,malformed.recallTreeParts("minecraft:overworld",root)),"a malformed manifest must fail closed rather than impersonating a legacy tree");
            int wood=placed.stream().filter(i->RecallTreeLayout.wholeBlock(i)).findFirst().orElseThrow();
            var woodPos=root.offset(RecallTrees.OFFSETS.get(wood));
            helper.assertTrue(level.getBlockState(woodPos).getDestroySpeed(level,woodPos)<0,"survival mining must not break deployed tree pieces");
            var box=new net.minecraft.world.phys.AABB(root).inflate(20);
            int items=level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,box).size();
            manager.failRun(player);
            helper.assertTrue(level.getBlockState(obstruction).is(Blocks.DIAMOND_BLOCK),"cleanup must preserve the dimension's obstruction");
            helper.assertTrue(data.recallTreeParts("minecraft:overworld",root).isEmpty(),"run end must discard its placed manifest");
            for(int i:placed)helper.assertTrue(!level.getBlockState(root.offset(RecallTrees.OFFSETS.get(i))).is(ModBlocks.RECALL_TREE_PART.get()),"every placed tree piece must despawn");
            helper.assertTrue(level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,box).size()==items,"clipping and despawning must produce no block drops");
        } finally {ExpeditionGameTests.failRunIfActive(manager,player);ExpeditionDefinitions.reload(Map.of());level.removeBlock(obstruction,false);}
        helper.succeed();
    }

    @GameTest(template="arena", batch="recall_full_blocks")
    public static void fullTreeUsesWholeLogsAndLeavesWithARecessedPortal(GameTestHelper helper) {
        int wood=0,leaves=0,portal=0;
        for(int i=RecallTreeLayout.AUTHORED_START;i<RecallTreeLayout.PIECES.size();i++) {
            var piece=RecallTreeLayout.PIECES.get(i);
            var state=ModBlocks.RECALL_TREE_PART.get().defaultBlockState().setValue(RecallTreePartBlock.PART,i);
            if(piece.kind().equals("portal")) {
                portal++;
                helper.assertTrue(piece.offset().getZ()==-1&&state.getCollisionShape(helper.getLevel(),BlockPos.ZERO).isEmpty(),
                        "the walkable portal must be recessed a full block into the trunk");
            } else if(piece.kind().startsWith("vine_")) {
                helper.assertTrue(state.getCollisionShape(helper.getLevel(),BlockPos.ZERO).isEmpty(),"the captured hanging detail must retain empty collision");
            } else {
                helper.assertTrue(state.isCollisionShapeFullBlock(helper.getLevel(),BlockPos.ZERO),
                        "each log and leaf must be an actual full block, not a thin enlarged model");
                if(piece.kind().equals("leaves"))leaves++;else wood++;
            }
        }
        helper.assertTrue(wood>25&&leaves>35&&portal==2,"the tree needs real trunk/root mass, a canopy and a two-high portal");
        helper.assertTrue(RecallTreeLayout.PIECES.stream().anyMatch(p->p.offset().getY()==14),"the authored above-ground canopy must retain its fifteen-block height");
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
        var obstruction=root.above();
        level.setBlockAndUpdate(obstruction,Blocks.DIAMOND_BLOCK.defaultBlockState());
        definition(root);
        ExpeditionGameTests.savedData(player.server).ledger().addTokens(player.getUUID(),10);
        try {
            helper.assertTrue(!RecallTrees.fits(level,root),"an occupied entrance must refuse extraction placement");
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
            var placed=data.recallTreeParts("minecraft:overworld",root).orElseThrow();
            helper.assertTrue(restored.liveMarkers().get("minecraft:overworld").containsAll(placed.stream().map(i->root.offset(RecallTrees.OFFSETS.get(i))).toList()),"every placed branch needs persisted crash tracking");
            ExpeditionManager.pruneOrphanMarkers(player.server);
            helper.assertTrue(RecallTrees.intact(level,root)&&manager.resumeOrFail(player).isPresent(),"startup pruning must preserve a live tree");
            int missing=placed.stream().filter(i->RecallTreeLayout.wholeBlock(i)&&RecallTrees.OFFSETS.get(i).getY()>1).findFirst().orElseThrow();
            level.removeBlock(root.offset(RecallTrees.OFFSETS.get(missing)),false);
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
            level.getBlockState(root.north()).entityInside(level,root.north(),player);
            helper.assertTrue(run.isActive(),"the open-looking hollow must not skip checkpoints");
            manager.activateCheckpoint(player,ExpeditionManager.toPos(run.checkpoints().getFirst()));
            helper.assertTrue(!run.objectiveState("timely").orElseThrow().completed(),
                    "the required timed objective must still await extraction evaluation");
            ExpeditionExitBlock.recall(player,root.east(12),false);
            helper.assertTrue(run.isActive(),"an unrelated exit address must not settle this charter");
            var aperture=root.above().north();
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
