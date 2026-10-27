package com.expeditions.block.entity;

import com.expeditions.ExpeditionsConfig;
import com.expeditions.block.AssimilationLayerBlock;
import com.expeditions.registry.ModBlockEntities;
import com.expeditions.registry.ModBlocks;
import com.expeditions.server.Microbiome;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The microbiome's nucleus: the only thing that grows the assimilation
 * layer. Every {@code microbiome.growthIntervalTicks} it pulses once —
 * seeding a layer on one of its own sturdy faces when the colony is
 * empty, otherwise picking one legal growth move across the colony's
 * whole frontier: extend along the same wall, or wrap the support's
 * edge onto an adjacent face. Colony positions are persisted so a
 * chunk unload never forgets what it grew; dead members are pruned each
 * pulse so broken layers free their colony slot.
 *
 * <p>Bounds are deliberate: {@code microbiome.maxColonySize} caps the
 * colony (a blob, never a takeover) and a cleared dimension or a
 * disabled config silences the pulse entirely — the heart stays, the
 * spread stops. Removing the heart is the counterplay: no ticker, no
 * growth, and the remaining layers die only with their supports.
 */
public class AssimilationHeartBlockEntity extends BlockEntity {
    private static final String TAG_COLONY = "Colony";

    /** Growth directions of a face: every axis other than the face's own. */
    private static final Map<Direction, List<Direction>> PERPENDICULAR = Map.of(
            Direction.UP, List.copyOf(EnumSet.of(
                    Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST)),
            Direction.DOWN, List.copyOf(EnumSet.of(
                    Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST)),
            Direction.NORTH, List.copyOf(EnumSet.of(
                    Direction.UP, Direction.DOWN, Direction.EAST, Direction.WEST)),
            Direction.SOUTH, List.copyOf(EnumSet.of(
                    Direction.UP, Direction.DOWN, Direction.EAST, Direction.WEST)),
            Direction.EAST, List.copyOf(EnumSet.of(
                    Direction.UP, Direction.DOWN, Direction.NORTH, Direction.SOUTH)),
            Direction.WEST, List.copyOf(EnumSet.of(
                    Direction.UP, Direction.DOWN, Direction.NORTH, Direction.SOUTH)));

    /** Colony members grown by this heart, as {@link BlockPos#asLong}. */
    private final Set<Long> colony = new HashSet<>();

    public AssimilationHeartBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ASSIMILATION_HEART.get(), pos, state);
    }

    /** The heart's colony positions (read-only view, for tests). */
    public Set<BlockPos> colony() {
        Set<BlockPos> out = new HashSet<>();
        for (long p : colony) {
            out.add(BlockPos.of(p));
        }
        return Set.copyOf(out);
    }

    /** Block-entity ticker entry point: rate-limits, gates, then pulses. */
    public static void serverTick(ServerLevel level, BlockPos pos,
            BlockState state, AssimilationHeartBlockEntity heart) {
        int interval = ExpeditionsConfig.microbiomeGrowthIntervalTicks();
        if ((level.getGameTime() + pos.asLong()) % interval != 0
                || !Microbiome.enabled(level)) {
            return;
        }
        heart.pulse(level);
    }

    /**
     * One growth pulse: prune dead members, then apply a single uniformly
     * chosen legal move. Public so game tests can drive pulses directly
     * without waiting on the interval gate.
     */
    public void pulse(ServerLevel level) {
        colony.removeIf(p -> !(level.getBlockState(BlockPos.of(p)).getBlock()
                instanceof AssimilationLayerBlock));
        if (colony.size() >= ExpeditionsConfig.microbiomeMaxColonySize()) {
            return;
        }
        List<long[]> moves = colony.isEmpty()
                ? seedMoves(level, getBlockPos())
                : growthMoves(level);
        if (moves.isEmpty()) {
            return;
        }
        long[] move = moves.get(level.random.nextInt(moves.size()));
        BlockPos cell = BlockPos.of(move[0]);
        Direction facing = Direction.from3DDataValue((int) move[1]);
        if (level.setBlock(cell, ModBlocks.ASSIMILATION_LAYER.get()
                .defaultBlockState().setValue(AssimilationLayerBlock.FACING, facing), 3)) {
            colony.add(cell.asLong());
            setChanged();
        }
    }

    /** First colony member: a layer on any sturdy face of the heart. */
    private static List<long[]> seedMoves(ServerLevel level, BlockPos heartPos) {
        List<long[]> moves = new ArrayList<>();
        for (Direction d : Direction.values()) {
            if (level.getBlockState(heartPos.relative(d)).isAir()
                    && AssimilationLayerBlock.canAttachTo(level, heartPos, d)) {
                moves.add(new long[]{heartPos.relative(d).asLong(), d.ordinal()});
            }
        }
        return moves;
    }

    /**
     * Every legal move across the colony's frontier. For each member on
     * face F of its support S and each direction D perpendicular to F:
     * extend covers the matching face of S+D (the wall continues), wrap
     * covers face D of S (the membrane rolls over the edge).
     */
    private List<long[]> growthMoves(ServerLevel level) {
        List<long[]> moves = new ArrayList<>();
        for (long member : colony) {
            BlockPos p = BlockPos.of(member);
            Direction f = level.getBlockState(p).getValue(AssimilationLayerBlock.FACING);
            BlockPos s = p.relative(f.getOpposite());
            for (Direction d : PERPENDICULAR.get(f)) {
                if (level.getBlockState(p.relative(d)).isAir()
                        && AssimilationLayerBlock.canAttachTo(level, s.relative(d), f)) {
                    moves.add(new long[]{p.relative(d).asLong(), f.ordinal()});
                }
                if (level.getBlockState(s.relative(d)).isAir()
                        && AssimilationLayerBlock.canAttachTo(level, s, d)) {
                    moves.add(new long[]{s.relative(d).asLong(), d.ordinal()});
                }
            }
        }
        return moves;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put(TAG_COLONY, new LongArrayTag(List.copyOf(colony)));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        colony.clear();
        for (long p : tag.getLongArray(TAG_COLONY)) {
            colony.add(p);
        }
    }
}
