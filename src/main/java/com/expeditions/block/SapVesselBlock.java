package com.expeditions.block;

import com.expeditions.block.entity.SapVesselBlockEntity;
import com.expeditions.registry.ModBlockEntities;
import com.expeditions.registry.ModFluids;
import com.expeditions.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;

public class SapVesselBlock extends Block implements EntityBlock {
    public enum Kind { BASIN, JAR, WICK }
    public static final IntegerProperty SAP_LEVEL = IntegerProperty.create("sap_level", 0, 4);
    private final Kind kind;

    public SapVesselBlock(Properties properties, Kind kind) {
        super(properties);
        this.kind = kind;
        registerDefaultState(stateDefinition.any().setValue(SAP_LEVEL, 0).setValue(BlockStateProperties.FACING, Direction.NORTH));
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
        builder.add(SAP_LEVEL, BlockStateProperties.FACING);
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
        if (level.isClientSide || kind != Kind.WICK || type != ModBlockEntities.SAP_VESSEL.get()) return null;
        return (world, pos, block, entity) -> ((SapVesselBlockEntity) entity).wickTick(world, pos, block);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hit) {
        if (kind == Kind.JAR && player.isShiftKeyDown() && !stack.isEmpty()) {
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
        if (kind == Kind.JAR && stack.is(Items.WATER_BUCKET)) {
            if (!level.isClientSide && level.getBlockEntity(pos) instanceof SapVesselBlockEntity vessel) {
                var essence = find(player, ModItems.ASSIMILATION_ESSENCE.get());
                var knot = find(player, ModItems.RETURN_KNOT.get());
                var sap = new FluidStack(ModFluids.SAP.get(), 1000);
                if (essence.isEmpty() || knot.isEmpty()) {
                    player.displayClientMessage(Component.translatable("message.expeditions.sap_ingredients"), true);
                } else if (vessel.tank().fill(sap, FluidAction.SIMULATE) != 1000) {
                    player.displayClientMessage(Component.translatable("message.expeditions.sap_full"), true);
                } else {
                    vessel.tank().fill(sap, FluidAction.EXECUTE);
                    level.playSound(null, pos, com.expeditions.registry.ModSounds.SAP_VESSEL_FILL.get(),
                            net.minecraft.sounds.SoundSource.BLOCKS, .8f, 1.0f);
                    essence.shrink(1);
                    knot.shrink(1);
                    player.setItemInHand(hand, new ItemStack(Items.BUCKET));
                    player.getInventory().setChanged();
                    player.displayClientMessage(Component.translatable("message.expeditions.sap_brewed"), true);
                }
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        if (FluidUtil.getFluidHandler(stack).isPresent()) {
            if (!level.isClientSide && level.getBlockEntity(pos) instanceof SapVesselBlockEntity vessel) {
                FluidUtil.interactWithFluidHandler(player, hand, vessel.tank());
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof SapVesselBlockEntity vessel) {
            player.displayClientMessage(Component.translatable("message.expeditions.sap_amount",
                    vessel.tank().getFluidAmount(), vessel.tank().getCapacity()), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private static ItemStack find(Player player, Item item) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            var stack = player.getInventory().getItem(slot);
            if (stack.is(item)) return stack;
        }
        return ItemStack.EMPTY;
    }
}
