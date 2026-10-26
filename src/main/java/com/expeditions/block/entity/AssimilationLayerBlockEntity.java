package com.expeditions.block.entity;

import com.expeditions.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Marker block entity for the assimilation layer. It stores nothing — the
 * renderer derives everything (attachment direction, covered face, texture)
 * from the block state and its live neighbor, so a support that changes
 * appearance is picked up automatically without synced data.
 */
public class AssimilationLayerBlockEntity extends BlockEntity {
    public AssimilationLayerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ASSIMILATION_LAYER.get(), pos, state);
    }
}
