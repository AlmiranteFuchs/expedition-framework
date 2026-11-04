package com.expeditions.gametest;

import com.expeditions.block.entity.*;
import com.expeditions.core.*;
import com.expeditions.item.ResonanceCellItem;
import com.expeditions.registry.*;
import com.expeditions.server.*;
import com.expeditions.server.data.*;
import com.google.gson.JsonParser;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.LodestoneTracker;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

@GameTestHolder("expeditions_preparation")
@PrefixGameTestTemplate(false)
public final class FieldPreparationTests {
    private static ItemStack cell(int energy){var s=new ItemStack(ModItems.RESONANCE_CELL.get());s.set(ModDataComponents.RESONANCE.get(),energy);return s;}
    @GameTest(template="arena",batch="preparation_transfer")
    public static void portableChargeTransfersThroughRealBlockInteractionAndPersists(GameTestHelper h){
        var level=h.getLevel();var p=h.makeMockServerPlayerInLevel();var pos=h.absolutePos(new BlockPos(5,1,5));
        level.setBlockAndUpdate(pos,ModBlocks.ESSENCE_CONDENSER.get().defaultBlockState());
        var machine=(ResonanceMachineBlockEntity)level.getBlockEntity(pos);var s=cell(4000);p.setItemInHand(InteractionHand.MAIN_HAND,s);
        var storage=s.getCapability(Capabilities.EnergyStorage.ITEM);
        h.assertTrue(storage!=null&&storage.extractEnergy(500,true)==500&&ResonanceCellItem.energy(s)==4000,"FE simulation must leave the actual cell untouched");
        p.teleportTo(pos.getX()+.5,pos.getY(),pos.getZ()+2.5);
        var hit=new BlockHitResult(Vec3.atCenterOf(pos),Direction.SOUTH,pos,false);
        p.gameMode.useItemOn(p,level,s,InteractionHand.MAIN_HAND,hit);
        h.assertTrue(machine.energy()==80&&ResonanceCellItem.energy(s)==3920,"block interaction must transfer only the receiver's actual accepted energy");
        h.assertTrue(p.containerMenu==p.inventoryMenu,"cell use must not open the machine menu instead");
        var restored=ItemStack.parseOptional(level.registryAccess(),(CompoundTag)s.save(level.registryAccess()));
        h.assertTrue(ResonanceCellItem.energy(restored)==3920,"carried energy must survive stack save/load");
        var source=pos.offset(3,0,0);level.setBlockAndUpdate(source,ModBlocks.STILLNESS_DYNAMO.get().defaultBlockState());
        var bank=(ResonanceMachineBlockEntity)level.getBlockEntity(source);var tag=bank.saveWithoutMetadata(level.registryAccess());tag.putInt("Energy",1000);bank.loadWithComponents(tag,level.registryAccess());
        p.setShiftKeyDown(true);var context=new net.minecraft.world.item.context.UseOnContext(p,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(source),Direction.SOUTH,source,false));
        ModItems.RESONANCE_CELL.get().useOn(context);
        h.assertTrue(bank.energy()==744&&ResonanceCellItem.energy(s)==4176,"sneak-use must withdraw conserved generator charge");
        level.removeBlock(source,false);level.removeBlock(pos,false);h.succeed();
    }
    @GameTest(template="arena",batch="preparation_ward")
    public static void paidWardHoldsAttentionWithoutCoolingOrInfectionImmunity(GameTestHelper h){
        var level=h.getLevel();var manager=ExpeditionManager.get(level.getServer());var data=manager.savedData();var p=h.makeMockServerPlayerInLevel();
        var pos=h.absolutePos(new BlockPos(5,1,5));level.setBlockAndUpdate(pos,ModBlocks.FIELD_WARD.get().defaultBlockState());
        var ward=(FieldWardBlockEntity)level.getBlockEntity(pos);var s=cell(8000);
        FrontierThreat.suppressForTests(false);Stillness.suppressForTests(true);data.resetCampaign("minecraft:overworld");
        ExpeditionTuning.reload(Map.of(ResourceLocation.parse("minecraft:overworld"),JsonParser.parseString("{\"frontier\":{\"home_refuge_radius\":0}}")));
        try{
            h.assertTrue(!ward.ignite(cell(3999)),"a partial cell must never light a free field");
            p.teleportTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5);p.setItemInHand(InteractionHand.MAIN_HAND,s);
            p.gameMode.useItemOn(p,level,s,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(pos),Direction.SOUTH,pos,false));
            h.assertTrue(ward.remaining()==2400&&ResonanceCellItem.energy(s)==4000,"lighting through a real interaction pays exactly 4000");
            h.assertTrue(!ward.ignite(s)&&ResonanceCellItem.energy(s)==4000,"active wards cannot stack or drain a second charge");
            FrontierThreat.record(p,"minecraft:overworld").putInt("seconds",30);
            for(int i=0;i<20;i++)manager.frontier().tickPlayer(p,data,true);
            h.assertTrue(FrontierThreat.attention(p,"minecraft:overworld")==30,"ward holds attention instead of clearing exposure history");
            h.assertTrue(!AssimilationProtection.protectedFromInfection(p),"a quiet camp must not grant contact immunity");
            h.assertTrue(!FieldWardBlockEntity.covers(level,pos.above(9)),"field radius is three-dimensional");
            var saved=ward.saveWithoutMetadata(level.registryAccess());var restored=new FieldWardBlockEntity(pos,level.getBlockState(pos));restored.setLevel(level);restored.loadWithComponents(saved,level.registryAccess());
            h.assertTrue(restored.remaining()==2400,"reload must retain the original paid deadline");
            saved.putLong("Until",level.getGameTime());ward.loadWithComponents(saved,level.registryAccess());FieldWardBlockEntity.tick(level,pos,level.getBlockState(pos),ward);
            h.assertTrue(!FieldWardBlockEntity.covers(level,pos),"expired wards cannot provide stale protection");
            for(int i=0;i<20;i++)manager.frontier().tickPlayer(p,data,true);
            h.assertTrue(FrontierThreat.attention(p,"minecraft:overworld")==40,"attention resumes after expiry");
        }finally{level.removeBlock(pos,false);data.resetCampaign("minecraft:overworld");ExpeditionTuning.reload(Map.of());FrontierThreat.suppressForTests(true);Stillness.suppressForTests(true);}
        h.succeed();
    }
    @GameTest(template="arena",batch="preparation_reading")
    public static void lensReadsOnlyLocalTargetsAndPaysFromCarriedCharge(GameTestHelper h){
        var p=h.makeMockServerPlayerInLevel();var compass=new ItemStack(ModItems.EXPEDITION_COMPASS.get());var s=cell(400);p.getInventory().setItem(0,s);p.getInventory().setItem(1,new ItemStack(ModItems.SURVEY_LENS.get()));
        compass.set(DataComponents.LODESTONE_TRACKER,new LodestoneTracker(Optional.of(GlobalPos.of(h.getLevel().dimension(),p.blockPosition().offset(12,8,0))),false));
        ModItems.EXPEDITION_COMPASS.get().finishUsingItem(compass,h.getLevel(),p);
        h.assertTrue(ResonanceCellItem.energy(s)==200,"a completed lens reading must cost 200 actual carried resonance");
        compass.set(DataComponents.LODESTONE_TRACKER,new LodestoneTracker(Optional.of(GlobalPos.of(net.minecraft.world.level.Level.NETHER,p.blockPosition())),false));
        ModItems.EXPEDITION_COMPASS.get().finishUsingItem(compass,h.getLevel(),p);
        h.assertTrue(ResonanceCellItem.energy(s)==200,"other-dimension targets cannot drain charge or yield a false reading");
        p.getInventory().setItem(1,ItemStack.EMPTY);compass.set(DataComponents.LODESTONE_TRACKER,new LodestoneTracker(Optional.of(GlobalPos.of(h.getLevel().dimension(),p.blockPosition())),false));
        ModItems.EXPEDITION_COMPASS.get().finishUsingItem(compass,h.getLevel(),p);
        h.assertTrue(ResonanceCellItem.energy(s)==200,"normal compass users retain free basic readings");h.succeed();
    }
    @GameTest(template="arena",batch="preparation_campaign")
    public static void shippedTrainingUsesDifferentObjectivesAndRecoveredHeartsHaveUses(GameTestHelper h)throws Exception{
        var map=new HashMap<ResourceLocation,com.google.gson.JsonElement>();
        for(String id:List.of("field_survey","trace_recovery","deep_salvage"))try(var in=FieldPreparationTests.class.getResourceAsStream("/data/expeditions/expeditions/"+id+".json")){map.put(ResourceLocation.parse("expeditions:"+id),JsonParser.parseReader(new InputStreamReader(Objects.requireNonNull(in),StandardCharsets.UTF_8)));}
        try{
            ExpeditionDefinitions.reload(map);var defs=ExpeditionDefinitions.all();var survey=defs.get("expeditions:field_survey");var recovery=defs.get("expeditions:trace_recovery");var salvage=defs.get("expeditions:deep_salvage");
            var ledger=new ExpeditionLedger();var id=UUID.randomUUID();ledger.addTokens(id,50);
            for(var key:survey.grants())ledger.grantUnlock(id,key);
            h.assertTrue(ledger.checkRequirements(id,recovery,CampaignState.FRONTIER).isSatisfied(),"Survey must open a free, distinct cargo training mission");
            h.assertTrue(!ledger.checkRequirements(id,salvage,CampaignState.ESTABLISHED).isSatisfied(),"repeating Survey cannot substitute for recovery training");
            for(var key:recovery.grants())ledger.grantUnlock(id,key);
            h.assertTrue(ledger.checkRequirements(id,salvage,CampaignState.ESTABLISHED).isSatisfied(),"two distinct successful training operations open salvage");
            h.assertTrue(recovery.objectives().stream().anyMatch(o->o.kind()==ObjectiveKind.RECOVER_ARTIFACT&&o.required()),"recovery must teach actual physical cargo");
            var manager=ExpeditionManager.get(h.getLevel().getServer());var legacy=h.makeMockServerPlayerInLevel();
            ExpeditionEventHandler.onPlayerLogin(new net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent(legacy));
            h.assertTrue(!manager.unlocks(legacy.getUUID()).contains("expeditions:recovery_clear"),"migration cannot invent training for a fresh player");
            manager.grantUnlock(legacy.getUUID(),"expeditions:salvage_clear");
            ExpeditionEventHandler.onPlayerLogin(new net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent(legacy));
            h.assertTrue(manager.unlocks(legacy.getUUID()).contains("expeditions:recovery_clear"),"existing salvage veterans must not be locked behind the new tutorial");
            for(String name:List.of("survey_lens","field_ward","living_heart_engine")){
                var recipe=h.getLevel().getRecipeManager().byKey(ResourceLocation.parse("expeditions:"+name)).orElseThrow().value();
                h.assertTrue(recipe.getIngredients().stream().flatMap(i->Arrays.stream(i.getItems())).anyMatch(s->s.is(ModItems.ASSIMILATION_HEART_ITEM.get())),"each field capability must consume a recovered specimen");
            }
        }finally{ExpeditionDefinitions.reload(Map.of());}h.succeed();
    }
    @GameTest(template="arena",batch="preparation_trace")
    public static void starterFindIsFiniteAndItsAddressSurvivesWorldSave(GameTestHelper h){
        var level=h.getLevel();var center=h.absolutePos(new BlockPos(6,1,6));var spawn=center.offset(-32,0,0);
        for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++){level.setBlockAndUpdate(center.offset(x,-1,z),Blocks.DIRT.defaultBlockState());level.setBlockAndUpdate(center.offset(x,0,z),Blocks.AIR.defaultBlockState());}
        var found=FirstTrace.findAndPlace(level,spawn);h.assertTrue(found!=null,"bounded starter search must find nearby suitable natural ground");
        var data=ExpeditionSavedData.create();data.recordFirstTrace(found);var saved=data.save(new CompoundTag(),level.registryAccess());var restored=ExpeditionSavedData.load(saved,level.registryAccess());
        h.assertTrue(restored.firstTraceAttempted()&&restored.firstTrace().orElseThrow().equals(found),"restarts must retain the find instead of reseeding starter supplies");
        h.succeed();
    }
    @GameTest(template="arena",batch="preparation_training_run",timeoutTicks=400)
    public static void twoDifferentTrainingRunsPayForTheFirstSalvage(GameTestHelper h)throws Exception{
        var definitions=new HashMap<ResourceLocation,com.google.gson.JsonElement>();
        for(String id:List.of("field_survey","trace_recovery","deep_salvage"))try(var in=FieldPreparationTests.class.getResourceAsStream("/data/expeditions/expeditions/"+id+".json")){definitions.put(ResourceLocation.parse("expeditions:"+id),JsonParser.parseReader(new InputStreamReader(Objects.requireNonNull(in),StandardCharsets.UTF_8)));}
        var manager=ExpeditionManager.get(h.getLevel().getServer());var data=manager.savedData();var player=h.makeMockServerPlayerInLevel();
        data.resetCampaign("minecraft:overworld");
        try{
            ExpeditionDefinitions.reload(definitions);
            for(String id:List.of("field_survey","trace_recovery")){
                var run=manager.startRun(player,h.absolutePos(new BlockPos(2,1,2)),new ExpeditionOptions("expeditions:"+id,com.expeditions.core.Difficulty.STANDARD,0,0));
                for(var checkpoint:run.checkpoints())manager.activateCheckpoint(player,ExpeditionManager.toPos(checkpoint));
                var done=manager.completeRun(player);
                h.assertTrue(done.status()==ExpeditionStatus.COMPLETED,"training must allow real checkpoint and cargo extraction");
                h.assertTrue(player.getInventory().items.stream().noneMatch(s->s.is(ModItems.EXPEDITION_ARTIFACT.get())),"successful recovery consumes its bound cargo, not a reusable crafting specimen");
            }
            var salvage=ExpeditionDefinitions.all().get("expeditions:deep_salvage");
            h.assertTrue(manager.missingRequirements(player.getUUID(),salvage).isSatisfied(),"distinct training completions must establish home, grant both proofs, and pay for salvage");
        }finally{
            if(manager.activeRun(player.getUUID()).isPresent())manager.failRun(player);
            data.resetCampaign("minecraft:overworld");ExpeditionDefinitions.reload(Map.of());
        }
        h.succeed();
    }
}
