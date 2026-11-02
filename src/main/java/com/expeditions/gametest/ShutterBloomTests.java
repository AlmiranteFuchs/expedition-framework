package com.expeditions.gametest;

import com.expeditions.block.BloomSourceBlock;
import com.expeditions.block.entity.AssimilationHeartBlockEntity;
import com.expeditions.registry.ModBlocks;
import com.expeditions.server.*;
import com.expeditions.server.data.ExpeditionTuning;
import com.google.gson.JsonParser;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.shapes.*;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.*;
import java.util.Map;

@GameTestHolder("expeditions_bloom")
@PrefixGameTestTemplate(false)
public final class ShutterBloomTests {
    private static ExpeditionSavedData begin(GameTestHelper h){
        Stillness.suppressForTests(true);var data=ExpeditionManager.get(h.getLevel().getServer()).savedData();data.resetCampaign("minecraft:overworld");
        ExpeditionTuning.reload(Map.of(ResourceLocation.parse("minecraft:overworld"),JsonParser.parseString("{\"microbiome\":{\"enabled\":true,\"hunt_radius\":0,\"max_colony_size\":32}}")));return data;
    }
    private static void end(ExpeditionSavedData data){data.resetCampaign("minecraft:overworld");ExpeditionTuning.reload(Map.of());Stillness.suppressForTests(true);}
    @GameTest(template="arena",batch="bloom_shapes")
    public static void everyShutterPoseHasMatchingNonCubeSelectionAndCollision(GameTestHelper h){
        var pos=h.absolutePos(new BlockPos(5,1,5));
        for(var direction:Direction.Plane.HORIZONTAL)for(int opening=0;opening<5;opening++){
            var state=ModBlocks.ASSIMILATION_HEART.get().defaultBlockState().setValue(BloomSourceBlock.FACING,direction).setValue(BloomSourceBlock.OPEN,opening);
            var shape=state.getShape(h.getLevel(),pos);var collision=state.getCollisionShape(h.getLevel(),pos);
            h.assertTrue(!Block.isShapeFullBlock(shape)&&!shape.isEmpty(),"every shell pose must have real gaps and a harvest target");
            h.assertTrue(!Shapes.joinIsNotEmpty(shape,collision,BooleanOp.NOT_SAME),"collision must be generated from the exact same pose as selection");
            h.assertTrue(shape.max(Direction.Axis.Y)<1,"the source must retain its low folded silhouette");
            for(var face:Direction.values())h.assertTrue(!state.isFaceSturdy(h.getLevel(),pos,face),"no invisible cube face may support a layer");
        }
        h.succeed();
    }
    @GameTest(template="arena",batch="bloom_terrain")
    public static void openShellSeedsOnlyRealTerrainAndKeepsOwnershipAcrossPoseChanges(GameTestHelper h){
        var data=begin(h);var level=h.getLevel();var pos=h.absolutePos(new BlockPos(6,1,6));
        try{
            for(int x=-4;x<=4;x++)for(int z=-4;z<=4;z++){level.setBlockAndUpdate(pos.offset(x,-1,z),Blocks.STONE.defaultBlockState());level.setBlockAndUpdate(pos.offset(x,0,z),Blocks.AIR.defaultBlockState());}
            level.setBlockAndUpdate(pos,ModBlocks.ASSIMILATION_HEART.get().defaultBlockState());var source=(AssimilationHeartBlockEntity)level.getBlockEntity(pos);
            for(int i=0;i<80;i++)source.pulse(level);
            h.assertTrue(!source.colony().isEmpty(),"non-cube sources must still colonize their surrounding terrain");
            for(var cell:source.colony())for(var face:MultifaceBlock.availableFaces(level.getBlockState(cell)))
                h.assertTrue(level.getBlockState(cell.relative(face)).isFaceSturdy(level,cell.relative(face),face.getOpposite()),"every grown face must have a real sturdy host");
            var members=source.colony();var saved=source.saveWithoutMetadata(level.registryAccess());
            for(int stage=0;stage<5;stage++)level.setBlockAndUpdate(pos,level.getBlockState(pos).setValue(BloomSourceBlock.OPEN,stage));
            h.assertTrue(level.getBlockEntity(pos)==source&&source.colony().equals(members),"opening is presentation, never a colony reset or source replacement");
            source.loadWithComponents(saved,level.registryAccess());h.assertTrue(source.colony().equals(members),"existing colony addresses must survive saves and the new geometry");
            level.removeBlock(pos,false);
            h.assertTrue(members.stream().allMatch(p->!level.getBlockState(p).is(ModBlocks.ASSIMILATION_LAYER.get())),"removing the source must still hibernate its linked tissue");
        }finally{level.removeBlock(pos,false);end(data);}h.succeed();
    }
    @GameTest(template="arena",batch="bloom_air")
    public static void suspendedSourceCannotCreateUnsupportedTissue(GameTestHelper h){
        var data=begin(h);var level=h.getLevel();var pos=h.absolutePos(new BlockPos(6,6,6));
        try{
            for(int x=-2;x<=2;x++)for(int y=-2;y<=2;y++)for(int z=-2;z<=2;z++)level.setBlockAndUpdate(pos.offset(x,y,z),Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(pos,ModBlocks.ASSIMILATION_HEART.get().defaultBlockState());var source=(AssimilationHeartBlockEntity)level.getBlockEntity(pos);
            for(int i=0;i<80;i++)source.pulse(level);
            h.assertTrue(source.colony().isEmpty(),"a floating shell must not paint membranes onto absent cube faces");
        }finally{level.removeBlock(pos,false);end(data);}h.succeed();
    }
    @GameTest(template="arena",batch="bloom_dormancy")
    public static void StillnessClosesTheSameOrientedColdBloom(GameTestHelper h){
        var data=begin(h);var level=h.getLevel();var pos=h.absolutePos(new BlockPos(5,1,5));
        try{
            var state=ModBlocks.ASSIMILATION_HEART.get().defaultBlockState().setValue(BloomSourceBlock.FACING,Direction.WEST).setValue(BloomSourceBlock.OPEN,4);
            level.setBlockAndUpdate(pos,state);var source=(AssimilationHeartBlockEntity)level.getBlockEntity(pos);
            Stillness.suppressForTests(false);AssimilationHeartBlockEntity.serverTick(level,pos,state,source);
            var cold=level.getBlockState(pos);
            h.assertTrue(cold.is(ModBlocks.DORMANT_HEART.get())&&cold.getValue(BloomSourceBlock.OPEN)==0&&cold.getValue(BloomSourceBlock.FACING)==Direction.WEST,"Stillness must fold the shell without rotating it or losing its stable block identity");
            h.assertTrue(!Block.isShapeFullBlock(cold.getShape(level,pos)),"the cold source must use the folded model, not the old cube");
        }finally{level.removeBlock(pos,false);end(data);}h.succeed();
    }
    @GameTest(template="arena",batch="bloom_breach_interaction")
    public static void crouchedSigilOpensTheFieldAndEssenceWakesTheOrientedBloom(GameTestHelper h){
        var data=begin(h);var level=h.getLevel();var pos=h.absolutePos(new BlockPos(5,1,5));var bloom=pos.offset(2,0,0);var player=h.makeMockServerPlayerInLevel();
        try{
            Stillness.suppressForTests(false);level.setBlockAndUpdate(pos,ModBlocks.ESSENCE_CONDENSER.get().defaultBlockState());
            var condenser=(com.expeditions.block.entity.ResonanceMachineBlockEntity)level.getBlockEntity(pos);
            var power=level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK,pos,null);
            for(int i=0;i<50;i++)power.receiveEnergy(80,false);
            var sigil=new net.minecraft.world.item.ItemStack(com.expeditions.registry.ModItems.ATTUNEMENT_SIGIL.get());
            player.teleportTo(pos.getX()+.5,pos.getY(),pos.getZ()+2.5);player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,sigil);player.setShiftKeyDown(true);
            var hit=new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos),Direction.SOUTH,pos,false);
            player.gameMode.useItemOn(player,level,sigil,net.minecraft.world.InteractionHand.MAIN_HAND,hit);
            h.assertTrue(condenser.breaching()&&!Stillness.suppresses(level,bloom),"real crouched item use must open the field despite bypassing default block use");
            level.setBlockAndUpdate(bloom,ModBlocks.DORMANT_HEART.get().defaultBlockState().setValue(BloomSourceBlock.FACING,Direction.EAST));
            var essence=new net.minecraft.world.item.ItemStack(com.expeditions.registry.ModItems.ASSIMILATION_ESSENCE.get());player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,essence);player.setShiftKeyDown(false);
            player.gameMode.useItemOn(player,level,essence,net.minecraft.world.InteractionHand.MAIN_HAND,new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(bloom),Direction.SOUTH,bloom,false));
            h.assertTrue(level.getBlockState(bloom).is(ModBlocks.ASSIMILATION_HEART.get())&&level.getBlockState(bloom).getValue(BloomSourceBlock.FACING)==Direction.EAST,"reactivation must keep the cold shell's orientation");
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,sigil);player.setShiftKeyDown(true);
            player.gameMode.useItemOn(player,level,sigil,net.minecraft.world.InteractionHand.MAIN_HAND,hit);
            h.assertTrue(!condenser.breaching(),"a second crouched use must close, not double-toggle, the field");
        }finally{level.removeBlock(pos,false);level.removeBlock(bloom,false);end(data);}h.succeed();
    }
}
