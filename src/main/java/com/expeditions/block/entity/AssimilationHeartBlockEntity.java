package com.expeditions.block.entity;

import com.expeditions.ExpeditionsConfig;
import com.expeditions.block.AssimilationLayerBlock;
import com.expeditions.registry.ModBlockEntities;
import com.expeditions.registry.ModBlocks;
import com.expeditions.server.Microbiome;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
 * seeding the carpet ring on the ground beside it when the colony is
 * empty (its own side faces are only a fallback: covering them would
 * occupy the cells the ring needs), otherwise picking one legal growth
 * move across the colony's whole frontier: extend along the same wall,
 * wrap the support's edge, climb a wall one level up, or roll over a
 * one-block obstacle. Colony positions are persisted so a
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
        Map<BlockPos, Direction> moves = colony.isEmpty()
                ? seedMoves(level, getBlockPos())
                : growthMoves(level);
        if (moves.isEmpty()) {
            return;
        }
        List<BlockPos> cells = new ArrayList<>(moves.keySet());
        BlockPos cell = cells.get(level.random.nextInt(cells.size()));
        Direction facing = moves.get(cell);
        if (level.setBlock(cell, ModBlocks.ASSIMILATION_LAYER.get()
                .defaultBlockState().setValue(AssimilationLayerBlock.FACING, facing), 3)) {
            colony.add(cell.asLong());
            setChanged();
        }
    }

    /**
     * First colony members. The heart seeds the floor ring — up-facing
     * layers on the ground beside it — because a layer on the heart's own
     * side face occupies exactly the cell the ring needs, and a colony
     * started that way can never carpet outward. Side faces are only a
     * seed where no floor exists to carpet (cliff edge, overhang); the top
     * and bottom faces join the ring as they're part of the carpet plane.
     */
    private static Map<BlockPos, Direction> seedMoves(ServerLevel level, BlockPos heartPos) {
        Map<BlockPos, Direction> moves = new LinkedHashMap<>();
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockPos cell = heartPos.relative(d);
            if (!level.getBlockState(cell).isAir()) {
                continue;
            }
            if (AssimilationLayerBlock.canAttachTo(level, cell.below(), Direction.UP)) {
                moves.put(cell, Direction.UP);
            } else if (AssimilationLayerBlock.canAttachTo(level, heartPos, d)) {
                moves.put(cell, d);
            }
        }
        if (level.getBlockState(heartPos.above()).isAir()
                && AssimilationLayerBlock.canAttachTo(level, heartPos, Direction.UP)) {
            moves.put(heartPos.above(), Direction.UP);
        }
        if (level.getBlockState(heartPos.below()).isAir()
                && AssimilationLayerBlock.canAttachTo(level, heartPos, Direction.DOWN)) {
            moves.put(heartPos.below(), Direction.DOWN);
        }
        return moves;
    }

    /**
     * Every legal move across the colony's frontier. For each member on
     * face F of its support S and each direction D perpendicular to F:
     * extend covers the matching face of S+D (the wall continues), wrap
     * covers face D of S (the membrane rolls over the edge). Floor members
     * get two more moves since the ground's edge cases are walls and
     * bumps, not edges: climb covers the wall's side face one level up
     * (its ground-level face shares the carpet's own cell — one block per
     * cell leaves the junction bare by design), and roll-over covers the
     * top of a one-block obstacle.
     */
    private Map<BlockPos, Direction> growthMoves(ServerLevel level) {
        Map<BlockPos, Direction> moves = new LinkedHashMap<>();
        for (long member : colony) {
            BlockPos p = BlockPos.of(member);
            Direction f = level.getBlockState(p).getValue(AssimilationLayerBlock.FACING);
            BlockPos s = p.relative(f.getOpposite());
            for (Direction d : PERPENDICULAR.get(f)) {
                if (level.getBlockState(p.relative(d)).isAir()
                        && AssimilationLayerBlock.canAttachTo(level, s.relative(d), f)) {
                    moves.putIfAbsent(p.relative(d), f);
                }
                if (level.getBlockState(s.relative(d)).isAir()
                        && AssimilationLayerBlock.canAttachTo(level, s, d)) {
                    moves.putIfAbsent(s.relative(d), d);
                }
            }
            if (f == Direction.UP) {
                for (Direction d : Direction.Plane.HORIZONTAL) {
                    BlockPos neighbor = p.relative(d);
                    if (level.getBlockState(neighbor.above()).isAir()
                            && AssimilationLayerBlock.canAttachTo(level, neighbor, Direction.UP)) {
                        moves.putIfAbsent(neighbor.above(), Direction.UP);
                    }
                    if (level.getBlockState(p.above()).isAir()
                            && AssimilationLayerBlock.canAttachTo(level, neighbor.above(), d.getOpposite())) {
                        moves.putIfAbsent(p.above(), d.getOpposite());
                    }
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
