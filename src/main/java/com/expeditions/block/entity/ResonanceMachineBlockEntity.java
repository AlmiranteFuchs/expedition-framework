package com.expeditions.block.entity;

import com.expeditions.core.ResonanceProcessing;
import com.expeditions.core.ResonancePower;
import com.expeditions.core.ResonanceStatus;
import com.expeditions.ExpeditionsConfig;
import com.expeditions.core.ResonancePower.Kind;
import com.expeditions.item.SeveranceCoreItem;
import com.expeditions.server.Stillness;
import com.expeditions.registry.*;
import com.expeditions.server.ExpeditionEventHandler;
import com.expeditions.server.ExpeditionManager;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.*;
import net.neoforged.neoforge.energy.*;
import net.neoforged.neoforge.items.*;

public final class ResonanceMachineBlockEntity extends BlockEntity {
    public final ItemStackHandler inventory=new ItemStackHandler(2) {
        @Override public boolean isItemValid(int slot,ItemStack stack) { return slot==0 && (!dynamo()||!ExpeditionsConfig.simpleGenerators()) && switch(kind()){
            case DORMANT->false;case LIVING->stack.is(ModItems.ASSIMILATION_ESSENCE.get());
            case SEVERED->stack.is(ModItems.SEVERANCE_CORE.get());case CONDENSER->stack.is(ModItems.DORMANT_FLAKE.get());}; }
        @Override protected void onContentsChanged(int slot) { setChanged();updateComparator(); }
    };
    private int fuelTicks,progress,generationRate,fieldCount=1;
    private long nextWorkSound;
    private long lastFieldScan=Long.MIN_VALUE;
    private boolean reserved,breach;
    private ResonanceStatus status=ResonanceStatus.NO_SAMPLE;
    private int actualGeneration,lastComparator=-1,activityTail;
    public ResonanceStatus status(){return status;}
    public int actualGeneration(){return actualGeneration;}
    public int collectors(){return fieldCount;}
    public int comparatorSignal(){
        if(dynamo())return ResonanceStatus.fillSignal(energy(),ResonanceProcessing.CAPACITY);
        var output=inventory.getStackInSlot(1);return output.isEmpty()?0:ResonanceStatus.fillSignal(output.getCount(),output.getMaxStackSize());
    }
    private void updateComparator(){
        if(level==null||level.isClientSide)return;
        int signal=comparatorSignal();
        if(signal!=lastComparator){lastComparator=signal;level.updateNeighbourForOutputSignal(worldPosition,getBlockState().getBlock());}
    }
    private final Bank bank=new Bank();
    private final IItemHandler automation=new IItemHandler() {
        public int getSlots(){return 2;}
        public ItemStack getStackInSlot(int slot){return inventory.getStackInSlot(slot);}
        public ItemStack insertItem(int slot,ItemStack stack,boolean simulate){return slot==0?inventory.insertItem(slot,stack,simulate):stack;}
        public ItemStack extractItem(int slot,int amount,boolean simulate){return slot==1?inventory.extractItem(slot,amount,simulate):ItemStack.EMPTY;}
        public int getSlotLimit(int slot){return inventory.getSlotLimit(slot);}
        public boolean isItemValid(int slot,ItemStack stack){return inventory.isItemValid(slot,stack);}
    };
    public ResonanceMachineBlockEntity(BlockPos pos,BlockState state){super(ModBlockEntities.RESONANCE_MACHINE.get(),pos,state);}
    public Kind kind(){
        var state=getBlockState();
        if(state.is(ModBlocks.STILLNESS_DYNAMO.get()))return Kind.DORMANT;
        if(state.is(ModBlocks.LIVING_HEART_ENGINE.get()))return Kind.LIVING;
        if(state.is(ModBlocks.SEVERANCE_GENERATOR.get()))return Kind.SEVERED;
        return Kind.CONDENSER;
    }
    public boolean dynamo(){return kind()!=Kind.CONDENSER;}
    public int generationRate(){return generationRate;}
    public String modelName(){return switch(kind()){case DORMANT->"stillness_dynamo";case LIVING->"living_heart_engine";case SEVERED->"severance_generator";case CONDENSER->"essence_condenser";};}
    private int fieldCount(ServerLevel level){
        if(lastFieldScan==Long.MIN_VALUE || level.getGameTime()-lastFieldScan>=20){
            int count=0;
            for(int x=(worldPosition.getX()-6)>>4;x<=(worldPosition.getX()+6)>>4;x++)
                for(int z=(worldPosition.getZ()-6)>>4;z<=(worldPosition.getZ()+6)>>4;z++){
                    var chunk=level.getChunkSource().getChunkNow(x,z);if(chunk==null)continue;
                    for(var other:chunk.getBlockEntities().values())if(other instanceof ResonanceMachineBlockEntity be
                            && be.kind()==Kind.DORMANT && be.getBlockPos().distSqr(worldPosition)<=36)count++;
                }
            fieldCount=Math.max(1,count);lastFieldScan=level.getGameTime();
        }
        return fieldCount;
    }
    public int energy(){return bank.getEnergyStored();}
    public int progress(){return dynamo()?fuelTicks:progress;}
    public boolean breaching(){return breach && energy()>=20 && !dynamo();}
    public static void registerCapabilities(RegisterCapabilitiesEvent event){
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK,ModBlockEntities.RESONANCE_MACHINE.get(),(be,side)->be.bank);
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK,ModBlockEntities.RESONANCE_MACHINE.get(),(be,side)->be.automation);
    }
    private final class Bank extends EnergyStorage {
        Bank(){super(ResonanceProcessing.CAPACITY,80,256);}
        @Override public boolean canReceive(){return !dynamo();}
        @Override public boolean canExtract(){return dynamo();}
        @Override public int receiveEnergy(int amount,boolean simulate){int n=amount>0 && canReceive()?super.receiveEnergy(amount,simulate):0;if(n>0&&!simulate){setChanged();updateComparator();}return n;}
        @Override public int extractEnergy(int amount,boolean simulate){int n=amount>0 && canExtract()?super.extractEnergy(amount,simulate):0;if(n>0&&!simulate){setChanged();updateComparator();}return n;}
        void add(int amount){energy=ResonanceProcessing.restoredEnergy(energy+amount);setChanged();}
        void spend(int amount){energy=Math.max(0,energy-amount);setChanged();}
        void restore(int value){energy=ResonanceProcessing.restoredEnergy(value);}
    }
    public boolean toggleBreach(){
        if(!(level instanceof ServerLevel server) || dynamo())return false;
        var data=ExpeditionManager.get(server.getServer()).savedData();
        if(!breach && (energy()<4000 || !server.dimension().equals(net.minecraft.world.level.Level.OVERWORLD)
                || data.isDimensionCleared(server.dimension().location().toString())))return false;
        if(!data.setStillnessBreach(worldPosition,!breach))return false;
        breach=!breach;setChanged();return true;
    }
    @Override public void onLoad(){super.onLoad();if(level instanceof ServerLevel server && breach)
        ExpeditionManager.get(server.getServer()).savedData().setStillnessBreach(worldPosition,true);}
    /**
     * Membrane reaches machinery: a colony face gripping any side of the
     * casing smothers the machine outright — generation, refining and a
     * running breach all stop until the coating is broken off. Stored
     * charge still drains out; the choke silences the source, not the
     * wires. This is the running cost of an unwarded colony: it can eat
     * the farm that hosts it.
     */
    private static boolean choked(ServerLevel level,BlockPos pos){
        for(Direction d:Direction.values()){
            var coated=level.getBlockState(pos.relative(d));
            if(coated.getBlock() instanceof com.expeditions.block.AssimilationLayerBlock
                    && net.minecraft.world.level.block.MultifaceBlock.hasFace(coated,d.getOpposite()))return true;
        }
        return false;
    }
    public static void tick(ServerLevel level,BlockPos pos,BlockState state,ResonanceMachineBlockEntity be){
        be.actualGeneration=0;boolean working=false,quiet=true,proven=false;
        boolean paused=level.hasNeighborSignal(pos);
        boolean simple=ExpeditionsConfig.simpleGenerators();
        boolean smothered=choked(level,pos);
        if(smothered&&be.breach){be.breach=false;ExpeditionManager.get(level.getServer()).savedData().setStillnessBreach(pos,false);be.setChanged();}
        if(simple&&be.dynamo()){be.fieldCount=1;be.lastFieldScan=Long.MIN_VALUE;}
        if(be.breach){
            if(be.energy()<20){be.breach=false;ExpeditionManager.get(level.getServer()).savedData().setStillnessBreach(pos,false);be.setChanged();}
            else {be.bank.spend(20);working=true;}
        } else if(be.dynamo()){
            if(!simple && be.kind()==Kind.LIVING && be.fuelTicks==0 && be.energy()<ResonanceProcessing.CAPACITY
                    && !paused && be.inventory.getStackInSlot(0).is(ModItems.ASSIMILATION_ESSENCE.get())){
                be.inventory.extractItem(0,1,false);be.fuelTicks=ResonancePower.LIVING_FUEL_TICKS;
                level.playSound(null,pos,ModSounds.RESONANCE_FUEL.get(),SoundSource.BLOCKS,.25f,1.2f);
            }
            quiet=simple||Stillness.suppresses(level,pos) || ExpeditionManager.get(level.getServer()).savedData().isDimensionCleared(level.dimension().location().toString());
            proven=!simple&&be.kind()==Kind.SEVERED&&SeveranceCoreItem.valid(level,be.inventory.getStackInSlot(0));
            be.generationRate=paused||smothered?0:ResonancePower.output(be.kind(),quiet,
                    !simple&&be.kind()==Kind.DORMANT?be.fieldCount(level):1,be.fuelTicks>0,proven,simple);
            int made=Math.min(be.generationRate,ResonanceProcessing.CAPACITY-be.energy());
            if(made>0){be.bank.add(made);be.actualGeneration=made;working=true;if(!simple&&be.kind()==Kind.LIVING)be.fuelTicks--;}
        } else if(!smothered){
            var output=be.inventory.getStackInSlot(1);
            boolean room=output.isEmpty() || output.is(ModItems.ASSIMILATION_ESSENCE.get()) && output.getCount()<output.getMaxStackSize();
            if(room && be.energy()>=20){
                if(!be.reserved && !be.inventory.getStackInSlot(0).isEmpty()){
                    be.inventory.extractItem(0,1,false);be.reserved=true;be.progress=0;
                }
                if(be.reserved){
                    working=true;
                    be.bank.spend(20);be.progress++;
                    if(be.progress>=ResonanceProcessing.PROCESS_TICKS){
                        var next=output.isEmpty()?new ItemStack(ModItems.ASSIMILATION_ESSENCE.get()):output.copyWithCount(output.getCount()+1);
                        be.inventory.setStackInSlot(1,next);be.reserved=false;be.progress=0;
                        level.playSound(null,pos,ModSounds.RESONANCE_COMPLETE.get(),SoundSource.BLOCKS,.45f,1.2f);
                        ExpeditionEventHandler.bloomWork(level,pos);
                    }
                }
            }
        }
        if (working && !be.breach && level.getGameTime() >= be.nextWorkSound) {
            var sound = switch (be.kind()) {
                case DORMANT -> ModSounds.RESONANCE_WORK_DORMANT.get();
                case LIVING -> ModSounds.RESONANCE_WORK_LIVING.get();
                case SEVERED -> ModSounds.RESONANCE_WORK_SEVERED.get();
                case CONDENSER -> ModSounds.RESONANCE_WORK_CONDENSER.get();
            };
            level.playSound(null, pos, sound, SoundSource.BLOCKS, be.kind() == Kind.SEVERED ? 0.65f : 0.32f,
                    be.dynamo() ? 0.75f : 0.9f);
            be.nextWorkSound = level.getGameTime() + 34 + level.random.nextInt(13);
        }
        if(be.dynamo() && be.energy()>0){
            int budget=Math.min(160,be.energy());
            var targets=new java.util.ArrayList<IEnergyStorage>(6);
            for(Direction side:Direction.values()){
                var target=level.getCapability(Capabilities.EnergyStorage.BLOCK,pos.relative(side),side.getOpposite());
                if(target!=null && target.canReceive() && target.receiveEnergy(budget,true)>0)targets.add(target);
            }
            // Divide stored output across accepting neighbors; one empty bank must not starve every other face.
            int offset=targets.isEmpty()?0:Math.floorMod(level.getGameTime()+pos.asLong(),targets.size());
            for(int i=0;i<targets.size() && budget>0;i++){
                int share=(budget+targets.size()-i-1)/(targets.size()-i);
                int moved=targets.get((offset+i)%targets.size()).receiveEnergy(Math.min(share,be.energy()),false);
                if(moved>0)be.bank.spend(moved);budget-=moved;
            }
        }
        var output=be.inventory.getStackInSlot(1);
        boolean outputRoom=output.isEmpty()||output.is(ModItems.ASSIMILATION_ESSENCE.get())&&output.getCount()<output.getMaxStackSize();
        be.status=be.dynamo()?ResonanceStatus.source(be.kind(),be.energy(),paused,quiet,be.fuelTicks>0||be.actualGeneration>0,
                be.inventory.getStackInSlot(0).is(ModItems.SEVERANCE_CORE.get()),proven,be.fieldCount)
                :ResonanceStatus.condenser(be.energy(),be.reserved,be.inventory.getStackInSlot(0).is(ModItems.DORMANT_FLAKE.get()),outputRoom,be.breaching());
        if(!be.dynamo()) {
            // Keep the readout steady between adjacent paid steps on a slow
            // supply, but settle promptly after a genuine stall or backpressure.
            if(be.breach||!be.reserved||!outputRoom)be.activityTail=0;
            else if(working)be.activityTail=3;
            else be.activityTail=Math.max(0,be.activityTail-1);
            if(be.activityTail>0)be.status=ResonanceStatus.REFINING;
        }
        if(simple&&be.dynamo())be.status=paused?ResonanceStatus.REDSTONE_PAUSED:be.energy()>=ResonanceProcessing.CAPACITY
                ?ResonanceStatus.BUFFER_FULL:ResonanceStatus.SIMPLE_GATHERING;
        if(smothered)be.status=ResonanceStatus.CHOKED;
        boolean lit=working||!be.dynamo()&&be.activityTail>0;
        boolean core=simple&&be.dynamo() || be.kind()==Kind.DORMANT || be.kind()==Kind.LIVING || be.inventory.getStackInSlot(0).is(ModItems.SEVERANCE_CORE.get());
        if(state.getValue(com.expeditions.block.ResonanceMachineBlock.ACTIVE)!=lit || state.getValue(com.expeditions.block.ResonanceMachineBlock.HAS_CORE)!=core)
            level.setBlock(pos,state.setValue(com.expeditions.block.ResonanceMachineBlock.ACTIVE,lit).setValue(com.expeditions.block.ResonanceMachineBlock.HAS_CORE,core),3);
        be.updateComparator();
    }
    public void dropContents(){
        if(level==null || level.isClientSide)return;
        for(int slot=0;slot<2;slot++)net.minecraft.world.level.block.Block.popResource(level,worldPosition,inventory.getStackInSlot(slot));
        if(reserved)net.minecraft.world.level.block.Block.popResource(level,worldPosition,new ItemStack(ModItems.DORMANT_FLAKE.get()));
        inventory.setStackInSlot(0,ItemStack.EMPTY);inventory.setStackInSlot(1,ItemStack.EMPTY);reserved=false;
        ExpeditionManager.get(level.getServer()).savedData().setStillnessBreach(worldPosition,false);
    }
    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider registries){
        super.saveAdditional(tag,registries);tag.put("Inventory",inventory.serializeNBT(registries));tag.putInt("Energy",energy());
        tag.putInt("Fuel",fuelTicks);tag.putInt("Progress",progress);tag.putBoolean("Reserved",reserved);tag.putBoolean("Breach",breach);
    }
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider registries){
        super.loadAdditional(tag,registries);inventory.deserializeNBT(registries,tag.getCompound("Inventory"));bank.restore(tag.getInt("Energy"));
        fuelTicks=kind()==Kind.LIVING?Math.clamp(tag.getInt("Fuel"),0,ResonancePower.LIVING_FUEL_TICKS):0;reserved=!dynamo()&&tag.getBoolean("Reserved");
        progress=ResonanceProcessing.restoredProgress(tag.getInt("Progress"),reserved);breach=!dynamo()&&tag.getBoolean("Breach");
    }
}
