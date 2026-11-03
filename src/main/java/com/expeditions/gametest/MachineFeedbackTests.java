package com.expeditions.gametest;

import com.expeditions.ExpeditionsConfig;
import com.expeditions.block.ResonanceMachineBlock;
import com.expeditions.block.entity.ResonanceMachineBlockEntity;
import com.expeditions.core.ResonanceStatus;
import com.expeditions.registry.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.ComparatorBlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("expeditions_resonance")
@PrefixGameTestTemplate(false)
public final class MachineFeedbackTests {
    private MachineFeedbackTests() { }
    private static ResonanceMachineBlockEntity place(GameTestHelper h,Block block,int x){
        var p=h.absolutePos(new BlockPos(x,1,4));h.getLevel().setBlockAndUpdate(p,block.defaultBlockState());
        return (ResonanceMachineBlockEntity)h.getLevel().getBlockEntity(p);
    }
    private static void tick(GameTestHelper h,ResonanceMachineBlockEntity be){
        ResonanceMachineBlockEntity.tick(h.getLevel(),be.getBlockPos(),h.getLevel().getBlockState(be.getBlockPos()),be);
    }

    @GameTest(template="arena",batch="machine_solid_mesh")
    public static void cleanedMeshKeepsFeetAndEmptyCoreSelection(GameTestHelper h){
        var state=ModBlocks.STILLNESS_DYNAMO.get().defaultBlockState().setValue(ResonanceMachineBlock.HAS_CORE,false);
        var shape=state.getShape(h.getLevel(),BlockPos.ZERO);
        h.assertTrue(shape.clip(new Vec3(.0625,2,.125),new Vec3(.0625,-1,.125),BlockPos.ZERO)!=null,"transparent collision solids must retain selectable feet");
        h.assertTrue(shape.clip(new Vec3(.5,.625,-1),new Vec3(.5,.625,2),BlockPos.ZERO)==null,"removing duplicate faces must not fill the empty knot opening");
        var withCore=state.setValue(ResonanceMachineBlock.HAS_CORE,true).getShape(h.getLevel(),BlockPos.ZERO);
        h.assertTrue(withCore.clip(new Vec3(.5,.625,-1),new Vec3(.5,.625,2),BlockPos.ZERO)!=null,"the core placeholder must still supply its intended collision");
        h.succeed();
    }

    @GameTest(template="arena",batch="machine_status_backpressure")
    public static void statusAndActivityFollowPaidWorkNotMerelyReservedInput(GameTestHelper h){
        var be=place(h,ModBlocks.ESSENCE_CONDENSER.get(),4);
        try {
            be.inventory.insertItem(0,new ItemStack(ModItems.DORMANT_FLAKE.get()),false);
            var power=h.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK,be.getBlockPos(),null);
            power.receiveEnergy(20,false);tick(h,be);
            h.assertTrue(be.progress()==1&&be.status()==ResonanceStatus.REFINING,"a 20 R/t supply must display real work even when each step empties the buffer");
            for(int i=0;i<4;i++)tick(h,be);
            h.assertTrue(be.progress()==1&&be.status()==ResonanceStatus.NO_POWER&&!be.getBlockState().getValue(ResonanceMachineBlock.ACTIVE),"a stalled paid sample must not keep the machine lit");
            power.receiveEnergy(80,false);be.inventory.setStackInSlot(1,new ItemStack(ModItems.ASSIMILATION_ESSENCE.get(),64));tick(h,be);
            h.assertTrue(be.energy()==80&&be.progress()==1&&be.status()==ResonanceStatus.OUTPUT_BLOCKED,"full output must pause without consuming power or the reserved sample");
            be.inventory.extractItem(1,64,false);tick(h,be);
            h.assertTrue(be.progress()==2&&be.status()==ResonanceStatus.REFINING,"clearing stock must resume the existing batch");
            var reset=be.saveWithoutMetadata(h.getLevel().registryAccess());reset.putInt("Energy",0);be.loadWithComponents(reset,h.getLevel().registryAccess());
            int before=be.progress();
            for(int i=0;i<20;i++){
                power.receiveEnergy(10,false);tick(h,be);
                if(i>0)h.assertTrue(be.status()==ResonanceStatus.REFINING&&be.getBlockState().getValue(ResonanceMachineBlock.ACTIVE),"a steady half-rate supply must not flicker the operating readout");
            }
            h.assertTrue(be.progress()==before+10,"feedback smoothing must not invent paid recipe progress");
        } finally {h.getLevel().removeBlock(be.getBlockPos(),false);}
        h.succeed();
    }

    @GameTest(template="arena",batch="machine_casual_mode")
    public static void optionalCasualPowerLeavesFuelAndProgressionProofUntouched(GameTestHelper h){
        var option=(net.neoforged.neoforge.common.ModConfigSpec.BooleanValue)ExpeditionsConfig.SPEC.getValues()
                .get(java.util.List.of("expeditions","resonance","simple_generators"));
        boolean previous=option.get();option.set(false);
        var dormant=place(h,ModBlocks.STILLNESS_DYNAMO.get(),2);var living=place(h,ModBlocks.LIVING_HEART_ENGINE.get(),5);var severed=place(h,ModBlocks.SEVERANCE_GENERATOR.get(),8);
        try {
            living.inventory.insertItem(0,new ItemStack(ModItems.ASSIMILATION_ESSENCE.get(),3),false);
            option.set(true);
            for(var be:java.util.List.of(dormant,living,severed)){
                tick(h,be);h.assertTrue(be.energy()==10&&be.actualGeneration()==10&&be.status()==ResonanceStatus.SIMPLE_GATHERING,"each casual source variant must use the same reduced power rule");
            }
            h.assertTrue(living.inventory.getStackInSlot(0).getCount()==3&&living.progress()==0,"casual power must not silently burn saved fuel");
            h.assertTrue(dormant.collectors()==1,"casual sources must not report a shared-field penalty");
            h.assertTrue(!severed.inventory.isItemValid(0,new ItemStack(ModItems.SEVERANCE_CORE.get())),"unused fuel/core slots must reject automation input in casual mode");
            option.set(false);tick(h,severed);
            h.assertTrue(severed.energy()==10&&severed.status()==ResonanceStatus.CORE_MISSING,"turning casual mode off restores the real binding requirement");
        } finally {option.set(previous);for(var be:java.util.List.of(dormant,living,severed))h.getLevel().removeBlock(be.getBlockPos(),false);}
        h.succeed();
    }

    @GameTest(template="arena",batch="machine_vanilla_comparator",timeoutTicks=80)
    public static void vanillaComparatorTracksFinishedStockAndExtraction(GameTestHelper h){
        var level=h.getLevel();var be=place(h,ModBlocks.ESSENCE_CONDENSER.get(),4);var comparator=be.getBlockPos().south();
        Runnable cleanup=()->{level.removeBlock(comparator,false);level.removeBlock(be.getBlockPos(),false);};
        try {
            level.setBlockAndUpdate(comparator.below(),Blocks.STONE.defaultBlockState());
            level.setBlockAndUpdate(comparator,Blocks.COMPARATOR.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING,Direction.NORTH));
            be.inventory.setStackInSlot(1,new ItemStack(ModItems.ASSIMILATION_ESSENCE.get(),32));
            level.scheduleTick(comparator,Blocks.COMPARATOR,1);
            h.runAfterDelay(4,()->{
                try {
                    h.assertTrue(((ComparatorBlockEntity)level.getBlockEntity(comparator)).getOutputSignal()==8,"a real comparator must read the half-full finished stock");
                    var items=level.getCapability(Capabilities.ItemHandler.BLOCK,be.getBlockPos(),Direction.DOWN);items.extractItem(1,32,false);
                    h.runAfterDelay(4,()->{try {
                        h.assertTrue(((ComparatorBlockEntity)level.getBlockEntity(comparator)).getOutputSignal()==0,"automation extraction must update the comparator without a block replacement");h.succeed();
                    } finally {cleanup.run();}});
                } catch(RuntimeException failure){cleanup.run();throw failure;}
            });
        } catch(RuntimeException failure){cleanup.run();throw failure;}
    }
}
