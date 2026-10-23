package com.expeditions.registry;

import com.expeditions.ExpeditionsMod;
import com.expeditions.item.ExpeditionCompassItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Item registrations, paired with {@link ModBlocks} where a block item applies.
 */
public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ExpeditionsMod.MOD_ID);

    public static final DeferredItem<BlockItem> EXPEDITION_TERMINAL_ITEM = ITEMS.registerSimpleBlockItem(
            "expedition_terminal", ModBlocks.EXPEDITION_TERMINAL);

    public static final DeferredItem<BlockItem> EXPEDITION_CHECKPOINT_ITEM = ITEMS.registerSimpleBlockItem(
            "expedition_checkpoint", ModBlocks.EXPEDITION_CHECKPOINT);

    public static final DeferredItem<BlockItem> EXPEDITION_EXIT_ITEM = ITEMS.registerSimpleBlockItem(
            "expedition_exit", ModBlocks.EXPEDITION_EXIT);

    public static final DeferredItem<ExpeditionCompassItem> EXPEDITION_COMPASS = ITEMS.register(
            "expedition_compass",
            () -> new ExpeditionCompassItem(new Item.Properties().stacksTo(1)));

    private ModItems() {
    }
}
