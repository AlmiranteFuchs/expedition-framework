package com.expeditions.block;

import com.expeditions.server.ExpeditionManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.level.BlockGetter;

public final class FieldCacheBlock extends Block {
    public FieldCacheBlock(Properties properties) { super(properties); }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer serverPlayer) {
            ExpeditionManager.get(serverPlayer.server).claimFieldCache(serverPlayer, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return box(1, 0, 1, 15, 14, 15);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, net.minecraft.util.RandomSource random) {
        if (random.nextInt(5) == 0) {
            level.addParticle(RouteMarkerBlock.AMBER, pos.getX() + 0.5, pos.getY() + 0.66, pos.getZ() - 0.03,
                    0, 0.012, 0);
        }
    }
}
