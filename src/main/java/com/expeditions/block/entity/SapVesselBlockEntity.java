package com.expeditions.block.entity;

import com.expeditions.block.SapVesselBlock;
import com.expeditions.registry.ModBlockEntities;
import com.expeditions.registry.ModFluids;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

public final class SapVesselBlockEntity extends BlockEntity {
    private final SapVesselBlock.Kind kind;
    private final FluidTank tank;

    public SapVesselBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SAP_VESSEL.get(), pos, state);
        kind = ((SapVesselBlock) state.getBlock()).kind();
        int capacity = switch (kind) {
            case BASIN -> 2000;
            case JAR -> 4000;
            case WICK -> 250;
        };
        tank = new FluidTank(capacity, fluid -> fluid.is(ModFluids.SAP.get())) {
            @Override
            protected void onContentsChanged() {
                setChanged();
                var world = getLevel();
                var state = getBlockState();
                if (world != null && !world.isClientSide && kind != SapVesselBlock.Kind.WICK) {
                    int visible = (getFluidAmount() * 4 + capacity - 1) / capacity;
                    if (state.getValue(SapVesselBlock.SAP_LEVEL) != visible) {
                        world.setBlock(worldPosition, state.setValue(SapVesselBlock.SAP_LEVEL, visible), 2);
                    }
                }
            }
        };
    }

    public FluidTank tank() { return tank; }

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, ModBlockEntities.SAP_VESSEL.get(),
                (vessel, side) -> vessel.kind != SapVesselBlock.Kind.WICK || side == null
                        || side.getAxis() == vessel.getBlockState().getValue(BlockStateProperties.FACING).getAxis()
                        ? vessel.tank : null);
    }

    public void wickTick(Level level, BlockPos pos, BlockState state) {
        if (level.getGameTime() % 20 != 0) return;
        var facing = state.getValue(BlockStateProperties.FACING);
        if (facing == net.minecraft.core.Direction.UP) return;
        var inlet = pos.relative(facing.getOpposite());
        var outlet = pos.relative(facing);
        if (level.isLoaded(inlet) && !(level.getBlockEntity(inlet) instanceof SapVesselBlockEntity other
                && other.kind == SapVesselBlock.Kind.WICK)) {
            var source = level.getCapability(Capabilities.FluidHandler.BLOCK, inlet, facing);
            if (source != null) FluidUtil.tryFluidTransfer(tank, source, 25, true);
        }
        if (level.isLoaded(outlet)) {
            var receiver = level.getCapability(Capabilities.FluidHandler.BLOCK, outlet, facing.getOpposite());
            if (receiver != null) FluidUtil.tryFluidTransfer(receiver, tank, 25, true);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Sap", tank.writeToNBT(registries, new CompoundTag()));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        tank.readFromNBT(registries, tag.getCompound("Sap"));
        if (!tank.isFluidValid(tank.getFluid())) tank.setFluid(FluidStack.EMPTY);
        else if (tank.getFluidAmount() > tank.getCapacity()) tank.getFluid().setAmount(tank.getCapacity());
    }
}
