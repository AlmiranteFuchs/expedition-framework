package com.expeditions.block;

import com.expeditions.block.entity.AssimilationHeartBlockEntity;
import com.expeditions.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * The microbiome's nucleus — a pulsing amber node that is the
 * assimilation's only growth organ. A full solid cube (sturdy on every
 * face) so the layer can seed directly onto it and crawl outward.
 * Breakable: destroying the heart is the counterplay — the colony goes
 * inert instantly.
 */
public class AssimilationHeartBlock extends Block implements EntityBlock {
    public AssimilationHeartBlock(Properties properties) {
        super(properties);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AssimilationHeartBlockEntity(pos, state);
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
