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
    public void animateTick(BlockState state, Level level, BlockPos pos, net.minecraft.util.RandomSource random) {
        double angle = random.nextDouble() * Math.PI * 2;
        double x = pos.getX() + 0.5 + Math.cos(angle) * 0.35;
        double z = pos.getZ() + 0.5 + Math.sin(angle) * 0.35;
        double y = pos.getY() + 0.2 + random.nextDouble() * 0.7;
        level.addParticle(net.minecraft.core.particles.ParticleTypes.ASH, x, y, z, 0, 0.012, 0);
        if (random.nextInt(4) == 0) {
            level.addParticle(net.minecraft.core.particles.ParticleTypes.SMALL_FLAME,
                    x, y, z, (pos.getX() + 0.5 - x) * 0.02, 0.005, (pos.getZ() + 0.5 - z) * 0.02);
        }
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (!level.isClientSide && entity instanceof ServerPlayer player) {
            ExpeditionManager.get(level.getServer()).enterPortal(player, pos);
        }
    }
}
