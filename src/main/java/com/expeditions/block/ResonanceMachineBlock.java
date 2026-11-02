package com.expeditions.block;
import com.expeditions.block.entity.ResonanceMachineBlockEntity;
import com.expeditions.registry.*;
import com.expeditions.server.ResonanceMenu;
import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.BlockHitResult;
public final class ResonanceMachineBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final BooleanProperty ACTIVE=BlockStateProperties.LIT;
    public static final BooleanProperty HAS_CORE=BooleanProperty.create("has_core");
    public ResonanceMachineBlock(Properties p){super(p);registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH).setValue(ACTIVE,false).setValue(HAS_CORE,false));}
    @Override protected com.mojang.serialization.MapCodec<? extends HorizontalDirectionalBlock> codec(){return simpleCodec(ResonanceMachineBlock::new);}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){b.add(FACING,ACTIVE,HAS_CORE);}
    @Override public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext c){return defaultBlockState().setValue(FACING,c.getHorizontalDirection().getOpposite());}
    @Override public BlockEntity newBlockEntity(BlockPos p,BlockState s){return new ResonanceMachineBlockEntity(p,s);}
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level l,BlockState s,BlockEntityType<T> t){
        return l.isClientSide||t!=ModBlockEntities.RESONANCE_MACHINE.get()?null:(world,p,state,be)->ResonanceMachineBlockEntity.tick((net.minecraft.server.level.ServerLevel)world,p,state,(ResonanceMachineBlockEntity)be);
    }
    @Override protected ItemInteractionResult useItemOn(ItemStack stack,BlockState state,Level level,BlockPos p,Player player,InteractionHand hand,BlockHitResult hit){
        if(stack.is(ModItems.ATTUNEMENT_SIGIL.get()) && player.isShiftKeyDown()){
            if(level instanceof net.minecraft.server.level.ServerLevel && level.getBlockEntity(p) instanceof ResonanceMachineBlockEntity be){
                boolean changed=be.toggleBreach();player.displayClientMessage(Component.translatable(changed?"machine.expeditions.breach_changed":"machine.expeditions.breach_refused"),true);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        open(level,p,player);return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override protected InteractionResult useWithoutItem(BlockState s,Level l,BlockPos p,Player player,BlockHitResult h){open(l,p,player);return InteractionResult.sidedSuccess(l.isClientSide);}
    private void open(Level l,BlockPos p,Player player){
        if(player instanceof ServerPlayer server && l.getBlockEntity(p) instanceof ResonanceMachineBlockEntity be)
            server.openMenu(new SimpleMenuProvider((id,inv,who)->new ResonanceMenu(id,inv,be),getName()),buf->{buf.writeBlockPos(p);buf.writeBoolean(be.dynamo());});
    }
    @Override protected void onRemove(BlockState old,Level l,BlockPos p,BlockState next,boolean moving){
        if(old.getBlock()!=next.getBlock() && l.getBlockEntity(p) instanceof ResonanceMachineBlockEntity be)be.dropContents();
        super.onRemove(old,l,p,next,moving);
    }    @Override protected net.minecraft.world.phys.shapes.VoxelShape getShape(BlockState state,net.minecraft.world.level.BlockGetter level,BlockPos pos,net.minecraft.world.phys.shapes.CollisionContext context){
        return FieldMachineShapes.shape(state.is(ModBlocks.STILLNESS_DYNAMO.get())?"stillness_dynamo":state.is(ModBlocks.LIVING_HEART_ENGINE.get())?"living_heart_engine":state.is(ModBlocks.SEVERANCE_GENERATOR.get())?"severance_generator":"essence_condenser",state.getValue(FACING),state.getValue(HAS_CORE));
    }

}
