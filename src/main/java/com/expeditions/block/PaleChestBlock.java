package com.expeditions.block;

import com.expeditions.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class PaleChestBlock extends ChestBlock {
    public PaleChestBlock(Properties properties) {
        super(properties, ModBlockEntities.PALE_CHEST::get);
    }

    @Override
    public ChestBlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new Storage(pos, state);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    public static final class Storage extends ChestBlockEntity {
        public Storage(BlockPos pos, BlockState state) {
            super(ModBlockEntities.PALE_CHEST.get(), pos, state);
        }
    }
}
