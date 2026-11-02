package com.expeditions.gametest;
import com.expeditions.block.*;
import com.expeditions.block.entity.*;
import com.expeditions.registry.*;
import com.expeditions.server.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("expeditions_stillness")
@PrefixGameTestTemplate(false)
public final class StillnessTests {
    private static ExpeditionSavedData begin(GameTestHelper h){Stillness.suppressForTests(false);var data=ExpeditionManager.get(h.getLevel().getServer()).savedData();data.resetCampaign("minecraft:overworld");return data;}
    private static void end(ExpeditionSavedData data){for(var p:data.stillnessBreaches())data.setStillnessBreach(p,false);data.resetCampaign("minecraft:overworld");Stillness.suppressForTests(true);}
    private static ResonanceMachineBlockEntity machine(GameTestHelper h,BlockPos relative,boolean dynamo){var p=h.absolutePos(relative);h.getLevel().setBlockAndUpdate(p,(dynamo?ModBlocks.STILLNESS_DYNAMO.get():ModBlocks.ESSENCE_CONDENSER.get()).defaultBlockState());return (ResonanceMachineBlockEntity)h.getLevel().getBlockEntity(p);}
    private static void tick(GameTestHelper h,ResonanceMachineBlockEntity be,int times){for(int i=0;i<times;i++)ResonanceMachineBlockEntity.tick(h.getLevel(),be.getBlockPos(),h.getLevel().getBlockState(be.getBlockPos()),be);}
    private static void charge(GameTestHelper h,ResonanceMachineBlockEntity be,int amount){var energy=h.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK,be.getBlockPos(),null);for(int i=0;i<amount/80;i++)energy.receiveEnergy(80,false);}
    @GameTest(template="arena",batch="stillness_home")
    public static void homeStartsShelteredWithoutLiberationOrEssenceRecipes(GameTestHelper h){
        var data=begin(h);try{
            var pos=h.absolutePos(new BlockPos(3,1,3));h.assertTrue(!data.isDimensionCleared("minecraft:overworld") && Stillness.suppresses(h.getLevel(),pos),"protection must precede liberation");
            h.assertTrue(!Microbiome.enabled(h.getLevel()) && Microbiome.trySpawnHeart(h.getLevel(),pos,h.getLevel().random).isEmpty(),"the default home must not spawn living hearts");
            for(String name:new String[]{"stillness_dynamo","essence_condenser","expedition_field_journal","field_assay_kit"}){
                var recipe=h.getLevel().getRecipeManager().byKey(ResourceLocation.parse("expeditions:"+name)).orElseThrow().value();
                h.assertTrue(recipe.getIngredients().stream().flatMap(i->java.util.Arrays.stream(i.getItems())).noneMatch(i->i.is(ModItems.ASSIMILATION_ESSENCE.get())),"bootstrap recipe needs essence: "+name);
            }
        }finally{end(data);}h.succeed();
    }
    @GameTest(template="arena",batch="stillness_power")
    public static void twoRawFlakesMakeOneEssenceThroughRealFeCapabilities(GameTestHelper h){
        var data=begin(h);var a=machine(h,new BlockPos(3,1,3),true);var b=machine(h,new BlockPos(4,1,3),false);
        try{
            a.inventory.insertItem(0,new ItemStack(ModItems.DORMANT_FLAKE.get()),false);b.inventory.insertItem(0,new ItemStack(ModItems.DORMANT_FLAKE.get()),false);
            for(int i=0;i<200;i++){tick(h,a,1);tick(h,b,1);}
            h.assertTrue(b.inventory.getStackInSlot(1).is(ModItems.ASSIMILATION_ESSENCE.get()) && b.inventory.getStackInSlot(1).getCount()==1,"adjacent standalone machines must bootstrap one essence");
            h.assertTrue(a.energy()==0 && b.energy()==0 && a.inventory.getStackInSlot(0).isEmpty(),"one fuel flake must account for exactly 4000 FE");
            var generator=h.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK,a.getBlockPos(),null);var receiver=h.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK,b.getBlockPos(),null);
            h.assertTrue(!generator.canReceive() && !receiver.canExtract(),"energy directions must prevent feedback");
            var item=h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK,a.getBlockPos(),null);a.inventory.insertItem(0,new ItemStack(ModItems.DORMANT_FLAKE.get()),false);
            h.assertTrue(item.extractItem(0,1,false).isEmpty(),"automation extracts finished products, not raw input");
        }finally{h.getLevel().removeBlock(a.getBlockPos(),false);h.getLevel().removeBlock(b.getBlockPos(),false);end(data);}h.succeed();
    }
    @GameTest(template="arena",batch="stillness_external")
    public static void externalFeAndSavedPaidFeedstockFinishExactlyOnce(GameTestHelper h){
        var data=begin(h);var be=machine(h,new BlockPos(3,1,3),false);
        try{
            var energy=h.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK,be.getBlockPos(),Direction.UP);
            h.assertTrue(energy.receiveEnergy(80,true)==80 && be.energy()==0,"simulation cannot mint power");
            charge(h,be,4000);be.inventory.insertItem(0,new ItemStack(ModItems.DORMANT_FLAKE.get()),false);tick(h,be,100);
            h.assertTrue(be.inventory.getStackInSlot(0).isEmpty() && be.progress()==100,"processing reserves feedstock before completion");
            var tag=be.saveWithoutMetadata(h.getLevel().registryAccess());be.loadWithComponents(tag,h.getLevel().registryAccess());tick(h,be,300);
            h.assertTrue(be.energy()==0 && be.inventory.getStackInSlot(1).getCount()==1,"a saved paid batch must finish once without duplicate output");
        }finally{h.getLevel().removeBlock(be.getBlockPos(),false);end(data);}h.succeed();
    }
    @GameTest(template="arena",batch="stillness_breach")
    public static void poweredBreachIsLocalReversibleAndHeartDeathLeavesDormantFaces(GameTestHelper h){
        var data=begin(h);var be=machine(h,new BlockPos(3,1,3),false);var level=h.getLevel();
        try{
            charge(h,be,4000);h.assertTrue(be.toggleBreach(),"a charged condenser and sigil interaction must open a deliberate breach");
            var heart=h.absolutePos(new BlockPos(7,1,7));
            h.assertTrue(!Stillness.suppresses(level,heart) && Stillness.suppresses(level,heart.offset(80,0,0)),"breach influence must remain local");
            var saved=new CompoundTag();data.save(saved,level.registryAccess());h.assertTrue(ExpeditionSavedData.load(saved,level.registryAccess()).stillnessBreaches().contains(be.getBlockPos()),"breach addresses must survive saves");
            for(int x=-4;x<=4;x++)for(int z=-4;z<=4;z++)level.setBlockAndUpdate(heart.offset(x,-1,z),Blocks.STONE.defaultBlockState());
            level.setBlockAndUpdate(heart,ModBlocks.ASSIMILATION_HEART.get().defaultBlockState());var organ=(AssimilationHeartBlockEntity)level.getBlockEntity(heart);for(int i=0;i<60;i++)organ.pulse(level);
            var colony=organ.colony();h.assertTrue(!colony.isEmpty(),"an explicit breach permits living growth");
            var original=new java.util.HashMap<BlockPos,net.minecraft.world.level.block.state.BlockState>();for(var p:colony)original.put(p,level.getBlockState(p));
            level.removeBlock(heart,false);
            int retained=0;
            for(var p:colony){var cold=level.getBlockState(p);h.assertTrue(cold.isAir() || cold.is(ModBlocks.DORMANT_LAYER.get()),"heart removal must leave dormant tissue or silently remove unsupported faces");
                if(cold.isAir())continue;retained++;
                for(var face:Direction.values())if(level.getBlockState(p.relative(face)).isFaceSturdy(level,p.relative(face),face.getOpposite()))
                    h.assertTrue(MultifaceBlock.hasFace(cold,face)==MultifaceBlock.hasFace(original.get(p),face),"dormancy preserves every supported face");}
            h.assertTrue(retained>0,"stable colony members must remain as harvestable dormant traces");
            tick(h,be,201);h.assertTrue(!be.breaching() && Stillness.suppresses(level,heart),"power loss must safely restore protection");
        }finally{level.removeBlock(be.getBlockPos(),false);end(data);}h.succeed();
    }
    @GameTest(template="arena",batch="stillness_geometry")
    public static void openFramesHaveRealHolesAndNativeDormantWorldgen(GameTestHelper h){
        var data=begin(h);var level=h.getLevel();try{
            var shape=FieldMachineShapes.shape("essence_condenser",Direction.NORTH);
            h.assertTrue(!Block.isShapeFullBlock(shape),"condenser must have an open silhouette and matching collision");
            var pos=h.absolutePos(new BlockPos(7,1,7));for(int x=-4;x<=4;x++)for(int z=-4;z<=4;z++){level.setBlockAndUpdate(pos.offset(x,-1,z),Blocks.DIRT.defaultBlockState());level.setBlockAndUpdate(pos.offset(x,0,z),Blocks.AIR.defaultBlockState());}
            com.expeditions.worldgen.DormantScarFeature.suppressForTests(false);
            h.assertTrue(ModFeatures.DORMANT_SCAR.get().place(net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration.INSTANCE,level,level.getChunkSource().getGenerator(),level.random,pos),"safe raw material must generate naturally without a live heart");
        }finally{com.expeditions.worldgen.DormantScarFeature.suppressForTests(true);end(data);}h.succeed();
    }
}
