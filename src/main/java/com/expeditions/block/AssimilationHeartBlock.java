package com.expeditions.block;

import com.expeditions.block.entity.AssimilationHeartBlockEntity;
import com.expeditions.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** The Shutter Bloom: a folded woody colony source. Registry identity is retained for old worlds. */
public class AssimilationHeartBlock extends BloomSourceBlock implements EntityBlock {
    public AssimilationHeartBlock(Properties properties) {
        super(properties);
    }

    @Override protected void onRemove(BlockState old,Level level,BlockPos pos,BlockState next,boolean moving){
        if(old.getBlock()!=next.getBlock() && level instanceof net.minecraft.server.level.ServerLevel server && level.getBlockEntity(pos) instanceof AssimilationHeartBlockEntity be)be.hibernate(server);
        super.onRemove(old,level,pos,next,moving);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AssimilationHeartBlockEntity(pos, state);
    }

    @Override protected com.mojang.serialization.MapCodec<? extends net.minecraft.world.level.block.HorizontalDirectionalBlock> codec(){return simpleCodec(AssimilationHeartBlock::new);}

    @Override public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if(state.getValue(OPEN)>0 && random.nextInt(12)==0)
            level.addParticle(ParticleTypes.WHITE_ASH,pos.getX()+.5,pos.getY()+.3,pos.getZ()+.5,0,.006,0);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,
            BlockState state, BlockEntityType<T> type) {
        return level.isClientSide || type != ModBlockEntities.ASSIMILATION_HEART.get()
                ? null
                : (lvl, pos, st, be) -> AssimilationHeartBlockEntity.serverTick(
                        (net.minecraft.server.level.ServerLevel) lvl, pos, st,
                        (AssimilationHeartBlockEntity) be);
    }
}
