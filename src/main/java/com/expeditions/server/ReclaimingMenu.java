package com.expeditions.server;

import com.expeditions.registry.ModBlocks;
import com.expeditions.registry.ModItems;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

/** The display stacks are quotes. Only a server-side click can settle a quote. */
public final class ReclaimingMenu extends ChestMenu {
    private final ServerLevel level;
    private final BlockPos basin;
    private final ExpeditionManager manager;
    private final List<String> dimensions;
    private int index;

    public ReclaimingMenu(int id, Inventory inventory) {
        super(com.expeditions.registry.ModMenus.RECLAIMING.get(), id, inventory, new SimpleContainer(27), 3);
        level = null;
        basin = BlockPos.ZERO;
        manager = null;
        dimensions = List.of();
    }

    public ReclaimingMenu(int id, Inventory inventory, ServerLevel level, BlockPos basin) {
        super(com.expeditions.registry.ModMenus.RECLAIMING.get(), id, inventory, new SimpleContainer(27), 3);
        this.level = level;
        this.basin = basin.immutable();
        manager = ExpeditionManager.get(level.getServer());
        dimensions = manager.reclaimingDimensions(inventory.player.getUUID());
        refresh(inventory.player);
    }

    @Override
    public boolean stillValid(Player player) {
        return level == null || player.level() == level && level.getBlockState(basin).is(ModBlocks.RECLAIMING_BASIN.get())
                && player.canInteractWithBlock(basin, 4);
    }

    @Override
    public void clicked(int slot, int button, ClickType type, Player player) {
        if (!(player instanceof ServerPlayer serverPlayer) || !stillValid(player)
                || type != ClickType.PICKUP || button != 0 || dimensions.isEmpty()
                || !getCarried().isEmpty()) return;
        if (slot == 18 || slot == 26) {
            index = Math.floorMod(index + (slot == 18 ? -1 : 1), dimensions.size());
        } else if (slot >= 0 && slot < offers().size()) {
            if (level.getBlockEntity(basin) instanceof com.expeditions.block.entity.SapVesselBlockEntity vessel
                    && manager.reclaim(serverPlayer, dimensions.get(index), slot, vessel)) {
                level.sendParticles(com.expeditions.block.RouteMarkerBlock.AMBER,
                        basin.getX() + 0.5, basin.getY() + 0.55, basin.getZ() + 0.5, 12, 0.2, 0.14, 0.2, 0);
            }
        }
        refresh(player);
        broadcastChanges();
    }

    @Override
    public void broadcastChanges() {
        if (level != null && !dimensions.isEmpty()) {
            int amount = level.getBlockEntity(basin) instanceof com.expeditions.block.entity.SapVesselBlockEntity vessel
                    ? vessel.tank().getFluidAmount() : 0;
            var account = getContainer().getItem(22);
            var name = Component.translatable("gui.expeditions.basin_account", dimensions.get(index), amount);
            if (!name.equals(account.get(DataComponents.CUSTOM_NAME))) account.set(DataComponents.CUSTOM_NAME, name);
        }
        super.broadcastChanges();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slot) {
        return ItemStack.EMPTY;
    }

    private List<ReclaimingOffers.Offer> offers() {
        return ReclaimingOffers.forDimension(dimensions.get(index));
    }

    private void refresh(Player player) {
        var display = getContainer();
        display.clearContent();
        if (dimensions.isEmpty()) {
            display.setItem(13, label(new ItemStack(Items.DEAD_BUSH), "gui.expeditions.basin_dormant"));
            return;
        }
        var offers = offers();
        for (int slot = 0; slot < offers.size(); slot++) {
            var offer = offers.get(slot);
            var quote = offer.stack();
            quote.set(DataComponents.CUSTOM_NAME, Component.translatable("gui.expeditions.basin_tier_" + slot,
                    quote.getCount(), offer.item().getDescription()));
            var notes = new java.util.ArrayList<Component>();
            notes.add(Component.translatable("gui.expeditions.basin_price", offer.sap()));
            if (offer.feedCount() > 0) notes.add(Component.translatable("gui.expeditions.basin_feed",
                    offer.feedCount(), offer.feed().getDescription()));
            notes.add(Component.translatable("gui.expeditions.basin_bonus", offer.extraCount(), offer.extra().getDescription()));
            if (slot == 2 && !manager.canReclaimGear(player.getUUID(), dimensions.get(index))) {
                notes.add(Component.translatable("message.expeditions.basin_gear"));
            }
            quote.set(DataComponents.LORE, new ItemLore(notes));
            display.setItem(slot, quote);
        }
        display.setItem(18, label(new ItemStack(Items.PAPER), "gui.expeditions.basin_previous"));
        int amount = level.getBlockEntity(basin) instanceof com.expeditions.block.entity.SapVesselBlockEntity vessel
                ? vessel.tank().getFluidAmount() : 0;
        display.setItem(22, label(new ItemStack(ModItems.RETURN_SAP_BUCKET.get()), "gui.expeditions.basin_account",
                dimensions.get(index), amount));
        display.setItem(26, label(new ItemStack(Items.PAPER), "gui.expeditions.basin_next"));
    }

    private static ItemStack label(ItemStack stack, String key, Object... args) {
        stack.set(DataComponents.CUSTOM_NAME, Component.translatable(key, args));
        return stack;
    }
}
