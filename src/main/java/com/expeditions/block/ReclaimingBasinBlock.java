package com.expeditions.block;

import com.expeditions.server.ReclaimingMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class ReclaimingBasinBlock extends SapVesselBlock {
    private static final VoxelShape SHAPE = box(1, 0, 1, 15, 10, 15);

    public ReclaimingBasinBlock(Properties properties) {
        super(properties, Kind.BASIN);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(new SimpleMenuProvider(
                    (id, inventory, ignored) -> new ReclaimingMenu(id, inventory, serverPlayer.serverLevel(), pos),
                    Component.translatable("block.expeditions.reclaiming_basin")));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
