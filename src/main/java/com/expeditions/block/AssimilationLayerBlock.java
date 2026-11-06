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
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
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
 * harvested for dormant flakes by direct breaking; support loss, piston crush
 * and world restoration all drop nothing.
 */
public class AssimilationLayerBlock extends MultifaceBlock implements EntityBlock {
    public static final MapCodec<AssimilationLayerBlock> CODEC =
            simpleCodec(AssimilationLayerBlock::new);

    /**
     * Decorative sprite growths rendered inside the membrane's own cell:
     * a tuft stands on a covered floor face ({@code down}), a drip hangs
     * under a covered ceiling face ({@code up}). Both are flat cross
     * sprites, never geometry, and die with the face that holds them.
     */
    public static final BooleanProperty TUFT = BooleanProperty.create("tuft");
    public static final BooleanProperty DRIP = BooleanProperty.create("drip");

    private final MultifaceSpreader spreader = new MultifaceSpreader(this);

    public AssimilationLayerBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState()
                .setValue(TUFT, false).setValue(DRIP, false));
    }

    @Override
    protected void createBlockStateDefinition(
            StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(TUFT, DRIP);
    }

    @Override
    protected void entityInside(BlockState state,Level level,BlockPos pos,net.minecraft.world.entity.Entity entity) {
        super.entityInside(state,level,pos,entity);
        if(!(this instanceof DormantLayerBlock) && level instanceof net.minecraft.server.level.ServerLevel server
                && entity instanceof net.minecraft.world.entity.LivingEntity living)
            com.expeditions.server.Assimilation.touchLayer(server,pos,state,living);
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
            if (direction == Direction.DOWN) {
                cleared = cleared.setValue(TUFT, false);
            } else if (direction == Direction.UP) {
                cleared = cleared.setValue(DRIP, false);
            }
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
     * Exactly one dormant flake per deliberate harvest. {@code playerDestroy} is
     * only invoked by the game mode after a successful survival break —
     * never in creative, never on support loss or replacement — so the
     * empty loot table cannot leak a second raw drop and cleanup stays silent.
     */
    @Override
    public void playerDestroy(Level level, Player player, BlockPos pos,
            BlockState state, @Nullable BlockEntity blockEntity, ItemStack tool) {
        super.playerDestroy(level, player, pos, state, blockEntity, tool);
        if (!level.isClientSide) {
            popResource(level, pos,
                    new ItemStack(ModItems.DORMANT_FLAKE.get()));
        }
    }

    /**
     * Sprouts at most one sprite growth on a membrane cell: a tuft where
     * a floor face is covered, a drip where a ceiling face is covered.
     * Called on fresh covering acts and rarely from the ticker so mature
     * colonies keep decorating. No-op when the roll fails or the covered
     * faces already carry their growth.
     */
    public static boolean trySprout(net.minecraft.server.level.ServerLevel level,
            BlockPos pos, BlockState state, double chance) {
        if (level.random.nextDouble() >= chance) {
            return false;
        }
        BlockState grown = null;
        if (MultifaceBlock.hasFace(state, Direction.DOWN)
                && !state.getValue(TUFT)) {
            grown = state.setValue(TUFT, true);
        } else if (MultifaceBlock.hasFace(state, Direction.UP)
                && !state.getValue(DRIP)) {
            grown = state.setValue(DRIP, true);
        }
        return grown != null && level.setBlock(pos, grown, 3);
    }

    @Override public <T extends BlockEntity> net.minecraft.world.level.block.entity.BlockEntityTicker<T> getTicker(Level level,BlockState state,net.minecraft.world.level.block.entity.BlockEntityType<T> type){
        if(level.isClientSide || this instanceof DormantLayerBlock || type!=com.expeditions.registry.ModBlockEntities.ASSIMILATION_LAYER.get())return null;
        return (world,pos,s,entity)->{
            if((world.getGameTime()+pos.asLong())%20!=0)return;
            var be=(AssimilationLayerBlockEntity)entity;var server=(net.minecraft.server.level.ServerLevel)world;var heart=be.heart();
            if(com.expeditions.server.Stillness.suppresses(server,pos) || heart!=null && world.hasChunkAt(heart) && !world.getBlockState(heart).is(com.expeditions.registry.ModBlocks.ASSIMILATION_HEART.get())){
                world.setBlockAndUpdate(pos,dormantState(s));
                return;
            }
            trySprout(server,pos,s,com.expeditions.server.data.ExpeditionTuning.real(
                    server.dimension(),"microbiome.growth_chance",
                    com.expeditions.ExpeditionsConfig.microbiomeGrowthChance())*0.002);
        };
    }
    public static BlockState dormantState(BlockState living){
        var cold=com.expeditions.registry.ModBlocks.DORMANT_LAYER.get().defaultBlockState();
        for(Direction face:DIRECTIONS)cold=cold.setValue(MultifaceBlock.getFaceProperty(face),MultifaceBlock.hasFace(living,face));
        return cold.setValue(TUFT,living.getValue(TUFT)).setValue(DRIP,living.getValue(DRIP));
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AssimilationLayerBlockEntity(pos, state);
    }
}
