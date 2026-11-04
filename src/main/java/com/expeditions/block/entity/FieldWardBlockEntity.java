package com.expeditions.block.entity;

import com.expeditions.core.FieldEquipment;
import com.expeditions.item.ResonanceCellItem;
import com.expeditions.registry.ModBlockEntities;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public final class FieldWardBlockEntity extends BlockEntity {
    private long until;
    public FieldWardBlockEntity(BlockPos p,BlockState s){super(ModBlockEntities.FIELD_WARD.get(),p,s);}
    @Override public void onLoad(){
        super.onLoad();
        if(level!=null&&!level.isClientSide&&until>level.getGameTime()+FieldEquipment.WARD_TICKS){until=level.getGameTime()+FieldEquipment.WARD_TICKS;setChanged();}
    }
    public int remaining(){return level==null?0:(int)Math.clamp(until-level.getGameTime(),0,FieldEquipment.WARD_TICKS);}
    public boolean ignite(ItemStack cell){
        if(level==null||level.isClientSide||remaining()>0||!ResonanceCellItem.spend(cell,FieldEquipment.WARD_COST))return false;
        until=level.getGameTime()+FieldEquipment.WARD_TICKS;setChanged();
        level.setBlockAndUpdate(worldPosition,getBlockState().setValue(BlockStateProperties.LIT,true));return true;
    }
    public static void tick(Level l,BlockPos p,BlockState s,FieldWardBlockEntity be){
        if(s.getValue(BlockStateProperties.LIT)&&be.remaining()==0)l.setBlockAndUpdate(p,s.setValue(BlockStateProperties.LIT,false));
    }
    /** Small loaded-chunk scan; never force-load a camp or keep it running offline. */
    public static boolean covers(ServerLevel l,BlockPos p){
        int r=FieldEquipment.WARD_RADIUS;
        for(int x=(p.getX()-r)>>4;x<=(p.getX()+r)>>4;x++)for(int z=(p.getZ()-r)>>4;z<=(p.getZ()+r)>>4;z++){
            var chunk=l.getChunkSource().getChunkNow(x,z);if(chunk==null)continue;
            for(var be:chunk.getBlockEntities().values())if(be instanceof FieldWardBlockEntity ward&&ward.remaining()>0&&ward.getBlockPos().distSqr(p)<=r*r)return true;
        }
        return false;
    }
    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider lookup){super.saveAdditional(tag,lookup);tag.putLong("Until",until);}
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider lookup){super.loadAdditional(tag,lookup);until=Math.max(0,tag.getLong("Until"));}
}
