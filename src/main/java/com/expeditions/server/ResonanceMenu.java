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
    public ResonanceMenu(int id,Inventory inv,net.minecraft.network.RegistryFriendlyByteBuf buf){this(id,inv,buf.readBlockPos(),buf.readBoolean(),new ItemStackHandler(2),new SimpleContainerData(4));}
    public ResonanceMenu(int id,Inventory inv,ResonanceMachineBlockEntity be){
        this(id,inv,be.getBlockPos(),be.dynamo(),be.inventory,new ContainerData(){
            public int get(int i){return switch(i){case 0->be.energy();case 1->be.progress();case 2->be.breaching()?1:0;default->16000;};}
            public void set(int i,int value){} public int getCount(){return 4;}
        });
    }
    private ResonanceMenu(int id,Inventory inv,BlockPos pos,boolean dynamo,IItemHandler items,ContainerData data){
        super(ModMenus.RESONANCE.get(),id);this.pos=pos;this.dynamo=dynamo;this.data=data;
        addSlot(new SlotItemHandler(items,0,53,35));addSlot(new SlotItemHandler(items,1,113,35){@Override public boolean mayPlace(ItemStack stack){return false;}});
        for(int row=0;row<3;row++)for(int col=0;col<9;col++)addSlot(new Slot(inv,col+row*9+9,8+col*18,84+row*18));
        for(int col=0;col<9;col++)addSlot(new Slot(inv,col,8+col*18,142));addDataSlots(data);
    }
    public int energy(){return data.get(0);} public int progress(){return data.get(1);} public boolean breaching(){return data.get(2)>0;}
    @Override public boolean stillValid(Player p){return p.level().getBlockState(pos).is(dynamo?ModBlocks.STILLNESS_DYNAMO.get():ModBlocks.ESSENCE_CONDENSER.get()) && p.distanceToSqr(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5)<=64;}
    @Override public ItemStack quickMoveStack(Player p,int slot){
        var current=slots.get(slot);if(!current.hasItem())return ItemStack.EMPTY;var stack=current.getItem();var original=stack.copy();
        if(slot<2){if(!moveItemStackTo(stack,2,slots.size(),true))return ItemStack.EMPTY;}
        else if(stack.is(ModItems.DORMANT_FLAKE.get())){if(!moveItemStackTo(stack,0,1,false))return ItemStack.EMPTY;}
        else return ItemStack.EMPTY;
        if(stack.isEmpty())current.set(ItemStack.EMPTY);else current.setChanged();return original;
    }
}
