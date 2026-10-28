package com.expeditions.block.entity;

import com.expeditions.ExpeditionsConfig;
import com.expeditions.block.AssimilationLayerBlock;
import com.expeditions.registry.ModBlockEntities;
import com.expeditions.registry.ModBlocks;
import com.expeditions.server.Microbiome;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The microbiome's nucleus: the only thing that grows the assimilation
 * layer. Every {@code microbiome.growthIntervalTicks} it pulses once —
 * seeding layers against its own faces when the colony is empty,
 * otherwise picking one legal covering act across the colony's whole
 * frontier: fillet an uncovered face of a member's own cell (the corner
 * cell that holds both floor and wall), walk the covered face sideways
 * along the surface, wrap around a support's edge onto its other faces,
 * or seep through to the far face of a thin wall. The membrane is
 * multiface — one cell coats several faces — so junctions are covered,
 * not skipped.
 *
 * <p>Colony cells are persisted so a chunk unload never forgets what it
 * grew; dead members are pruned each pulse so broken layers free their
 * colony slot. Bounds are deliberate: {@code microbiome.maxColonySize}
 * caps the covered surface (a blob, never a takeover) and a cleared
 * dimension or a disabled config silences the pulse entirely — the heart
 * stays, the spread stops. Removing the heart is the counterplay: no
 * ticker, no growth, and the remaining layers die only with their
 * supports.
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

    /**
     * One covering act: {@code cell} gains face-property {@code face},
     * coating the {@code face.getOpposite()} face of the support at
     * {@code cell.relative(face)}. The cell may be empty (fresh membrane)
     * or already hold a layer (a new face joins it).
     */
    private record Move(BlockPos cell, Direction face) {
    }

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

    /** Live covered-face count across the colony (the cap's measure). */
    public int coverage(ServerLevel level) {
        int covered = 0;
        for (long member : colony) {
            covered += MultifaceBlock.availableFaces(
                    level.getBlockState(BlockPos.of(member))).size();
        }
        return covered;
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
        if (coverage(level) >= ExpeditionsConfig.microbiomeMaxColonySize()) {
            return;
        }
        Set<Move> moves = colony.isEmpty()
                ? seedMoves(level, getBlockPos())
                : growthMoves(level);
        if (moves.isEmpty()) {
            return;
        }
        List<Move> choices = new ArrayList<>(moves);
        Move move = choices.get(level.random.nextInt(choices.size()));
        apply(level, move);
    }

    /** Applies one covering act and registers the cell with the colony. */
    private void apply(ServerLevel level, Move move) {
        BlockState existing = level.getBlockState(move.cell());
        BlockState next = (existing.getBlock() instanceof AssimilationLayerBlock
                        ? existing
                        : ModBlocks.ASSIMILATION_LAYER.get().defaultBlockState())
                .setValue(MultifaceBlock.getFaceProperty(move.face()), true);
        if (!next.equals(existing)
                && level.setBlock(move.cell(), next, 3)) {
            colony.add(move.cell().asLong());
            setChanged();
        }
    }

    /**
     * First colony members: the membrane coats the heart itself — every
     * open face gets a layer cell hugging it — plus the floor face under
     * each horizontal neighbor, so the carpet ring starts where players
     * will see it. Multiface cells mean the ring cell can hold the heart's
     * face AND the floor's at once; nothing blocks the junction anymore.
     */
    private static Set<Move> seedMoves(ServerLevel level, BlockPos heartPos) {
        Set<Move> moves = new LinkedHashSet<>();
        for (Direction d : Direction.values()) {
            BlockPos cell = heartPos.relative(d);
            if (!canHost(level, cell)) {
                continue;
            }
            moves.add(new Move(cell, d.getOpposite()));
            if (d.getAxis().isHorizontal()
                    && AssimilationLayerBlock.canAttachTo(
                            level, cell.below(), Direction.UP)) {
                moves.add(new Move(cell, Direction.DOWN));
            }
        }
        return moves;
    }

    /**
     * Every legal covering act across the colony's frontier. For a member
     * cell C and each face D it already covers (support S at C+D):
     * fillet adds any sturdy uncovered face of C itself (junction cells
     * coat floor and wall alike); walk steps face D onto the matching
     * face of each perpendicular neighbor of S (the surface continues);
     * wrap rolls onto S's other faces around the edge (over the top, down
     * the far side); through coats S's far face outright — the membrane
     * permeates one-block substrates.
     */
    private Set<Move> growthMoves(ServerLevel level) {
        Set<Move> moves = new LinkedHashSet<>();
        for (long member : colony) {
            BlockPos c = BlockPos.of(member);
            BlockState state = level.getBlockState(c);
            Set<Direction> faces = MultifaceBlock.availableFaces(state);
            for (Direction d : Direction.values()) {
                if (!faces.contains(d)
                        && AssimilationLayerBlock.canAttachTo(
                                level, c.relative(d), d.getOpposite())) {
                    moves.add(new Move(c, d));
                }
            }
            for (Direction d : faces) {
                BlockPos support = c.relative(d);
                for (Direction e : PERPENDICULAR.get(d)) {
                    propose(moves, level, c.relative(e), d,
                            AssimilationLayerBlock.canAttachTo(level,
                                    support.relative(e), d.getOpposite()));
                    propose(moves, level, support.relative(e), e.getOpposite(),
                            AssimilationLayerBlock.canAttachTo(level, support, e));
                }
                propose(moves, level, support.relative(d), d.getOpposite(),
                        AssimilationLayerBlock.canAttachTo(level, support, d));
            }
        }
        return moves;
    }

    /**
     * A move is legal when the face is attachable and the cell can host
     * it: air for fresh membrane, or an existing layer missing this face.
     */
    private static void propose(Set<Move> moves, ServerLevel level,
            BlockPos cell, Direction face, boolean attachable) {
        if (!attachable || !canHost(level, cell)) {
            return;
        }
        BlockState state = level.getBlockState(cell);
        if (!(state.getBlock() instanceof AssimilationLayerBlock)
                || !MultifaceBlock.hasFace(state, face)) {
            moves.add(new Move(cell, face));
        }
    }

    private static boolean canHost(ServerLevel level, BlockPos cell) {
        BlockState state = level.getBlockState(cell);
        return state.isAir() || state.getBlock() instanceof AssimilationLayerBlock;
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
