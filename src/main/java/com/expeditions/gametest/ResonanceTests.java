package com.expeditions.gametest;
import com.expeditions.block.entity.ResonanceMachineBlockEntity;
import com.expeditions.item.SeveranceCoreItem;
import com.expeditions.registry.*;
import com.expeditions.server.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("expeditions_resonance")
@PrefixGameTestTemplate(false)
public final class ResonanceTests {
    private static ExpeditionSavedData begin(GameTestHelper h){Stillness.suppressForTests(false);var d=ExpeditionManager.get(h.getLevel().getServer()).savedData();d.resetCampaign("minecraft:overworld");return d;}
    private static void end(ExpeditionSavedData d){for(var p:d.stillnessBreaches())d.setStillnessBreach(p,false);d.resetCampaign("minecraft:overworld");Stillness.suppressForTests(true);}
    private static ResonanceMachineBlockEntity place(GameTestHelper h,BlockPos r,Block block){var p=h.absolutePos(r);h.getLevel().setBlockAndUpdate(p,block.defaultBlockState());return (ResonanceMachineBlockEntity)h.getLevel().getBlockEntity(p);}
    private static void tick(GameTestHelper h,ResonanceMachineBlockEntity be,int count){for(int i=0;i<count;i++)ResonanceMachineBlockEntity.tick(h.getLevel(),be.getBlockPos(),h.getLevel().getBlockState(be.getBlockPos()),be);}
    @GameTest(template="arena",batch="resonance_field")
    public static void passiveSourcesShareQuietGroundAndNeverOpenABreach(GameTestHelper h){
        var d=begin(h);var a=place(h,new BlockPos(3,1,3),ModBlocks.STILLNESS_DYNAMO.get());var b=place(h,new BlockPos(4,1,3),ModBlocks.STILLNESS_DYNAMO.get());var chamber=place(h,new BlockPos(6,1,3),ModBlocks.ESSENCE_CONDENSER.get());
        try{
            tick(h,a,10);tick(h,b,10);
            h.assertTrue(a.energy()==100 && b.energy()==100,"two nearby knots must share twenty resonance per tick, not double it");
            h.assertTrue(d.stillnessBreaches().isEmpty(),"safe collection must never weaken the Stillness automatically");
            var input=h.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK,chamber.getBlockPos(),null);for(int i=0;i<50;i++)input.receiveEnergy(80,false);
            h.assertTrue(chamber.toggleBreach(),"fixture must deliberately open the local field");tick(h,a,1);
            h.assertTrue(a.generationRate()==0 && a.energy()==100,"a dormant knot cannot draw calm resonance inside an open breach");
        }finally{h.getLevel().removeBlock(a.getBlockPos(),false);h.getLevel().removeBlock(b.getBlockPos(),false);h.getLevel().removeBlock(chamber.getBlockPos(),false);end(d);}h.succeed();
    }
    @GameTest(template="arena",batch="resonance_living")
    public static void containedLivingHeartPaysForItsOutputAndDoesNotConsumeFuelWhenFull(GameTestHelper h){
        var d=begin(h);var be=place(h,new BlockPos(3,1,3),ModBlocks.LIVING_HEART_ENGINE.get());
        try{
            be.inventory.insertItem(0,new ItemStack(ModItems.ASSIMILATION_ESSENCE.get()),false);
            var power=h.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK,be.getBlockPos(),null);int total=0;
            for(int i=0;i<600;i++){tick(h,be,1);total+=power.extractEnergy(80,false);}
            h.assertTrue(total==48000 && be.inventory.getStackInSlot(0).isEmpty(),"one refined essence must fund exactly thirty seconds of eighty resonance per tick");
            tick(h,be,1);h.assertTrue(be.generationRate()==0,"an unfed heart must go quiet without escaping containment");
            h.assertTrue(d.stillnessBreaches().isEmpty(),"a contained heart must not infect home or open a field");
            be.inventory.insertItem(0,new ItemStack(ModItems.ASSIMILATION_ESSENCE.get()),false);tick(h,be,200);
            int remaining=be.progress();tick(h,be,100);h.assertTrue(be.progress()==remaining,"a full bank must pause upkeep instead of silently burning fuel");
            var saved=be.saveWithoutMetadata(h.getLevel().registryAccess());be.loadWithComponents(saved,h.getLevel().registryAccess());h.assertTrue(be.progress()==remaining,"remaining containment fuel must survive saves");
        }finally{h.getLevel().removeBlock(be.getBlockPos(),false);end(d);}h.succeed();
    }
    @GameTest(template="arena",batch="resonance_proof")
    public static void terminalAttunementRequiresGuardianProofAndCoreStopsWhenSourceIsReset(GameTestHelper h){
        var d=begin(h);var level=h.getLevel();var p=h.makeMockServerPlayerInLevel();var terminal=h.absolutePos(new BlockPos(6,1,3));var be=place(h,new BlockPos(3,1,3),ModBlocks.SEVERANCE_GENERATOR.get());
        try{
            level.setBlockAndUpdate(terminal,ModBlocks.EXPEDITION_TERMINAL.get().defaultBlockState());
            p.teleportTo(terminal.getX()+1.5,terminal.getY(),terminal.getZ()+.5);var core=new ItemStack(ModItems.SEVERANCE_CORE.get());p.setItemInHand(InteractionHand.MAIN_HAND,core);
            var hit=new BlockHitResult(Vec3.atCenterOf(terminal),Direction.UP,terminal,false);
            d.setDimensionCleared("minecraft:overworld");p.gameMode.useItemOn(p,level,core,InteractionHand.MAIN_HAND,hit);
            h.assertTrue(SeveranceCoreItem.source(core).isEmpty(),"a count-only cleared mark must not invent a severed core");
            d.recordGuardianLiberation("minecraft:overworld");p.gameMode.useItemOn(p,level,core,InteractionHand.MAIN_HAND,hit);
            h.assertTrue(SeveranceCoreItem.source(core).equals("minecraft:overworld"),"a proven victory must attune the actual held core through terminal interaction");
            var center=net.minecraft.world.phys.shapes.Shapes.box(.48,.66,.48,.52,.72,.52);
            h.assertTrue(!net.minecraft.world.phys.shapes.Shapes.joinIsNotEmpty(level.getBlockState(be.getBlockPos()).getShape(level,be.getBlockPos()),center,net.minecraft.world.phys.shapes.BooleanOp.AND),"an empty cradle must not select an invisible floating core");
            be.inventory.insertItem(0,core.copy(),false);tick(h,be,1);
            h.assertTrue(net.minecraft.world.phys.shapes.Shapes.joinIsNotEmpty(level.getBlockState(be.getBlockPos()).getShape(level,be.getBlockPos()),center,net.minecraft.world.phys.shapes.BooleanOp.AND),"an installed core must join the visible selection shape");
            h.assertTrue(be.energy()==120,"an installed proven core must supply stable endgame resonance");
            d.resetCampaign("minecraft:overworld");tick(h,be,1);h.assertTrue(be.generationRate()==0 && be.energy()==120,"reset proof must stop new generation without deleting already earned energy");
        }finally{level.removeBlock(terminal,false);level.removeBlock(be.getBlockPos(),false);end(d);}h.succeed();
    }
    @GameTest(template="arena",batch="resonance_persistence")
    public static void guardianLiberationProofSurvivesWorldSaveAndMalformedEntriesAreSkipped(GameTestHelper h){
        var d=begin(h);try{
            d.recordGuardianLiberation("minecraft:overworld");var tag=new CompoundTag();d.save(tag,h.getLevel().registryAccess());
            tag.getList("GuardianLiberations",net.minecraft.nbt.Tag.TAG_STRING).add(net.minecraft.nbt.StringTag.valueOf("invalid:bad:dimension"));
            var restored=ExpeditionSavedData.load(tag,h.getLevel().registryAccess());
            h.assertTrue(restored.hasGuardianLiberation("minecraft:overworld") && restored.isDimensionCleared("minecraft:overworld"),"guardian proof and the liberated flag must persist together");
            h.assertTrue(!restored.hasGuardianLiberation("invalid:bad:dimension"),"one malformed proof must not become a valid source");
        }finally{end(d);}h.succeed();
    }
    @GameTest(template="arena",batch="resonance_bootstrap")
    public static void starterKnotAndResonatorHaveNoRefinedEssenceDependency(GameTestHelper h){
        var d=begin(h);try{
            for(String name:new String[]{"dormant_knot","stillness_dynamo"}){
                var recipe=h.getLevel().getRecipeManager().byKey(ResourceLocation.parse("expeditions:"+name)).orElseThrow().value();
                h.assertTrue(recipe.getIngredients().stream().flatMap(i->java.util.Arrays.stream(i.getItems())).noneMatch(s->s.is(ModItems.ASSIMILATION_ESSENCE.get())),"starter resonance must not depend on its own refined output");
            }
            var core=new ItemStack(ModItems.SEVERANCE_CORE.get());SeveranceCoreItem.bind(core,"minecraft:the_nether");
            h.assertTrue(!SeveranceCoreItem.valid(h.getLevel(),core),"a dimension string in an item alone must not bypass server progression");
        }finally{end(d);}h.succeed();
    }
    @GameTest(template="arena",batch="resonance_distribution")
    public static void bufferedOutputFeedsEveryAcceptingFace(GameTestHelper h){
        var d=begin(h);var source=place(h,new BlockPos(5,1,5),ModBlocks.LIVING_HEART_ENGINE.get());
        var a=place(h,new BlockPos(6,1,5),ModBlocks.ESSENCE_CONDENSER.get());
        var b=place(h,new BlockPos(4,1,5),ModBlocks.ESSENCE_CONDENSER.get());
        var c=place(h,new BlockPos(5,1,6),ModBlocks.ESSENCE_CONDENSER.get());
        try{
            var tag=source.saveWithoutMetadata(h.getLevel().registryAccess());tag.putInt("Energy",16000);source.loadWithComponents(tag,h.getLevel().registryAccess());
            tick(h,source,1);
            h.assertTrue(a.energy()>0 && b.energy()>0 && c.energy()>0,"one empty neighboring bank must not starve the other receivers");
            h.assertTrue(a.energy()+b.energy()+c.energy()==160 && source.energy()==15840,"distribution must conserve exactly the exported resonance");
        }finally{for(var be:java.util.List.of(source,a,b,c))h.getLevel().removeBlock(be.getBlockPos(),false);end(d);}h.succeed();
    }

}
