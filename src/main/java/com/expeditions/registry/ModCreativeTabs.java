package com.expeditions.registry;

import com.expeditions.ExpeditionsMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The mod's creative tab. Keeping our own tab keeps the terminal obtainable in
 * creative mode without mutating vanilla tabs.
 */
public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(
            Registries.CREATIVE_MODE_TAB, ExpeditionsMod.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> EXPEDITIONS_TAB = CREATIVE_MODE_TABS.register(
            "expeditions",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.expeditions"))
                    .icon(() -> new ItemStack(ModItems.EXPEDITION_TERMINAL_ITEM.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(ModItems.EXPEDITION_TERMINAL_ITEM.get());
                        output.accept(ModItems.EXPEDITION_CHECKPOINT_ITEM.get());
                        output.accept(ModItems.EXPEDITION_EXIT_ITEM.get());
                    })
                    .build());

    private ModCreativeTabs() {
    }
}
