package com.expeditions.server;
import com.expeditions.block.entity.ResonanceMachineBlockEntity;
import com.expeditions.registry.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.*;
public final class ResonanceMenu extends AbstractContainerMenu {
    private final BlockPos pos;private final ContainerData data;
    public final boolean dynamo;
    private final int machineSlots;
    public ResonanceMenu(int id,Inventory inv,net.minecraft.network.RegistryFriendlyByteBuf buf){this(id,inv,buf.readBlockPos(),buf.readBoolean(),new ItemStackHandler(2),new SimpleContainerData(9));}
    public ResonanceMenu(int id,Inventory inv,ResonanceMachineBlockEntity be){
        this(id,inv,be.getBlockPos(),be.dynamo(),be.inventory,new ContainerData(){
            public int get(int i){return switch(i){case 0->be.energy();case 1->be.progress();case 2->be.breaching()?1:0;case 4->be.kind().ordinal();case 5->be.generationRate();case 6->be.status().ordinal();case 7->be.collectors();case 8->be.actualGeneration();default->16000;};}
            public void set(int i,int value){} public int getCount(){return 9;}
        });
    }
    private ResonanceMenu(int id,Inventory inv,BlockPos pos,boolean dynamo,IItemHandler items,ContainerData data){
        super(ModMenus.RESONANCE.get(),id);this.pos=pos;this.dynamo=dynamo;this.data=data;machineSlots=dynamo?1:2;
        addSlot(new SlotItemHandler(items,0,53,35));if(!dynamo)addSlot(new SlotItemHandler(items,1,113,35){@Override public boolean mayPlace(ItemStack stack){return false;}});
        for(int row=0;row<3;row++)for(int col=0;col<9;col++)addSlot(new Slot(inv,col+row*9+9,8+col*18,84+row*18));
        for(int col=0;col<9;col++)addSlot(new Slot(inv,col,8+col*18,142));addDataSlots(data);
    }
    public int sourceKind(){return data.get(4);}
    public com.expeditions.core.ResonanceStatus status(){return com.expeditions.core.ResonanceStatus.byId(data.get(6));}
    public int collectors(){return data.get(7);}
    public int actualGeneration(){return data.get(8);}
    public int comparatorSignal(){
        if(dynamo)return com.expeditions.core.ResonanceStatus.fillSignal(energy(),16000);
        var output=getSlot(1).getItem();return output.isEmpty()?0:com.expeditions.core.ResonanceStatus.fillSignal(output.getCount(),output.getMaxStackSize());
    }
    public int generationRate(){return data.get(5);}
    public int energy(){return data.get(0);} public int progress(){return data.get(1);} public boolean breaching(){return data.get(2)>0;}
    @Override public boolean stillValid(Player p){return p.level().getBlockState(pos).getBlock() instanceof com.expeditions.block.ResonanceMachineBlock && p.distanceToSqr(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5)<=64;}
    @Override public ItemStack quickMoveStack(Player p,int slot){
        var current=slots.get(slot);if(!current.hasItem())return ItemStack.EMPTY;var stack=current.getItem();var original=stack.copy();
        if(slot<machineSlots){if(!moveItemStackTo(stack,machineSlots,slots.size(),true))return ItemStack.EMPTY;}
        else if(slots.getFirst().mayPlace(stack)){if(!moveItemStackTo(stack,0,1,false))return ItemStack.EMPTY;}
        else return ItemStack.EMPTY;
        if(stack.isEmpty())current.set(ItemStack.EMPTY);else current.setChanged();return original;
    }
}
