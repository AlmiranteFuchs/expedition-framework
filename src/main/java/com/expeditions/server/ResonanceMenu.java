package com.expeditions.server;

import com.expeditions.block.ResonanceMachineBlock;
import com.expeditions.block.entity.ResonanceMachineBlockEntity;
import com.expeditions.item.ResonanceCellItem;
import com.expeditions.registry.ModItems;
import com.expeditions.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

public final class ResonanceMenu extends AbstractContainerMenu {
    private final BlockPos pos;
    private final ContainerData data;
    public final boolean dynamo;
    private final int machineSlots;
    private final int cellSlot;

    public ResonanceMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(id, inventory, buffer.readBlockPos(), buffer.readBoolean(),
                new ItemStackHandler(3), new SimpleContainerData(9));
    }

    public ResonanceMenu(int id, Inventory inventory, ResonanceMachineBlockEntity machine) {
        this(id, inventory, machine.getBlockPos(), machine.dynamo(), machine.inventory, new ContainerData() {
            @Override
            public int get(int index) {
                return switch (index) {
                    case 0 -> machine.energy();
                    case 1 -> machine.progress();
                    case 2 -> machine.breaching() ? 1 : 0;
                    case 4 -> machine.kind().ordinal();
                    case 5 -> machine.generationRate();
                    case 6 -> machine.status().ordinal();
                    case 7 -> machine.collectors();
                    case 8 -> machine.actualGeneration();
                    default -> 16000;
                };
            }

            @Override public void set(int index, int value) { }
            @Override public int getCount() { return 9; }
        });
    }

    private ResonanceMenu(int id, Inventory inventory, BlockPos pos, boolean dynamo,
            IItemHandler items, ContainerData data) {
        super(ModMenus.RESONANCE.get(), id);
        this.pos = pos;
        this.dynamo = dynamo;
        this.data = data;
        addSlot(new SlotItemHandler(items, 0, 58, 65));
        if (!dynamo) {
            addSlot(new SlotItemHandler(items, 1, 108, 65) {
                @Override public boolean mayPlace(ItemStack stack) { return false; }
            });
        }
        cellSlot = dynamo ? 1 : 2;
        addSlot(new SlotItemHandler(items, ResonanceMachineBlockEntity.CELL_SLOT, 164, 65) {
            @Override public boolean mayPlace(ItemStack stack) { return stack.is(ModItems.RESONANCE_CELL.get()); }
            @Override public int getMaxStackSize() { return 1; }
        });
        machineSlots = slots.size();
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, column + row * 9 + 9, 25 + column * 18, 137 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, 25 + column * 18, 195));
        }
        addDataSlots(data);
    }

    public int cellSlot() { return cellSlot; }
    public ItemStack cell() { return getSlot(cellSlot).getItem(); }
    public int cellEnergy() { return ResonanceCellItem.energy(cell()); }
    public int sourceKind() { return data.get(4); }
    public com.expeditions.core.ResonanceStatus status() { return com.expeditions.core.ResonanceStatus.byId(data.get(6)); }
    public int collectors() { return data.get(7); }
    public int actualGeneration() { return data.get(8); }
    public int generationRate() { return data.get(5); }
    public int energy() { return data.get(0); }
    public int progress() { return data.get(1); }
    public boolean breaching() { return data.get(2) > 0; }

    public int comparatorSignal() {
        if (dynamo) return com.expeditions.core.ResonanceStatus.fillSignal(energy(), 16000);
        var output = getSlot(1).getItem();
        return output.isEmpty() ? 0 : com.expeditions.core.ResonanceStatus.fillSignal(output.getCount(), output.getMaxStackSize());
    }

    @Override
    public boolean stillValid(Player player) {
        return player.level().getBlockState(pos).getBlock() instanceof ResonanceMachineBlock
                && player.distanceToSqr(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5) <= 64;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        var slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        var stack = slot.getItem();
        var previous = stack.copy();
        if (index < machineSlots) {
            if (!moveItemStackTo(stack, machineSlots, slots.size(), true)) return ItemStack.EMPTY;
        } else {
            int destination = stack.is(ModItems.RESONANCE_CELL.get()) ? cellSlot : 0;
            if (!slots.get(destination).mayPlace(stack)
                    || !moveItemStackTo(stack, destination, destination + 1, false)) return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();
        return previous;
    }
}
