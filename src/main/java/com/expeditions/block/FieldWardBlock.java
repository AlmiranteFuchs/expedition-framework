package com.expeditions.block;

import com.expeditions.block.entity.FieldWardBlockEntity;
import com.expeditions.registry.*;
import com.expeditions.item.ResonanceCellItem;
import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;

/** A finite pause in attention, not a refuge or a monster barrier. */
public final class FieldWardBlock extends Block implements EntityBlock {
    private static final VoxelShape SHAPE=Shapes.or(box(3,0,3,13,2,13),box(6,2,6,10,4,10),
            box(6,4,6,7,12,7),box(9,4,9,10,12,10),box(7,5,7,9,10,9),
            box(4,12,4,12,14,12),box(7,14,7,9,15,9));
    public FieldWardBlock(Properties p) { super(p);registerDefaultState(stateDefinition.any().setValue(BlockStateProperties.LIT,false)); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){b.add(BlockStateProperties.LIT);}
    @Override public BlockEntity newBlockEntity(BlockPos p,BlockState s){return new FieldWardBlockEntity(p,s);}
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level l,BlockState s,BlockEntityType<T> t){return l.isClientSide||t!=ModBlockEntities.FIELD_WARD.get()?null:(world,p,state,be)->FieldWardBlockEntity.tick(world,p,state,(FieldWardBlockEntity)be);}
    @Override protected VoxelShape getShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c){return SHAPE;}
    @Override protected ItemInteractionResult useItemOn(ItemStack stack,BlockState s,Level l,BlockPos p,Player player,InteractionHand hand,BlockHitResult hit){
        if(stack.is(ModItems.RESONANCE_CELL.get())) {
            if(!l.isClientSide && l.getBlockEntity(p) instanceof FieldWardBlockEntity ward) {
                boolean started=ward.ignite(stack);
                player.displayClientMessage(Component.translatable(started?"equipment.expeditions.ward_started":"equipment.expeditions.ward_refused"),true);
                player.containerMenu.broadcastChanges();
            }
            return ItemInteractionResult.sidedSuccess(l.isClientSide);
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }
    @Override protected InteractionResult useWithoutItem(BlockState s,Level l,BlockPos p,Player player,BlockHitResult hit){
        if(!l.isClientSide && l.getBlockEntity(p) instanceof FieldWardBlockEntity ward) player.displayClientMessage(Component.translatable("equipment.expeditions.ward_status",(ward.remaining()+19)/20),true);
        return InteractionResult.sidedSuccess(l.isClientSide);
    }
    @Override public void animateTick(BlockState s,Level l,BlockPos p,net.minecraft.util.RandomSource r){
        if(s.getValue(BlockStateProperties.LIT)&&r.nextInt(3)==0)l.addParticle(net.minecraft.core.particles.ParticleTypes.WHITE_ASH,p.getX()+.5,p.getY()+.85,p.getZ()+.5,0,.01,0);
    }
}
