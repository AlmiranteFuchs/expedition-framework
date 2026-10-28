package com.expeditions.block;

import com.expeditions.block.entity.AssimilationLayerBlockEntity;
import com.expeditions.registry.ModItems;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.MultifaceSpreader;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * The Big Bad's assimilation layer: a sculk-vein-style membrane that
 * covers the faces of solid supporting blocks. One block occupies one
 * cell but can coat several faces of it at once — the corner cell at a
 * floor-wall junction holds both the floor-top and wall-side faces, so
 * growth bends around edges without leaving bare seams. The layer never
 * replaces, mutates or stores a support's state — each covered face lives
 * and dies with its own support, and the block dies with its last face.
 *
 * <p>Face semantics are vanilla multiface: {@code hasFace(state, d)}
 * means the neighbor at {@code pos.relative(d)} is covered on its
 * {@code d.getOpposite()} face, and the patch renders on the cell's
 * {@code d} side. A sturdy full face is required — air, fluids, and
 * non-solid or partial faces refuse attachment. The block is only
 * harvested for essence by direct breaking; support loss, piston crush
 * and world restoration all drop nothing.
 */
public class AssimilationLayerBlock extends MultifaceBlock implements EntityBlock {
    public static final MapCodec<AssimilationLayerBlock> CODEC =
            simpleCodec(AssimilationLayerBlock::new);

    private final MultifaceSpreader spreader = new MultifaceSpreader(this);

    public AssimilationLayerBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends MultifaceBlock> codec() {
        return CODEC;
    }

    @Override
    public MultifaceSpreader getSpreader() {
        return spreader;
    }

    /** Whether {@code face} of the block at {@code supportPos} can hold a layer. */
    public static boolean canAttachTo(BlockGetter level, BlockPos supportPos,
            Direction face) {
        return level.getBlockState(supportPos)
                .isFaceSturdy(level, supportPos, face);
    }

    /**
     * Vanilla's multiface attachment rule accepts any full collision face —
     * leaves included. The membrane only grips sturdy surfaces, so every
     * consult point below re-routes through {@link #canAttachTo}.
     */
    @Override
    public boolean isValidStateForPlacement(BlockGetter level, BlockState state,
            BlockPos pos, Direction face) {
        return !(state.is(this) && MultifaceBlock.hasFace(state, face))
                && canAttachTo(level, pos.relative(face), face.getOpposite());
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level,
            BlockPos pos) {
        boolean any = false;
        for (Direction d : DIRECTIONS) {
            if (MultifaceBlock.hasFace(state, d)) {
                if (!canAttachTo(level, pos.relative(d), d.getOpposite())) {
                    return false;
                }
                any = true;
            }
        }
        return any;
    }

    /**
     * The canonical survival check, per face: when a supporting block
     * changes, only the face it held is shed — the membrane survives a
     * lost support while any other face still grips. This path goes
     * through {@code updateOrDestroy} which rolls drops, so the block must
     * have no loot table — essence is granted by {@link #playerDestroy}
     * instead, which only a real survival harvest reaches.
     */
    @Override
    protected BlockState updateShape(BlockState state, Direction direction,
            BlockState neighborState, LevelAccessor level, BlockPos pos,
            BlockPos neighborPos) {
        if (MultifaceBlock.hasFace(state, direction)
                && !canAttachTo(level, neighborPos, direction.getOpposite())) {
            BlockState cleared = state.setValue(
                    MultifaceBlock.getFaceProperty(direction), false);
            return MultifaceBlock.hasAnyFace(cleared)
                    ? cleared
                    : Blocks.AIR.defaultBlockState();
        }
        return state;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level,
            BlockPos pos, CollisionContext context) {
        // A membrane is a film, not a shelf: entities pass through it like
        // sculk veins and collide with the support, never the 1/16 skin.
        return Shapes.empty();
    }

    /**
     * Exactly one essence per deliberate harvest. {@code playerDestroy} is
     * only invoked by the game mode after a successful survival break —
     * never in creative, never on support loss or replacement — so the
     * empty loot table cannot leak a second drop and cleanup stays silent.
     */
    @Override
    public void playerDestroy(Level level, Player player, BlockPos pos,
            BlockState state, @Nullable BlockEntity blockEntity, ItemStack tool) {
        super.playerDestroy(level, player, pos, state, blockEntity, tool);
        if (!level.isClientSide) {
            popResource(level, pos,
                    new ItemStack(ModItems.ASSIMILATION_ESSENCE.get()));
        }
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AssimilationLayerBlockEntity(pos, state);
    }
}
