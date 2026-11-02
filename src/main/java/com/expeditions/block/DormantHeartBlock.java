package com.expeditions.block;
import com.expeditions.registry.*;
import com.expeditions.server.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
public final class DormantHeartBlock extends Block {
    public DormantHeartBlock(Properties p) { super(p); }
    @Override public void playerDestroy(Level l,Player p,BlockPos pos,BlockState s,BlockEntity be,ItemStack tool) {
        super.playerDestroy(l,p,pos,s,be,tool);
        if(!l.isClientSide)popResource(l,pos,new ItemStack(ModItems.DORMANT_FLAKE.get(),4));
    }
    @Override protected ItemInteractionResult useItemOn(ItemStack stack,BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit) {
        if(!stack.is(ModItems.ASSIMILATION_ESSENCE.get()))return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if(level instanceof net.minecraft.server.level.ServerLevel server && Microbiome.enabledAt(server,pos)) {
            level.setBlockAndUpdate(pos,ModBlocks.ASSIMILATION_HEART.get().defaultBlockState());
            if(!player.isCreative())stack.shrink(1);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }
}
