package com.expeditions.block;

import com.expeditions.block.entity.SapVesselBlockEntity;
import com.expeditions.registry.ModBlockEntities;
import com.expeditions.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;

public class SapVesselBlock extends Block implements EntityBlock {
    public enum Kind { BASIN, JAR, WICK }
    public static final IntegerProperty SAP_LEVEL = IntegerProperty.create("sap_level", 0, 4);
    public static final BooleanProperty WATER = BooleanProperty.create("water");
    private final Kind kind;

    public SapVesselBlock(Properties properties, Kind kind) {
        super(properties);
        this.kind = kind;
        var initial = stateDefinition.any().setValue(SAP_LEVEL, 0).setValue(BlockStateProperties.FACING, Direction.NORTH);
        registerDefaultState(initial.setValue(WATER, false));
    }

    public Kind kind() { return kind; }

    /** Explicit recycling uses crouch even when the held item normally bypasses block use. */
    public static void allowRecyclingInteraction(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity().isShiftKeyDown() && !event.getItemStack().isEmpty()
                && event.getLevel().getBlockState(event.getPos()).getBlock() instanceof SapVesselBlock block
                && block.kind == Kind.JAR
                && event.getUseBlock() != net.neoforged.neoforge.common.util.TriState.FALSE) {
            event.setUseBlock(net.neoforged.neoforge.common.util.TriState.TRUE);
        }
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (kind != Kind.WICK) return box(2, 0, 2, 14, 15, 14);
        return switch (state.getValue(BlockStateProperties.FACING).getAxis()) {
            case X -> box(0, 4, 4, 16, 12, 12);
            case Y -> box(4, 0, 4, 12, 16, 12);
            case Z -> box(4, 4, 0, 12, 12, 16);
        };
    }

    @Override
    protected BlockState rotate(BlockState state, net.minecraft.world.level.block.Rotation rotation) {
        return state.setValue(BlockStateProperties.FACING, rotation.rotate(state.getValue(BlockStateProperties.FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, net.minecraft.world.level.block.Mirror mirror) {
        return rotate(state, mirror.getRotation(state.getValue(BlockStateProperties.FACING)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(SAP_LEVEL, BlockStateProperties.FACING, WATER);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        var face = context.getClickedFace();
        return defaultBlockState().setValue(BlockStateProperties.FACING,
                face == Direction.UP ? context.getHorizontalDirection() : face);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SapVesselBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || kind == Kind.BASIN || type != ModBlockEntities.SAP_VESSEL.get()) return null;
        return (world, pos, block, entity) -> {
            var vessel = (SapVesselBlockEntity) entity;
            if (kind == Kind.JAR) vessel.jarTick(world);
            else vessel.wickTick(world, pos, block);
        };
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hit) {
        if (kind == Kind.JAR && player.isShiftKeyDown() && !stack.isEmpty()
                && !stack.is(ModItems.RETURN_KNOT.get()) && !stack.is(ModItems.ASSIMILATION_ESSENCE.get())
                && !stack.is(Items.WATER_BUCKET) && !stack.is(Items.BUCKET) && !stack.is(ModItems.RETURN_SAP_BUCKET.get())) {
            if (!level.isClientSide && level.getBlockEntity(pos) instanceof SapVesselBlockEntity vessel) {
                int used = vessel.recycle(stack, false);
                if (used > 0) {
                    level.playSound(null, pos, com.expeditions.registry.ModSounds.SAP_VESSEL_RECYCLE.get(),
                            net.minecraft.sounds.SoundSource.BLOCKS, .8f, 1.0f);
                    stack.shrink(used);
                    player.getInventory().setChanged();
                    player.displayClientMessage(Component.translatable("message.expeditions.sap_recycled", used), true);
                } else {
                    player.displayClientMessage(Component.translatable("message.expeditions.sap_full"), true);
                }
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof SapVesselBlockEntity vessel) {
            // The steep: water receives the knot, essence later settles the memory into sap.
            if (kind == Kind.JAR && stack.is(ModItems.RETURN_KNOT.get())) {
                if (vessel.startSteep(com.expeditions.item.ReturnKnotItem.source(stack))) {
                    stack.shrink(1);
                    level.playSound(null, pos, com.expeditions.registry.ModSounds.SAP_VESSEL_FILL.get(),
                            net.minecraft.sounds.SoundSource.BLOCKS, .8f, 1.0f);
                    player.displayClientMessage(brewStatus(vessel), true);
                } else {
                    player.displayClientMessage(vessel.steeping() ? brewStatus(vessel)
                            : Component.translatable("message.expeditions.sap_needs_water"), true);
                }
                return ItemInteractionResult.sidedSuccess(level.isClientSide);
            }
            if (kind == Kind.JAR && stack.is(ModItems.ASSIMILATION_ESSENCE.get())) {
                if (vessel.catalyze()) {
                    stack.shrink(1);
                    level.playSound(null, pos, com.expeditions.registry.ModSounds.SAP_VESSEL_FILL.get(),
                            net.minecraft.sounds.SoundSource.BLOCKS, .8f, 1.0f);
                    player.displayClientMessage(Component.translatable("message.expeditions.sap_brewed"), true);
                } else {
                    player.displayClientMessage(vessel.steeping() ? brewStatus(vessel)
                            : Component.translatable("message.expeditions.sap_ingredients"), true);
                }
                return ItemInteractionResult.sidedSuccess(level.isClientSide);
            }
            if (kind == Kind.JAR && stack.is(Items.WATER_BUCKET)) {
                var water = new FluidStack(net.minecraft.world.level.material.Fluids.WATER, 1000);
                if (vessel.tank().fill(water, FluidAction.SIMULATE) == 1000) {
                    vessel.tank().fill(water, FluidAction.EXECUTE);
                    level.playSound(null, pos, com.expeditions.registry.ModSounds.SAP_VESSEL_FILL.get(),
                            net.minecraft.sounds.SoundSource.BLOCKS, .8f, 1.0f);
                    player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, new ItemStack(Items.BUCKET)));
                    player.getInventory().setChanged();
                } else {
                    player.displayClientMessage(Component.translatable("message.expeditions.sap_full"), true);
                }
                return ItemInteractionResult.sidedSuccess(level.isClientSide);
            }
            if (stack.is(Items.BUCKET)) {
                var drained = vessel.fluidHandler().drain(1000, FluidAction.SIMULATE);
                if (drained.getAmount() == 1000) {
                    vessel.fluidHandler().drain(1000, FluidAction.EXECUTE);
                    level.playSound(null, pos, com.expeditions.registry.ModSounds.SAP_VESSEL_FILL.get(),
                            net.minecraft.sounds.SoundSource.BLOCKS, .8f, 1.0f);
                    ItemStack filled = SapVesselBlockEntity.isWater(drained) ? new ItemStack(Items.WATER_BUCKET)
                            : sapBucket(com.expeditions.core.ReturnSap.dimension(drained));
                    player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, filled));
                    player.getInventory().setChanged();
                } else {
                    player.displayClientMessage(vesselStatus(vessel), true);
                }
                return ItemInteractionResult.sidedSuccess(level.isClientSide);
            }
            if (FluidUtil.getFluidHandler(stack).isPresent()) {
                FluidUtil.interactWithFluidHandler(player, hand, vessel.fluidHandler());
                return ItemInteractionResult.sidedSuccess(level.isClientSide);
            }
        } else if (level.getBlockEntity(pos) instanceof SapVesselBlockEntity vessel
                && (stack.is(ModItems.RETURN_KNOT.get()) || stack.is(ModItems.ASSIMILATION_ESSENCE.get())
                || stack.is(Items.WATER_BUCKET) || stack.is(Items.BUCKET)
                || FluidUtil.getFluidHandler(stack).isPresent())) {
            return ItemInteractionResult.sidedSuccess(true);
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    private static ItemStack sapBucket(String dimension) {
        var bucket = new ItemStack(ModItems.RETURN_SAP_BUCKET.get());
        com.expeditions.item.ReturnSapBucketItem.bind(bucket, dimension);
        return bucket;
    }

    private static Component brewStatus(SapVesselBlockEntity vessel) {
        if (vessel.steeped()) {
            return Component.translatable(vessel.tank().getFluidAmount() == 1000
                    ? "message.expeditions.sap_ready" : "message.expeditions.sap_needs_water");
        }
        return Component.translatable("message.expeditions.sap_steeping", vessel.steepSecondsRemaining());
    }

    private static Component vesselStatus(SapVesselBlockEntity vessel) {
        if (vessel.steeping()) return brewStatus(vessel);
        if (vessel.kind() == Kind.JAR && SapVesselBlockEntity.isWater(vessel.tank().getFluid())) {
            int water = vessel.tank().getFluidAmount();
            return water == 1000 ? Component.translatable("message.expeditions.sap_add_knot")
                    : Component.translatable("message.expeditions.sap_needs_water");
        }
        return Component.translatable("message.expeditions.sap_amount", vesselTotal(vessel), vesselCapacity(vessel));
    }
    private static int vesselTotal(SapVesselBlockEntity vessel) {
        return vessel.kind() == Kind.BASIN ? vessel.totalSap() : vessel.tank().getFluidAmount();
    }

    private static int vesselCapacity(SapVesselBlockEntity vessel) {
        return vessel.kind() == Kind.BASIN
                ? SapVesselBlockEntity.BASIN_CAPACITY * SapVesselBlockEntity.BASIN_WELLS
                : vessel.tank().getCapacity();
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof SapVesselBlockEntity vessel) {
            player.displayClientMessage(vesselStatus(vessel), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
