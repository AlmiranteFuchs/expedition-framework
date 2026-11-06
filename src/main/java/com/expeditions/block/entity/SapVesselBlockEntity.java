package com.expeditions.block.entity;

import com.expeditions.block.SapVesselBlock;
import com.expeditions.core.ReturnSap;
import com.expeditions.registry.ModBlockEntities;
import com.expeditions.registry.ModFluids;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;

public final class SapVesselBlockEntity extends BlockEntity {
    public static final int BASIN_CAPACITY = 2000;
    /** Wells one basin keeps separate — one trace dimension each. */
    public static final int BASIN_WELLS = 6;
    private final SapVesselBlock.Kind kind;
    private final FluidTank tank;
    /** Basin wells: trace dimension -> stored mB. Only the basin uses it. */
    private final Map<String, Integer> wells = new LinkedHashMap<>();
    /**
     * The jar's steep: the dimension of the dissolving knot and the ticks
     * left before essence can catalyze the steep into return sap. Empty
     * dimension means no steep is underway. Public counter is a test seam.
     */
    private String steepDimension = "";
    public int steepTicks;
    private final ItemStackHandler recycler = new ItemStackHandler(1) {
        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            validateSlotIndex(slot);
            int used = recycle(stack, simulate);
            return stack.copyWithCount(stack.getCount() - used);
        }
    };

    public SapVesselBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SAP_VESSEL.get(), pos, state);
        kind = ((SapVesselBlock) state.getBlock()).kind();
        int capacity = switch (kind) {
            case BASIN -> BASIN_CAPACITY;
            case JAR -> 4000;
            case WICK -> 250;
        };
        tank = new FluidTank(capacity, this::fluidAllowed) {
            @Override
            public int fill(FluidStack resource, FluidAction action) {
                if (kind == SapVesselBlock.Kind.JAR && isWater(resource)) {
                    int amount = Math.min(resource.getAmount(), 1000 - getFluidAmount());
                    return amount > 0 ? super.fill(resource.copyWithAmount(amount), action) : 0;
                }
                return super.fill(resource, action);
            }

            @Override
            public FluidStack drain(int amount, FluidAction action) {
                if (kind == SapVesselBlock.Kind.JAR && steeping() && isWater(getFluid())) {
                    // Preserve the steep even if an external pump asks to empty the jar.
                    amount = Math.min(amount, getFluidAmount() - 1000);
                }
                return amount > 0 ? super.drain(amount, action) : FluidStack.EMPTY;
            }

            @Override
            protected void onContentsChanged() {
                // A steep lives in its water — draw the water off and the knot is lost with it.
                if (kind == SapVesselBlock.Kind.JAR && steeping()
                        && (!isWater(tank.getFluid()) || tank.getFluidAmount() < 1000)) {
                    steepDimension = "";
                }
                setChanged();
                updateLevelProperty();
            }
        };
    }

    /** Water steeping and same-memory sap may occupy a jar; a wick carries whatever flows. */
    private boolean fluidAllowed(FluidStack fluid) {
        if (kind == SapVesselBlock.Kind.BASIN) {
            return false;
        }
        if (fluid.getFluid() == Fluids.WATER || fluid.getFluid() == Fluids.FLOWING_WATER) {
            return kind == SapVesselBlock.Kind.JAR && (tank == null || tank.isEmpty() || isWater(tank.getFluid()));
        }
        if (!fluid.is(ModFluids.SAP.get())) {
            return false;
        }
        if (kind == SapVesselBlock.Kind.WICK || tank == null || tank.isEmpty()) {
            return true;
        }
        return ReturnSap.same(tank.getFluid(), fluid);
    }

    public static boolean isWater(FluidStack stack) {
        return stack.getFluid() == Fluids.WATER || stack.getFluid() == Fluids.FLOWING_WATER;
    }

    private void updateLevelProperty() {
        var world = getLevel();
        var state = getBlockState();
        if (world == null || world.isClientSide || kind == SapVesselBlock.Kind.WICK) {
            return;
        }
        int amount = kind == SapVesselBlock.Kind.BASIN
                ? wells.values().stream().mapToInt(Integer::intValue).max().orElse(0)
                : tank.getFluidAmount();
        int capacity = kind == SapVesselBlock.Kind.BASIN ? BASIN_CAPACITY : tank.getCapacity();
        int visible = (amount * 4 + capacity - 1) / capacity;
        var updated = state.setValue(SapVesselBlock.SAP_LEVEL, visible);
        if (kind == SapVesselBlock.Kind.JAR) updated = updated.setValue(SapVesselBlock.WATER, isWater(tank.getFluid()));
        if (state != updated) {
            world.setBlock(worldPosition, updated, 2);
        }
    }

    /** The jar/wick tank. The basin's wells live behind {@link #sapFor}/{@link #drainSap}. */
    public FluidTank tank() { return tank; }

    public SapVesselBlock.Kind kind() { return kind; }

    /** The handler this vessel exposes to the world — wells for the basin, tank otherwise. */
    public IFluidHandler fluidHandler() {
        return kind == SapVesselBlock.Kind.BASIN ? basinHandler : tank;
    }

    // --- Basin wells -----------------------------------------------------

    public int sapFor(String dimension) {
        return kind == SapVesselBlock.Kind.BASIN ? wells.getOrDefault(dimension, 0) : 0;
    }

    public Set<String> stockedDimensions() {
        return Set.copyOf(wells.keySet());
    }

    public int totalSap() {
        return wells.values().stream().mapToInt(Integer::intValue).sum();
    }

    public boolean drainSap(String dimension, int amount) {
        int held = sapFor(dimension);
        if (held < amount) {
            return false;
        }
        if (held == amount) {
            wells.remove(dimension);
        } else {
            wells.put(dimension, held - amount);
        }
        setChanged();
        updateLevelProperty();
        return true;
    }

    private int fillWell(FluidStack stack, boolean simulate) {
        if (!stack.is(ModFluids.SAP.get()) || stack.isEmpty()) {
            return 0;
        }
        // Unmarked sap (e.g. a mixing recipe's output) assumes local trace.
        String dimension = ReturnSap.dimension(stack);
        if (dimension.isBlank() && getLevel() != null) {
            dimension = getLevel().dimension().location().toString();
        }
        if (!wells.containsKey(dimension) && wells.size() >= BASIN_WELLS) {
            return 0;
        }
        int moved = Math.min(stack.getAmount(), BASIN_CAPACITY - sapFor(dimension));
        if (moved <= 0) {
            return 0;
        }
        if (!simulate) {
            wells.merge(dimension, moved, Integer::sum);
            setChanged();
            updateLevelProperty();
        }
        return moved;
    }

    /** Pipes and buckets see each well as its own tank, in insertion order. */
    private final IFluidHandler basinHandler = new IFluidHandler() {
        @Override public int getTanks() { return BASIN_WELLS; }
        @Override public FluidStack getFluidInTank(int tankIndex) {
            var keys = new ArrayList<>(wells.keySet());
            return tankIndex >= 0 && tankIndex < keys.size()
                    ? ReturnSap.of(keys.get(tankIndex), wells.get(keys.get(tankIndex)))
                    : FluidStack.EMPTY;
        }
        @Override public int getTankCapacity(int tankIndex) { return BASIN_CAPACITY; }
        @Override public boolean isFluidValid(int tankIndex, FluidStack stack) {
            return stack.is(ModFluids.SAP.get());
        }
        @Override public int fill(FluidStack resource, FluidAction action) {
            return fillWell(resource, !action.execute());
        }
        @Override public FluidStack drain(FluidStack resource, FluidAction action) {
            if (!resource.is(ModFluids.SAP.get())) {
                return FluidStack.EMPTY;
            }
            String dimension = ReturnSap.dimension(resource);
            int moved = Math.min(resource.getAmount(), sapFor(dimension));
            if (moved <= 0) {
                return FluidStack.EMPTY;
            }
            if (action.execute()) {
                drainSap(dimension, moved);
            }
            return ReturnSap.of(dimension, moved);
        }
        @Override public FluidStack drain(int maxDrain, FluidAction action) {
            String deepest = wells.entrySet().stream().max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey).orElse(null);
            if (deepest == null) {
                return FluidStack.EMPTY;
            }
            int moved = Math.min(maxDrain, wells.get(deepest));
            if (action.execute()) {
                drainSap(deepest, moved);
            }
            return ReturnSap.of(deepest, moved);
        }
    };

    // --- Jar steep -------------------------------------------------------

    public boolean steeping() {
        return !steepDimension.isEmpty();
    }

    public boolean steeped() {
        return steeping() && steepTicks <= 0;
    }

    /** A knot dissolves into the water it stands in; unmarked knots assume local trace. */
    public boolean startSteep(String knotDimension) {
        if (steeping() || !isWater(tank.getFluid()) || tank.getFluidAmount() != 1000) {
            return false;
        }
        steepDimension = knotDimension.isBlank() && getLevel() != null
                ? getLevel().dimension().location().toString() : knotDimension;
        steepTicks = com.expeditions.ExpeditionsConfig.sapSteepTicks();
        setChanged();
        return true;
    }

    /** Essence is the solvent the memory yields to: steep + essence -> that dimension's sap. */
    public boolean catalyze() {
        if (!steeped() || !isWater(tank.getFluid()) || tank.getFluidAmount() != 1000) {
            return false;
        }
        tank.setFluid(ReturnSap.of(steepDimension, 1000));
        steepDimension = "";
        setChanged();
        updateLevelProperty();
        if (getLevel() instanceof net.minecraft.server.level.ServerLevel server) {
            com.expeditions.server.ExpeditionEventHandler.bloomWork(server, worldPosition);
        }
        return true;
    }

    public int steepSecondsRemaining() {
        return (steepTicks + 19) / 20;
    }

    public void jarTick(Level level) {
        if (getBlockState().getValue(SapVesselBlock.WATER) != isWater(tank.getFluid())) updateLevelProperty();
        if (!steeping() || steepTicks <= 0) return;
        if (--steepTicks == 0) {
            setChanged();
            level.playSound(null, worldPosition, com.expeditions.registry.ModSounds.SAP_VESSEL_FILL.get(),
                    net.minecraft.sounds.SoundSource.BLOCKS, 0.7f, 1.2f);
            if (level instanceof net.minecraft.server.level.ServerLevel server) {
                server.sendParticles(net.minecraft.core.particles.ParticleTypes.WAX_ON,
                        worldPosition.getX() + 0.5, worldPosition.getY() + 0.9, worldPosition.getZ() + 0.5,
                        5, 0.16, 0.08, 0.16, 0);
            }
        }
    }

    // --- Recycling -------------------------------------------------------

    public int recycle(ItemStack stack, boolean simulate) {
        if (kind != SapVesselBlock.Kind.JAR || stack.isEmpty()) return 0;
        String dimension = getLevel() != null ? getLevel().dimension().location().toString() : "";
        if (tank.fill(ReturnSap.of(dimension, 1), FluidAction.SIMULATE) == 0) return 0;
        int yield = Math.clamp(com.expeditions.server.ItemValuator.score(stack), 1, 25);
        if (stack.isDamaged()) {
            yield = Math.max(1, (int) ((long) yield * (stack.getMaxDamage() - stack.getDamageValue()) / stack.getMaxDamage()));
        }
        int used = Math.min(stack.getCount(), tank.getSpace() / yield);
        if (used > 0 && !simulate) {
            tank.fill(ReturnSap.of(dimension, used * yield), FluidAction.EXECUTE);
            if (getLevel() instanceof net.minecraft.server.level.ServerLevel server) {
                com.expeditions.server.ExpeditionEventHandler.bloomWork(server, worldPosition);
            }
        }
        return used;
    }

    // --- Capabilities, ticking, persistence ------------------------------

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, ModBlockEntities.SAP_VESSEL.get(),
                (vessel, side) -> switch (vessel.kind) {
                    case BASIN -> vessel.basinHandler;
                    case JAR -> vessel.tank;
                    case WICK -> side == null || side.getAxis() == vessel.getBlockState()
                            .getValue(BlockStateProperties.FACING).getAxis() ? vessel.tank : null;
                });
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ModBlockEntities.SAP_VESSEL.get(),
                (vessel, side) -> vessel.kind == SapVesselBlock.Kind.JAR
                        && (side == null || side == net.minecraft.core.Direction.UP) ? vessel.recycler : null);
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
        tag.putString("SteepDimension", steepDimension);
        tag.putInt("SteepTicks", steepTicks);
        var wellsTag = new CompoundTag();
        wells.forEach(wellsTag::putInt);
        tag.put("Wells", wellsTag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        tank.readFromNBT(registries, tag.getCompound("Sap"));
        if (kind != SapVesselBlock.Kind.BASIN) {
            if (!tank.isFluidValid(tank.getFluid())) tank.setFluid(FluidStack.EMPTY);
            else if (tank.getFluidAmount() > tank.getCapacity()) tank.getFluid().setAmount(tank.getCapacity());
        }
        steepDimension = tag.getString("SteepDimension");
        steepTicks = tag.getInt("SteepTicks");
        wells.clear();
        var wellsTag = tag.getCompound("Wells");
        for (String dimension : wellsTag.getAllKeys()) {
            if (wells.size() < BASIN_WELLS) {
                wells.put(dimension, Math.min(BASIN_CAPACITY, wellsTag.getInt(dimension)));
            }
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        // Chunk loading reads the tag before attaching a level. Migrate only once
        // the basin knows its dimension, and persist the resulting well.
        if (kind == SapVesselBlock.Kind.BASIN && getLevel() instanceof net.minecraft.server.level.ServerLevel server
                && tank.getFluid().is(ModFluids.SAP.get())) {
            String dimension = server.dimension().location().toString();
            wells.merge(dimension, Math.min(BASIN_CAPACITY, tank.getFluidAmount()),
                    (current, legacy) -> Math.min(BASIN_CAPACITY, current + legacy));
            tank.setFluid(FluidStack.EMPTY);
            setChanged();
        }
    }
}
