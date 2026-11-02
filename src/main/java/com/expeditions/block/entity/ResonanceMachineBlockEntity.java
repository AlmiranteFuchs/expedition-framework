package com.expeditions.block.entity;

import com.expeditions.core.ResonanceProcessing;
import com.expeditions.registry.*;
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
        @Override public boolean isItemValid(int slot,ItemStack stack) { return slot==0 && stack.is(ModItems.DORMANT_FLAKE.get()); }
        @Override protected void onContentsChanged(int slot) { setChanged(); }
    };
    private int fuelTicks,progress;
    private boolean reserved,breach;
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
    public boolean dynamo(){return getBlockState().is(ModBlocks.STILLNESS_DYNAMO.get());}
    public int energy(){return bank.getEnergyStored();}
    public int progress(){return dynamo()?fuelTicks:progress;}
    public boolean breaching(){return breach && energy()>=20 && !dynamo();}
    public static void registerCapabilities(RegisterCapabilitiesEvent event){
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK,ModBlockEntities.RESONANCE_MACHINE.get(),(be,side)->be.bank);
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK,ModBlockEntities.RESONANCE_MACHINE.get(),(be,side)->be.automation);
    }
    private final class Bank extends EnergyStorage {
        Bank(){super(ResonanceProcessing.CAPACITY,80,80);}
        @Override public boolean canReceive(){return !dynamo();}
        @Override public boolean canExtract(){return dynamo();}
        @Override public int receiveEnergy(int amount,boolean simulate){int n=amount>0 && canReceive()?super.receiveEnergy(amount,simulate):0;if(n>0&&!simulate)setChanged();return n;}
        @Override public int extractEnergy(int amount,boolean simulate){int n=amount>0 && canExtract()?super.extractEnergy(amount,simulate):0;if(n>0&&!simulate)setChanged();return n;}
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
    public static void tick(ServerLevel level,BlockPos pos,BlockState state,ResonanceMachineBlockEntity be){
        if(be.breach){
            if(be.energy()<20){be.breach=false;ExpeditionManager.get(level.getServer()).savedData().setStillnessBreach(pos,false);be.setChanged();}
            else be.bank.spend(20);
        } else if(be.dynamo()){
            if(be.fuelTicks==0 && be.energy()<=ResonanceProcessing.CAPACITY-4000 && !be.inventory.getStackInSlot(0).isEmpty()){
                be.inventory.extractItem(0,1,false);be.fuelTicks=100;
                level.playSound(null,pos,SoundEvents.COPPER_BULB_TURN_ON,SoundSource.BLOCKS,.35f,.8f);
            }
            if(be.fuelTicks>0 && be.energy()<=ResonanceProcessing.CAPACITY-40){be.bank.add(40);be.fuelTicks--;}
        } else {
            var output=be.inventory.getStackInSlot(1);
            boolean room=output.isEmpty() || output.is(ModItems.ASSIMILATION_ESSENCE.get()) && output.getCount()<output.getMaxStackSize();
            if(room && be.energy()>=20){
                if(!be.reserved && !be.inventory.getStackInSlot(0).isEmpty()){
                    be.inventory.extractItem(0,1,false);be.reserved=true;be.progress=0;
                }
                if(be.reserved){
                    be.bank.spend(20);be.progress++;
                    if(be.progress>=ResonanceProcessing.PROCESS_TICKS){
                        var next=output.isEmpty()?new ItemStack(ModItems.ASSIMILATION_ESSENCE.get()):output.copyWithCount(output.getCount()+1);
                        be.inventory.setStackInSlot(1,next);be.reserved=false;be.progress=0;
                        level.playSound(null,pos,SoundEvents.AMETHYST_BLOCK_CHIME,SoundSource.BLOCKS,.45f,1.2f);
                    }
                }
            }
        }
        if(be.dynamo() && be.energy()>0){
            int budget=Math.min(80,be.energy());
            for(Direction side:Direction.values()){
                var target=level.getCapability(Capabilities.EnergyStorage.BLOCK,pos.relative(side),side.getOpposite());
                if(target==null || !target.canReceive())continue;
                int moved=target.receiveEnergy(Math.min(budget,be.energy()),false);
                be.bank.spend(moved);budget-=moved;if(budget<=0)break;
            }
        }
        boolean lit=be.breach || be.fuelTicks>0 || be.reserved && be.energy()>0;
        if(state.getValue(com.expeditions.block.ResonanceMachineBlock.ACTIVE)!=lit)
            level.setBlock(pos,state.setValue(com.expeditions.block.ResonanceMachineBlock.ACTIVE,lit),3);
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
        fuelTicks=dynamo()?Math.clamp(tag.getInt("Fuel"),0,100):0;reserved=!dynamo()&&tag.getBoolean("Reserved");
        progress=ResonanceProcessing.restoredProgress(tag.getInt("Progress"),reserved);breach=!dynamo()&&tag.getBoolean("Breach");
    }
}
