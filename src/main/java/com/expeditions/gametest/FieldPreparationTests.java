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
        net.neoforged.neoforge.network.registration.NetworkRegistry.configureMockConnection(p.connection.getConnection());
        level.setBlockAndUpdate(pos,ModBlocks.ESSENCE_CONDENSER.get().defaultBlockState());
        var machine=(ResonanceMachineBlockEntity)level.getBlockEntity(pos);var s=cell(4000);p.setItemInHand(InteractionHand.MAIN_HAND,s);
        var storage=s.getCapability(Capabilities.EnergyStorage.ITEM);
        h.assertTrue(storage!=null&&storage.extractEnergy(500,true)==500&&ResonanceCellItem.energy(s)==4000,"FE simulation must leave the actual cell untouched");
        p.teleportTo(pos.getX()+.5,pos.getY(),pos.getZ()+2.5);
        var hit=new BlockHitResult(Vec3.atCenterOf(pos),Direction.SOUTH,pos,false);
        p.gameMode.useItemOn(p,level,s,InteractionHand.MAIN_HAND,hit);
        h.assertTrue(p.containerMenu instanceof ResonanceMenu&&machine.energy()==0&&ResonanceCellItem.energy(s)==4000,"holding a cell opens the machine without transferring carried charge");
        var menu=(ResonanceMenu)p.containerMenu;
        int playerSlot=-1;
        for(int i=0;i<menu.slots.size();i++)if(menu.getSlot(i).container==p.getInventory()&&menu.getSlot(i).getItem()==s)playerSlot=i;
        h.assertTrue(playerSlot>=0,"held cell must be visible in the inventory");
        menu.quickMoveStack(p,playerSlot);
        h.assertTrue(machine.inventory.getStackInSlot(2).is(ModItems.RESONANCE_CELL.get())&&machine.inventory.getStackInSlot(0).isEmpty(),"shift-click routes cells into the dock, never the sample slot");
        ResonanceMachineBlockEntity.tick(level,pos,level.getBlockState(pos),machine);
        var docked=machine.inventory.getStackInSlot(2);
        h.assertTrue(machine.energy()==80&&ResonanceCellItem.energy(docked)==3920,"docked cells transfer only the receiver's actual accepted energy");
        var restored=new ResonanceMachineBlockEntity(pos,level.getBlockState(pos));restored.setLevel(level);
        restored.loadWithComponents(machine.saveWithoutMetadata(level.registryAccess()),level.registryAccess());
        h.assertTrue(restored.inventory.getSlots()==3&&restored.energy()==80&&ResonanceCellItem.energy(restored.inventory.getStackInSlot(2))==3920,"machine save/load preserves docked charge and the receiver bank together");
        menu.quickMoveStack(p,menu.cellSlot());
        h.assertTrue(machine.inventory.getStackInSlot(2).isEmpty()&&p.getInventory().items.stream().anyMatch(item->item.is(ModItems.RESONANCE_CELL.get())&&ResonanceCellItem.energy(item)==3920),"removing a cell retains its remaining charge");
        p.closeContainer();
        var source=pos.offset(3,0,0);level.setBlockAndUpdate(source,ModBlocks.STILLNESS_DYNAMO.get().defaultBlockState());
        level.setBlockAndUpdate(source.above(),Blocks.REDSTONE_BLOCK.defaultBlockState());
        var bank=(ResonanceMachineBlockEntity)level.getBlockEntity(source);var tag=bank.saveWithoutMetadata(level.registryAccess());tag.putInt("Energy",1000);bank.loadWithComponents(tag,level.registryAccess());
        p.teleportTo(source.getX()+.5,source.getY(),source.getZ()+2.5);p.setShiftKeyDown(true);p.setItemInHand(InteractionHand.MAIN_HAND,cell(0));
        p.gameMode.useItemOn(p,level,p.getMainHandItem(),InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(source),Direction.SOUTH,source,false));
        h.assertTrue(p.containerMenu instanceof ResonanceMenu&&bank.energy()==1000,"sneak-use also opens the dock instead of draining the generator");
        bank.inventory.setStackInSlot(2,cell(0));
        ResonanceMachineBlockEntity.tick(level,source,level.getBlockState(source),bank);
        h.assertTrue(bank.energy()==744&&ResonanceCellItem.energy(bank.inventory.getStackInSlot(2))==256,"generator dock conserves stored charge even while generation is redstone-paused");
        bank.inventory.setStackInSlot(2,cell(16000));
        ResonanceMachineBlockEntity.tick(level,source,level.getBlockState(source),bank);
        h.assertTrue(bank.energy()==744&&ResonanceCellItem.energy(bank.inventory.getStackInSlot(2))==16000,"a full cell never wastes generator charge");
        p.closeContainer();level.removeBlock(source.above(),false);level.removeBlock(source,false);level.removeBlock(pos,false);h.succeed();
    }
    @GameTest(template="arena",batch="preparation_cell_save")
    public static void legacyMachineInventoryAndBrokenDockPreserveContents(GameTestHelper h){
        var level=h.getLevel();var pos=h.absolutePos(new BlockPos(5,1,5));
        level.setBlockAndUpdate(pos,ModBlocks.ESSENCE_CONDENSER.get().defaultBlockState());
        var machine=(ResonanceMachineBlockEntity)level.getBlockEntity(pos);
        var old=new net.neoforged.neoforge.items.ItemStackHandler(2);
        old.setStackInSlot(0,new ItemStack(ModItems.DORMANT_FLAKE.get(),3));old.setStackInSlot(1,new ItemStack(ModItems.ASSIMILATION_ESSENCE.get(),2));
        var tag=new CompoundTag();tag.put("Inventory",old.serializeNBT(level.registryAccess()));tag.putInt("Energy",16000);
        machine.loadWithComponents(tag,level.registryAccess());
        h.assertTrue(machine.inventory.getSlots()==3&&machine.inventory.getStackInSlot(0).getCount()==3&&machine.inventory.getStackInSlot(1).getCount()==2&&machine.inventory.getStackInSlot(2).isEmpty(),"old two-slot saves preserve input/output and acquire an empty dock");
        machine.inventory.setStackInSlot(2,cell(4000));
        // Full bank and blocked output make this a pure conservation check.
        machine.inventory.setStackInSlot(1,new ItemStack(ModItems.ASSIMILATION_ESSENCE.get(),64));
        ResonanceMachineBlockEntity.tick(level,pos,level.getBlockState(pos),machine);
        h.assertTrue(machine.energy()==16000&&ResonanceCellItem.energy(machine.inventory.getStackInSlot(2))==4000,"a full receiver never drains a docked cell");
        h.assertTrue(machine.inventory.getSlotLimit(2)==1&&!machine.inventory.isItemValid(2,new ItemStack(Items.STICK)),"dock accepts exactly one cell");
        level.removeBlock(pos,false);machine.dropContents();
        var drops=level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new AABB(pos).inflate(2),entity->entity.getItem().is(ModItems.RESONANCE_CELL.get()));
        h.assertTrue(drops.stream().mapToInt(entity->entity.getItem().getCount()).sum()==1&&ResonanceCellItem.energy(drops.getFirst().getItem())==4000,"breaking the machine drops its charged cell exactly once");
        for(var drop:drops)drop.discard();h.succeed();
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
