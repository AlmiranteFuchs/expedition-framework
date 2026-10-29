package com.expeditions.block;

import com.expeditions.block.entity.AssimilationHeartBlockEntity;
import com.expeditions.core.PulseWave;
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

/**
 * The microbiome's nucleus — a pale node that ignites ember on its
 * pulse; the assimilation's only growth organ. A full solid cube (sturdy on every
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

    /**
     * The colony breathes where it lives: pale ash drifts off the heart,
     * souls rise out of it, and embers spark only on the lub — the same
     * beat the renderer glows to. Client display tick only.
     */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos,
            RandomSource random) {
        if (random.nextFloat() < 0.35f) {
            level.addParticle(ParticleTypes.WHITE_ASH,
                    pos.getX() + random.nextDouble(),
                    pos.getY() + 1.05 + random.nextDouble() * 0.5,
                    pos.getZ() + random.nextDouble(), 0, 0.004, 0);
        }
        if (random.nextFloat() < 0.07f) {
            level.addParticle(ParticleTypes.SCULK_SOUL,
                    pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.7,
                    pos.getY() + 0.9,
                    pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.7,
                    0, 0.035, 0);
        }
        if (PulseWave.amplitudeAt(level.getGameTime(), 0, pos.asLong()) > 0.8
                && random.nextFloat() < 0.5f) {
            level.addParticle(ParticleTypes.SMALL_FLAME,
                    pos.getX() + random.nextDouble(), pos.getY() + 1.02,
                    pos.getZ() + random.nextDouble(), 0, 0.012, 0);
        }
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
