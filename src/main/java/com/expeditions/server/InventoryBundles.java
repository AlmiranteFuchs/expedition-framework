package com.expeditions.server;

import java.util.List;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;

final class InventoryBundles {
    private InventoryBundles() { }

    static boolean fits(Inventory inventory, List<ItemStack> bundle) {
        var trial = new ItemStackHandler(inventory.items.size());
        for (int slot = 0; slot < trial.getSlots(); slot++) {
            trial.setStackInSlot(slot, inventory.items.get(slot).copy());
        }
        for (var stack : bundle) {
            var remainder = stack.copy();
            for (int slot = 0; slot < trial.getSlots() && !remainder.isEmpty(); slot++) {
                if (!trial.getStackInSlot(slot).isEmpty()) remainder = trial.insertItem(slot, remainder, false);
            }
            for (int slot = 0; slot < trial.getSlots() && !remainder.isEmpty(); slot++) {
                remainder = trial.insertItem(slot, remainder, false);
            }
            if (!remainder.isEmpty()) return false;
        }
        return true;
    }

    static void deliver(Inventory inventory, List<ItemStack> bundle) {
        for (var stack : bundle) inventory.add(stack.copy());
        inventory.setChanged();
    }
}
