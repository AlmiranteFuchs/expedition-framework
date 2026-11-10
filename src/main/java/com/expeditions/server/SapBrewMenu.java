package com.expeditions.server;

import com.expeditions.block.entity.SapVesselBlockEntity;
import com.expeditions.item.ReturnSapBucketItem;
import com.expeditions.registry.ModItems;
import com.expeditions.registry.ModMenus;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

public final class SapBrewMenu extends AbstractContainerMenu {
    private final SapVesselBlockEntity vessel;
    private final SimpleContainer traces = new SimpleContainer(2);
    private final ContainerData data;

    public SapBrewMenu(int id, Inventory inventory) {
        this(id, inventory, null);
    }

    public SapBrewMenu(int id, Inventory inventory, SapVesselBlockEntity vessel) {
        super(ModMenus.SAP_BREW.get(), id);
        this.vessel = vessel;
        var ingredients = vessel == null ? new ItemStackHandler(3) : vessel.ingredients();
        for (int slot = 0; slot < 3; slot++) addSlot(new SlotItemHandler(ingredients, slot, 44 + slot * 51, 48));
        for (int slot = 0; slot < 2; slot++) addSlot(new Slot(traces, slot, -10000, -10000) {
            @Override public boolean mayPlace(ItemStack stack) { return false; }
            @Override public boolean mayPickup(Player player) { return false; }
        });
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, 9 + row * 9 + col, 26 + col * 18, 114 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, 26 + col * 18, 172));
        data = vessel == null ? new SimpleContainerData(5) : new ContainerData() {
            @Override public int get(int index) {
                return switch (index) {
                    case 0 -> vessel.energy();
                    case 1 -> vessel.waterAmount();
                    case 2 -> vessel.tank().getFluidAmount();
                    case 3 -> vessel.batchTicks();
                    case 4 -> vessel.brewStatus().ordinal();
                    default -> 0;
                };
            }
            @Override public void set(int index, int value) { }
            @Override public int getCount() { return 5; }
        };
        addDataSlots(data);
        updateTraces();
    }

    public int energy() { return data.get(0); }
    public int water() { return data.get(1); }
    public int sap() { return data.get(2); }
    public int progress() { return data.get(3); }
    public SapVesselBlockEntity.BrewStatus status() { return SapVesselBlockEntity.BrewStatus.values()[data.get(4)]; }
    public String inputTrace() { return ReturnSapBucketItem.source(traces.getItem(0)); }
    public String outputTrace() { return ReturnSapBucketItem.source(traces.getItem(1)); }

    private void updateTraces() {
        if (vessel != null) {
            updateTrace(0, vessel.brewingTrace());
            updateTrace(1, vessel.outputTrace());
        }
    }

    private void updateTrace(int slot, String trace) {
        if (trace.equals(ReturnSapBucketItem.source(traces.getItem(slot)))) return;
        var icon = trace.isBlank() ? ItemStack.EMPTY : new ItemStack(ModItems.RETURN_SAP_BUCKET.get());
        ReturnSapBucketItem.bind(icon, trace);
        traces.setItem(slot, icon);
    }

    @Override public void broadcastChanges() {
        updateTraces();
        super.broadcastChanges();
    }

    @Override public boolean stillValid(Player player) {
        return vessel == null || player.level() == vessel.getLevel() && !vessel.isRemoved()
                && player.canInteractWithBlock(vessel.getBlockPos(), 4);
    }

    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size() || index == 3 || index == 4) return ItemStack.EMPTY;
        var slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        var stack = slot.getItem();
        var original = stack.copy();
        if (index < 3) {
            if (!moveItemStackTo(stack, 5, slots.size(), true)) return ItemStack.EMPTY;
        } else {
            int target = stack.is(ModItems.RETURN_KNOT.get()) ? 0 : stack.is(ModItems.ASSIMILATION_ESSENCE.get()) ? 1
                    : stack.is(ModItems.RESONANCE_CELL.get()) ? 2 : -1;
            if (target < 0 || !slots.get(target).mayPlace(stack) || !moveItemStackTo(stack, target, target + 1, false)) {
                return ItemStack.EMPTY;
            }
        }
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();
        return original;
    }
}
