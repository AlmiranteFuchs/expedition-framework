package com.expeditions.block;

import com.expeditions.server.ExpeditionManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Expedition portal. Spawned above the terminal when a run starts; players
 * who step into it are teleported to the run's remote landing spot and join
 * its party. It stays open only until the first checkpoint is activated,
 * then the server removes it — the party seals.
 *
 * <p>All behavior is delegated to the manager: the block itself is a
 * non-solid marker with no logic beyond forwarding entity collisions.
 */
public class ExpeditionPortalBlock extends Block {
    public ExpeditionPortalBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (!level.isClientSide && entity instanceof ServerPlayer player) {
            ExpeditionManager.get(level.getServer()).enterPortal(player, pos);
        }
    }
}
