package com.expeditions.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import com.expeditions.registry.ModBlockEntities;

/**
 * Marker block entity for the assimilation layer. It stores one thing:
 * the position of the heart that grew it, so the client renderer can run
 * the colony's heartbeat wave — each cell's lub-dub is the same waveform
 * delayed by its distance from the source. Everything else (faces,
 * supports, texture) is derived live from the block state and neighbors.
 */
public class AssimilationLayerBlockEntity extends BlockEntity {
    private static final String TAG_HEART = "Heart";
    private static final String TAG_BORN = "Born";
    /** Sentinel: no recorded birth — the face renders fully grown. */
    public static final long BORN_UNKNOWN = Long.MIN_VALUE;

    @Nullable
    private BlockPos heart;
    private long born = BORN_UNKNOWN;

    public AssimilationLayerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ASSIMILATION_LAYER.get(), pos, state);
    }

    /** Claims this cell for {@code heartPos}'s colony wavefront. */
    public void setHeart(BlockPos heartPos) {
        this.heart = heartPos.immutable();
        setChanged();
    }

    /**
     * Stamps the cell's birth time once — the renderer uses it to grow
     * each face in over a few ticks instead of popping. Cells gaining
     * faces later keep their first stamp (the cell grew once).
     */
    public void markBorn(long gameTime) {
        if (born == BORN_UNKNOWN) {
            born = gameTime;
            setChanged();
        }
    }

    /** The game-time this cell was colonized, or {@link #BORN_UNKNOWN}. */
    public long born() {
        return born;
    }

    /** The heart that grew this cell, or null for hand-placed membrane. */
    @Nullable
    public BlockPos heart() {
        return heart;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (heart != null) {
            tag.putLong(TAG_HEART, heart.asLong());
        }
        if (born != BORN_UNKNOWN) {
            tag.putLong(TAG_BORN, born);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        heart = tag.contains(TAG_HEART)
                ? BlockPos.of(tag.getLong(TAG_HEART))
                : null;
        born = tag.getLong(TAG_BORN);
        if (born == 0) {
            born = BORN_UNKNOWN;
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        if (heart != null) {
            tag.putLong(TAG_HEART, heart.asLong());
        }
        if (born != BORN_UNKNOWN) {
            tag.putLong(TAG_BORN, born);
        }
        return tag;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
