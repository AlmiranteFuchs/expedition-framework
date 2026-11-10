package com.expeditions.block.entity;

import com.expeditions.block.SapVesselBlock;
import com.expeditions.core.ReturnSap;
import com.expeditions.registry.ModBlockEntities;
import com.expeditions.registry.ModFluids;
import com.expeditions.registry.ModItems;
import com.expeditions.item.ReturnKnotItem;
import com.expeditions.item.ResonanceCellItem;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.items.wrapper.RangedWrapper;
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
    public static final int BATCH_TICKS = 200;
    public static final int POWER_CAPACITY = 16000;
    public static final int WATER_CAPACITY = 4000;
    public static final int SAP_CAPACITY = 4000;
    private static final int BATCH_VOLUME = 1000;
    private static final int TICK_COST = 20;
    private String batchDimension = "";
    private String legacySteep = "";
    private int batchTicks;
    private boolean migrateLegacy;
    private final ItemStackHandler ingredients = new ItemStackHandler(3) {
        @Override public boolean isItemValid(int slot, ItemStack stack) {
            return switch (slot) {
                case 0 -> stack.is(ModItems.RETURN_KNOT.get()) && validTrace(ReturnKnotItem.source(stack));
                case 1 -> stack.is(ModItems.ASSIMILATION_ESSENCE.get());
                case 2 -> stack.is(ModItems.RESONANCE_CELL.get());
                default -> false;
            };
        }
        @Override public int getSlotLimit(int slot) { return slot == 2 ? 1 : super.getSlotLimit(slot); }
        @Override protected void onContentsChanged(int slot) { setChanged(); }
    };
    private final RangedWrapper input = new RangedWrapper(ingredients, 0, 2);
    private final FluidTank water = new FluidTank(WATER_CAPACITY, SapVesselBlockEntity::isWater) {
        @Override protected void onContentsChanged() { setChanged(); updateLevelProperty(); }
    };
    private final JarEnergy energy = new JarEnergy();

    private final class JarEnergy extends EnergyStorage {
        JarEnergy() { super(POWER_CAPACITY, 80, 0); }
        @Override public int receiveEnergy(int amount, boolean simulate) {
            int received = super.receiveEnergy(amount, simulate);
            if (!simulate && received > 0) setChanged();
            return received;
        }
        void restore(int amount) { energy = Math.clamp(amount, 0, POWER_CAPACITY); }
        void spendTick() { energy -= TICK_COST; }
    }

    private static boolean validTrace(String dimension) {
        return !dimension.isBlank() && net.minecraft.resources.ResourceLocation.tryParse(dimension) != null;
    }

    public SapVesselBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SAP_VESSEL.get(), pos, state);
        kind = ((SapVesselBlock) state.getBlock()).kind();
        int capacity = switch (kind) {
            case BASIN -> BASIN_CAPACITY;
            case JAR -> 4000;
            case WICK -> 250;
        };
        tank = new FluidTank(capacity, resource -> resource.is(ModFluids.SAP.get())) {
            @Override protected void onContentsChanged() { setChanged(); updateLevelProperty(); }
        };
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
                : tank.isEmpty() && kind == SapVesselBlock.Kind.JAR ? water.getFluidAmount() : tank.getFluidAmount();
        int capacity = kind == SapVesselBlock.Kind.BASIN ? BASIN_CAPACITY : tank.getCapacity();
        int visible = (amount * 4 + capacity - 1) / capacity;
        var updated = state.setValue(SapVesselBlock.SAP_LEVEL, visible);
        if (kind == SapVesselBlock.Kind.JAR) updated = updated.setValue(SapVesselBlock.WATER, tank.isEmpty() && amount > 0);
        if (state != updated) {
            world.setBlock(worldPosition, updated, 2);
        }
    }

    /** The jar/wick tank. The basin's wells live behind {@link #sapFor}/{@link #drainSap}. */
    public FluidTank tank() { return tank; }

    public SapVesselBlock.Kind kind() { return kind; }

    /** The handler this vessel exposes to the world — wells for the basin, tank otherwise. */
    public IFluidHandler fluidHandler() {
        return switch (kind) {
            case BASIN -> basinHandler;
            case JAR -> jarHandler;
            case WICK -> tank;
        };
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
        if (amount <= 0 || held < amount) {
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
        String dimension = ReturnSap.dimension(stack);
        if (!validTrace(dimension)) return 0;
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
            return stack.is(ModFluids.SAP.get()) && validTrace(ReturnSap.dimension(stack));
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
            if (deepest == null || maxDrain <= 0) {
                return FluidStack.EMPTY;
            }
            int moved = Math.min(maxDrain, wells.get(deepest));
            if (action.execute()) {
                drainSap(deepest, moved);
            }
            return ReturnSap.of(deepest, moved);
        }
    };

    private final IFluidHandler jarHandler = new IFluidHandler() {
        @Override public int getTanks() { return 2; }
        @Override public FluidStack getFluidInTank(int slot) {
            return slot == 0 ? water.getFluid() : slot == 1 ? tank.getFluid() : FluidStack.EMPTY;
        }
        @Override public int getTankCapacity(int slot) { return slot == 0 ? WATER_CAPACITY : SAP_CAPACITY; }
        @Override public boolean isFluidValid(int slot, FluidStack stack) { return slot == 0 && isWater(stack); }
        @Override public int fill(FluidStack resource, FluidAction action) {
            return isWater(resource) ? water.fill(resource, action) : 0;
        }
        @Override public FluidStack drain(FluidStack resource, FluidAction action) { return tank.drain(resource, action); }
        @Override public FluidStack drain(int amount, FluidAction action) { return tank.drain(amount, action); }
    };

    public enum BrewStatus {
        NEED_KNOT, NEED_ESSENCE, NEED_WATER, OUTPUT_BLOCKED, NEED_POWER, PAUSED, BREWING
    }

    public ItemStackHandler ingredients() { return ingredients; }
    public int energy() { return energy.getEnergyStored(); }
    public int waterAmount() { return water.getFluidAmount(); }
    public int batchTicks() { return batchTicks; }
    public String brewingTrace() {
        return !batchDimension.isBlank() ? batchDimension : ReturnKnotItem.source(ingredients.getStackInSlot(0));
    }
    public String outputTrace() { return ReturnSap.dimension(tank.getFluid()); }

    public BrewStatus brewStatus() {
        if (batchDimension.isEmpty()) {
            String trace = brewingTrace();
            if (!validTrace(trace)) return BrewStatus.NEED_KNOT;
            if (ingredients.getStackInSlot(1).isEmpty()) return BrewStatus.NEED_ESSENCE;
            if (waterAmount() < BATCH_VOLUME) return BrewStatus.NEED_WATER;
            if (tank.fill(ReturnSap.of(trace, BATCH_VOLUME), FluidAction.SIMULATE) != BATCH_VOLUME) {
                return BrewStatus.OUTPUT_BLOCKED;
            }
        }
        if (level != null && level.hasNeighborSignal(worldPosition)) return BrewStatus.PAUSED;
        return energy() >= TICK_COST ? BrewStatus.BREWING : BrewStatus.NEED_POWER;
    }

    public void jarTick(Level level) {
        var dock = ingredients.getStackInSlot(2);
        int charge = energy.receiveEnergy(ResonanceCellItem.energy(dock), true);
        if (charge > 0 && ResonanceCellItem.spend(dock, charge)) energy.receiveEnergy(charge, false);
        if (brewStatus() != BrewStatus.BREWING) return;
        if (batchDimension.isEmpty()) {
            batchDimension = brewingTrace();
            ingredients.extractItem(0, 1, false);
            ingredients.extractItem(1, 1, false);
            water.drain(BATCH_VOLUME, FluidAction.EXECUTE);
        }
        energy.spendTick();
        if (++batchTicks == BATCH_TICKS) {
            tank.fill(ReturnSap.of(batchDimension, BATCH_VOLUME), FluidAction.EXECUTE);
            batchDimension = "";
            batchTicks = 0;
            level.playSound(null, worldPosition, com.expeditions.registry.ModSounds.SAP_VESSEL_FILL.get(),
                    net.minecraft.sounds.SoundSource.BLOCKS, .7f, 1.2f);
            if (level instanceof net.minecraft.server.level.ServerLevel server) {
                com.expeditions.server.ExpeditionEventHandler.bloomWork(server, worldPosition);
            }
        } else if (batchTicks % 20 == 1 && level instanceof net.minecraft.server.level.ServerLevel server) {
            server.sendParticles(net.minecraft.core.particles.ParticleTypes.WAX_ON,
                    worldPosition.getX() + .5, worldPosition.getY() + .9, worldPosition.getZ() + .5,
                    3, .12, .05, .12, .01);
        }
        setChanged();
    }

    public void dropIngredients() {
        if (kind != SapVesselBlock.Kind.JAR || level == null || level.isClientSide) return;
        for (int slot = 0; slot < ingredients.getSlots(); slot++) {
            net.minecraft.world.level.block.Block.popResource(level, worldPosition, ingredients.getStackInSlot(slot));
            ingredients.setStackInSlot(slot, ItemStack.EMPTY);
        }
        if (!batchDimension.isEmpty()) {
            var knot = new ItemStack(ModItems.RETURN_KNOT.get());
            ReturnKnotItem.bind(knot, batchDimension);
            net.minecraft.world.level.block.Block.popResource(level, worldPosition, knot);
            net.minecraft.world.level.block.Block.popResource(level, worldPosition, new ItemStack(ModItems.ASSIMILATION_ESSENCE.get()));
            batchDimension = "";
            batchTicks = 0;
        }
    }

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, ModBlockEntities.SAP_VESSEL.get(),
                (vessel, side) -> vessel.kind != SapVesselBlock.Kind.WICK || side == null
                        || side.getAxis() == vessel.getBlockState().getValue(BlockStateProperties.FACING).getAxis()
                        ? vessel.fluidHandler() : null);
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ModBlockEntities.SAP_VESSEL.get(),
                (vessel, side) -> vessel.kind == SapVesselBlock.Kind.JAR && side != net.minecraft.core.Direction.DOWN
                        ? vessel.input : null);
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModBlockEntities.SAP_VESSEL.get(),
                (vessel, side) -> vessel.kind == SapVesselBlock.Kind.JAR ? vessel.energy : null);
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
        tag.putInt("SapSchema", 1);
        if (kind == SapVesselBlock.Kind.JAR) {
            tag.put("Water", water.writeToNBT(registries, new CompoundTag()));
            tag.put("Ingredients", ingredients.serializeNBT(registries));
            tag.putInt("Energy", energy());
            tag.putString("BatchDimension", batchDimension);
            tag.putInt("BatchTicks", batchTicks);
        }
        var wellsTag = new CompoundTag();
        wells.forEach(wellsTag::putInt);
        tag.put("Wells", wellsTag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        tank.readFromNBT(registries, tag.getCompound("Sap"));
        migrateLegacy = tag.getInt("SapSchema") == 0;
        legacySteep = migrateLegacy ? tag.getString("SteepDimension") : "";
        if (kind == SapVesselBlock.Kind.JAR) {
            water.readFromNBT(registries, tag.getCompound("Water"));
            if (migrateLegacy && isWater(tank.getFluid())) {
                water.setFluid(tank.getFluid().copy());
                tank.setFluid(FluidStack.EMPTY);
            }
            ingredients.deserializeNBT(registries, tag.getCompound("Ingredients"));
            energy.restore(tag.getInt("Energy"));
            String trace = tag.getString("BatchDimension");
            batchDimension = validTrace(trace) ? trace : "";
            batchTicks = batchDimension.isEmpty() ? 0 : Math.clamp(tag.getInt("BatchTicks"), 0, BATCH_TICKS - 1);
        }
        if (!tank.getFluid().is(ModFluids.SAP.get())) tank.setFluid(FluidStack.EMPTY);
        if (tank.getFluidAmount() > tank.getCapacity()) tank.getFluid().setAmount(tank.getCapacity());
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
        if (!(level instanceof net.minecraft.server.level.ServerLevel server)) return;
        if (migrateLegacy) {
            String local = server.dimension().location().toString();
            if (tank.getFluid().is(ModFluids.SAP.get())) {
                String trace = outputTrace();
                if (!validTrace(trace)) trace = local;
                if (kind == SapVesselBlock.Kind.BASIN) {
                    fillWell(ReturnSap.of(trace, tank.getFluidAmount()), false);
                    tank.setFluid(FluidStack.EMPTY);
                } else tank.setFluid(ReturnSap.of(trace, tank.getFluidAmount()));
            }
            if (kind == SapVesselBlock.Kind.JAR && validTrace(legacySteep)) {
                var knot = new ItemStack(ModItems.RETURN_KNOT.get());
                ReturnKnotItem.bind(knot, legacySteep);
                ingredients.setStackInSlot(0, knot);
            }
            legacySteep = "";
            migrateLegacy = false;
            setChanged();
        }
    }
}
